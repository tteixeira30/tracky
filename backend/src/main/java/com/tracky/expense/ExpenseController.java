package com.tracky.expense;

import com.tracky.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Despesas e contas correntes: CRUD de contas, movimentos manuais e importação
 * de extratos bancários (o frontend faz o parse do ficheiro; aqui recebem-se
 * as linhas já estruturadas e evita-se a duplicação de movimentos).
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;
    private final CategoryRuleRepository rules;

    public ExpenseController(AccountRepository accounts, TransactionRepository transactions,
                             CategoryRuleRepository rules) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.rules = rules;
    }

    /** currentBalance opcional, em EUR; null = limpar/não definido. */
    public record AccountRequest(@NotBlank String name, BigDecimal currentBalance) {}
    public record AccountDto(Long id, String name, long transactionCount, BigDecimal currentBalance) {}
    /** applyToSimilar: aplica a categoria a todos os movimentos com a mesma descrição e memoriza como regra. */
    public record TransactionRequest(@NotNull Long accountId, @NotNull LocalDate date, @NotBlank String description,
                                     @NotNull @Positive BigDecimal amount, boolean inflow, String category,
                                     Boolean applyToSimilar) {}
    public record RuleDto(Long id, String matchKey, String category) {}
    public record TransactionDto(Long id, Long accountId, String accountName, LocalDate date, String description,
                                 BigDecimal amount, boolean inflow, String category) {}
    public record ImportRow(@NotNull LocalDate date, @NotBlank String description,
                            @NotNull @Positive BigDecimal amount, boolean inflow, String category) {}
    public record ImportRequest(@NotNull Long accountId, @NotEmpty List<@Valid ImportRow> rows) {}
    public record ImportResult(int imported, int skipped) {}
    public record CategoryTotal(String category, BigDecimal total) {}
    public record MonthSummary(String month, BigDecimal inflows, BigDecimal outflows, BigDecimal net,
                               List<CategoryTotal> byCategory, List<AccountDto> accounts,
                               List<TransactionDto> transactions) {}

    // ---------- Contas ----------

    @GetMapping("/accounts")
    public List<AccountDto> listAccounts(@AuthenticationPrincipal User user) {
        return accounts.findByUserIdOrderByIdAsc(user.getId()).stream().map(a -> toDto(user, a)).toList();
    }

    @PostMapping("/accounts")
    public AccountDto createAccount(@AuthenticationPrincipal User user, @Valid @RequestBody AccountRequest req) {
        Account a = new Account();
        a.setUserId(user.getId());
        a.setName(req.name().trim());
        a.setCurrentBalance(roundBalance(req.currentBalance()));
        return toDto(user, accounts.save(a));
    }

    @PutMapping("/accounts/{id}")
    public AccountDto updateAccount(@AuthenticationPrincipal User user, @PathVariable Long id,
                                    @Valid @RequestBody AccountRequest req) {
        Account a = accounts.findByIdAndUserId(id, user.getId()).orElseThrow();
        a.setName(req.name().trim());
        a.setCurrentBalance(roundBalance(req.currentBalance()));
        return toDto(user, accounts.save(a));
    }

    /** Elimina a conta e todos os movimentos associados. */
    @DeleteMapping("/accounts/{id}")
    @Transactional
    public void deleteAccount(@AuthenticationPrincipal User user, @PathVariable Long id) {
        accounts.findByIdAndUserId(id, user.getId()).ifPresent(a -> {
            transactions.deleteByUserIdAndAccountId(user.getId(), a.getId());
            accounts.delete(a);
        });
    }

    // ---------- Movimentos ----------

    @GetMapping
    public MonthSummary month(@AuthenticationPrincipal User user,
                              @RequestParam(required = false) String month,
                              @RequestParam(required = false) Long accountId) {
        YearMonth ym = parseMonth(month);
        LocalDate from = ym.atDay(1), to = ym.atEndOfMonth();

        Map<Long, String> accountNames = new HashMap<>();
        List<AccountDto> accountDtos = accounts.findByUserIdOrderByIdAsc(user.getId()).stream()
                .peek(a -> accountNames.put(a.getId(), a.getName()))
                .map(a -> toDto(user, a)).toList();

        List<Transaction> txs = transactions.findByUserIdAndTxDateBetweenOrderByTxDateDescIdDesc(user.getId(), from, to);
        if (accountId != null) txs = txs.stream().filter(t -> accountId.equals(t.getAccountId())).toList();

        BigDecimal in = BigDecimal.ZERO, out = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (Transaction t : txs) {
            if (t.isInflow()) in = in.add(t.getAmount());
            else {
                out = out.add(t.getAmount());
                byCategory.merge(t.getCategory().name(), t.getAmount(), BigDecimal::add);
            }
        }
        List<CategoryTotal> categories = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> new CategoryTotal(e.getKey(), e.getValue())).toList();

        return new MonthSummary(ym.toString(), in, out, in.subtract(out), categories, accountDtos,
                txs.stream().map(t -> toDto(t, accountNames)).toList());
    }

    @PostMapping("/transactions")
    public TransactionDto create(@AuthenticationPrincipal User user, @Valid @RequestBody TransactionRequest req) {
        Account a = requireAccount(user, req.accountId());
        Transaction t = new Transaction();
        t.setUserId(user.getId());
        t.setAccountId(a.getId());
        apply(t, req);
        TransactionDto dto = toDto(transactions.save(t), Map.of(a.getId(), a.getName()));
        if (Boolean.TRUE.equals(req.applyToSimilar())) applyCategoryRule(user, t.getDescription(), t.getCategory());
        return dto;
    }

    @PutMapping("/transactions/{id}")
    public TransactionDto update(@AuthenticationPrincipal User user, @PathVariable Long id,
                                 @Valid @RequestBody TransactionRequest req) {
        Transaction t = transactions.findByIdAndUserId(id, user.getId()).orElseThrow();
        Account a = requireAccount(user, req.accountId());
        t.setAccountId(a.getId());
        apply(t, req);
        TransactionDto dto = toDto(transactions.save(t), Map.of(a.getId(), a.getName()));
        if (Boolean.TRUE.equals(req.applyToSimilar())) applyCategoryRule(user, t.getDescription(), t.getCategory());
        return dto;
    }

    // ---------- Regras de categorização ----------

    @GetMapping("/rules")
    public List<RuleDto> listRules(@AuthenticationPrincipal User user) {
        return rules.findByUserIdOrderByIdAsc(user.getId()).stream()
                .map(r -> new RuleDto(r.getId(), r.getMatchKey(), r.getCategory().name())).toList();
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@AuthenticationPrincipal User user, @PathVariable Long id) {
        rules.findByIdAndUserId(id, user.getId()).ifPresent(rules::delete);
    }

    /**
     * Memoriza a regra descrição→categoria e propaga a categoria a todos os
     * movimentos do utilizador com a mesma descrição normalizada.
     */
    private void applyCategoryRule(User user, String description, Transaction.Category category) {
        String key = categoryKey(description);
        if (key.isEmpty()) return;

        CategoryRule rule = rules.findByUserIdAndMatchKey(user.getId(), key).orElseGet(() -> {
            CategoryRule r = new CategoryRule();
            r.setUserId(user.getId());
            r.setMatchKey(key);
            return r;
        });
        rule.setCategory(category);
        rules.save(rule);

        List<Transaction> toUpdate = transactions.findByUserId(user.getId()).stream()
                .filter(t -> key.equals(categoryKey(t.getDescription())) && t.getCategory() != category)
                .peek(t -> t.setCategory(category))
                .toList();
        transactions.saveAll(toUpdate);
    }

    @DeleteMapping("/transactions/{id}")
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        transactions.findByIdAndUserId(id, user.getId()).ifPresent(transactions::delete);
    }

    /**
     * Importa linhas de um extrato já estruturadas pelo frontend. Movimentos que já
     * existam na conta (mesma data, valor, sentido e descrição) são ignorados, para
     * que reimportar o mesmo extrato — ou extratos com meses sobrepostos — seja seguro.
     * A comparação é por contagem (multiset): um extrato pode ter movimentos
     * legitimamente idênticos (ex.: duas compras iguais no mesmo dia) e cada
     * ocorrência já existente na conta só absorve uma ocorrência do ficheiro.
     */
    @PostMapping("/import")
    public ImportResult importRows(@AuthenticationPrincipal User user, @Valid @RequestBody ImportRequest req) {
        Account a = requireAccount(user, req.accountId());

        LocalDate min = req.rows().stream().map(ImportRow::date).min(LocalDate::compareTo).orElseThrow();
        LocalDate max = req.rows().stream().map(ImportRow::date).max(LocalDate::compareTo).orElseThrow();
        Map<String, Integer> existing = new HashMap<>();
        for (Transaction t : transactions.findByUserIdAndAccountIdAndTxDateBetween(user.getId(), a.getId(), min, max)) {
            existing.merge(dedupeKey(t.getTxDate(), t.getAmount(), t.isInflow(), t.getDescription()), 1, Integer::sum);
        }

        // regras de categorização aprendidas têm prioridade sobre a categoria vinda do frontend
        Map<String, Transaction.Category> ruleMap = new HashMap<>();
        for (CategoryRule r : rules.findByUserIdOrderByIdAsc(user.getId())) ruleMap.put(r.getMatchKey(), r.getCategory());

        int imported = 0, skipped = 0;
        List<Transaction> batch = new ArrayList<>();
        for (ImportRow row : req.rows()) {
            String key = dedupeKey(row.date(), row.amount(), row.inflow(), row.description());
            Integer remaining = existing.get(key);
            if (remaining != null && remaining > 0) {
                existing.put(key, remaining - 1);
                skipped++;
                continue;
            }
            Transaction t = new Transaction();
            t.setUserId(user.getId());
            t.setAccountId(a.getId());
            t.setTxDate(row.date());
            t.setDescription(truncate(row.description().trim()));
            t.setAmount(row.amount().setScale(2, RoundingMode.HALF_UP));
            t.setInflow(row.inflow());
            Transaction.Category ruled = ruleMap.get(categoryKey(row.description()));
            t.setCategory(ruled != null ? ruled : parseCategory(row.category()));
            batch.add(t);
            imported++;
        }
        transactions.saveAll(batch);
        return new ImportResult(imported, skipped);
    }

    // ---------- Helpers ----------

    private Account requireAccount(User user, Long accountId) {
        return accounts.findByIdAndUserId(accountId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Conta inexistente."));
    }

    private void apply(Transaction t, TransactionRequest req) {
        t.setTxDate(req.date());
        t.setDescription(truncate(req.description().trim()));
        t.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        t.setInflow(req.inflow());
        t.setCategory(parseCategory(req.category()));
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) return YearMonth.now();
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido (usa AAAA-MM).");
        }
    }

    private static Transaction.Category parseCategory(String category) {
        if (category == null || category.isBlank()) return Transaction.Category.OTHER;
        try {
            return Transaction.Category.valueOf(category.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return Transaction.Category.OTHER;
        }
    }

    private static String truncate(String s) {
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** Normalização de descrições para dedupe (comparação exata entre movimentos). */
    private static String normalizeDesc(String description) {
        return description == null ? "" : description.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Chave de categorização: identifica o "comerciante" da descrição ignorando as
     * partes variáveis (referências, datas, valores, pontuação), para que a mesma
     * regra apanhe movimentos do mesmo sítio mesmo com números diferentes.
     * NÃO usar no dedupe — só para regras de categoria. Tem de ser idêntica à versão
     * do frontend (categoryKey em statementParser.js).
     */
    static String categoryKey(String description) {
        if (description == null) return "";
        return description.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\d+", " ")            // referências, datas, valores
                .replaceAll("[^\\p{L}\\s]", " ")    // pontuação e símbolos
                .replaceAll("\\s+", " ").trim();    // colapsa espaços
    }

    private static String dedupeKey(LocalDate date, BigDecimal amount, boolean inflow, String description) {
        return date + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + inflow + "|" + normalizeDesc(description);
    }

    /** Arredonda o saldo a 2 casas decimais; null passa (saldo "não definido"). */
    private static BigDecimal roundBalance(BigDecimal balance) {
        return balance == null ? null : balance.setScale(2, RoundingMode.HALF_UP);
    }

    private AccountDto toDto(User user, Account a) {
        return new AccountDto(a.getId(), a.getName(), transactions.countByUserIdAndAccountId(user.getId(), a.getId()),
                a.getCurrentBalance());
    }

    private static TransactionDto toDto(Transaction t, Map<Long, String> accountNames) {
        return new TransactionDto(t.getId(), t.getAccountId(), accountNames.get(t.getAccountId()), t.getTxDate(),
                t.getDescription(), t.getAmount(), t.isInflow(), t.getCategory().name());
    }
}
