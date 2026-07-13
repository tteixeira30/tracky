package com.tracky.income;

import com.tracky.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

interface IncomeSettingsRepository extends JpaRepository<IncomeSettings, Long> {
    Optional<IncomeSettings> findByUserIdAndMonth(Long userId, String month);
    List<IncomeSettings> findByUserIdOrderByMonthAsc(Long userId);
    List<IncomeSettings> findByUserIdAndMonthIsNull(Long userId);
}

interface AllocationRepository extends JpaRepository<Allocation, Long> {
    List<Allocation> findByUserIdAndMonthOrderByIdAsc(Long userId, String month);
    List<Allocation> findByUserIdAndMonthIsNull(Long userId);
    Optional<Allocation> findByIdAndUserId(Long id, Long userId);
}

@RestController
@RequestMapping("/api/income")
public class IncomeController {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final IncomeSettingsRepository incomeRepo;
    private final AllocationRepository allocationRepo;

    public IncomeController(IncomeSettingsRepository incomeRepo, AllocationRepository allocationRepo) {
        this.incomeRepo = incomeRepo;
        this.allocationRepo = allocationRepo;
    }

    public record AllocationDto(Long id, String name, BigDecimal percentage, BigDecimal fixedAmount,
                                BigDecimal amount, BigDecimal effectivePercentage) {}
    public record IncomeResponse(String month, boolean current, BigDecimal monthlyIncome,
                                 List<AllocationDto> allocations, BigDecimal totalAllocated,
                                 BigDecimal totalPercentage, BigDecimal unallocated,
                                 List<String> availableMonths, String copiedFrom) {}
    public record IncomeRequest(@NotNull BigDecimal monthlyIncome) {}
    /** Ou percentage ou fixedAmount — exatamente um dos dois. */
    public record AllocationRequest(@NotBlank String name, BigDecimal percentage, BigDecimal fixedAmount) {}

    // ---------- helpers ----------

    private String currentMonth() {
        return YearMonth.now().toString();
    }

    private String normalizeMonth(String month) {
        if (month == null || month.isBlank()) return currentMonth();
        try {
            return YearMonth.parse(month.trim()).toString();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido — usa o formato AAAA-MM.");
        }
    }

    /** Dados antigos (antes do rendimento mensal) não tinham mês: passam a pertencer ao mês atual. */
    private void migrateLegacyRows(User user) {
        String current = currentMonth();
        incomeRepo.findByUserIdAndMonthIsNull(user.getId()).forEach(s -> {
            s.setMonth(current);
            incomeRepo.save(s);
        });
        allocationRepo.findByUserIdAndMonthIsNull(user.getId()).forEach(a -> {
            a.setMonth(current);
            allocationRepo.save(a);
        });
    }

    /** Mês mais recente com dados, anterior ao mês dado. */
    private Optional<IncomeSettings> latestBefore(Long userId, String month) {
        return incomeRepo.findByUserIdOrderByMonthAsc(userId).stream()
                .filter(s -> s.getMonth() != null && s.getMonth().compareTo(month) < 0)
                .reduce((first, second) -> second);
    }

    private IncomeSettings getOrCreate(User user, String month) {
        return incomeRepo.findByUserIdAndMonth(user.getId(), month).orElseGet(() -> {
            IncomeSettings s = new IncomeSettings();
            s.setUserId(user.getId());
            s.setMonth(month);
            return incomeRepo.save(s);
        });
    }

    // ---------- endpoints ----------

    @GetMapping
    public IncomeResponse get(@AuthenticationPrincipal User user,
                              @RequestParam(required = false) String month) {
        migrateLegacyRows(user);
        String m = normalizeMonth(month);
        String current = currentMonth();
        String copiedFrom = null;

        Optional<IncomeSettings> existing = incomeRepo.findByUserIdAndMonth(user.getId(), m);

        // ao entrar num mês novo (o atual), arranca com as categorias e o rendimento do mês anterior
        if (existing.isEmpty() && m.equals(current)) {
            Optional<IncomeSettings> previous = latestBefore(user.getId(), m);
            IncomeSettings s = new IncomeSettings();
            s.setUserId(user.getId());
            s.setMonth(m);
            if (previous.isPresent()) {
                s.setMonthlyIncome(previous.get().getMonthlyIncome());
                List<Allocation> prevAllocs =
                        allocationRepo.findByUserIdAndMonthOrderByIdAsc(user.getId(), previous.get().getMonth());
                for (Allocation a : prevAllocs) {
                    Allocation copy = new Allocation();
                    copy.setUserId(user.getId());
                    copy.setMonth(m);
                    copy.setName(a.getName());
                    copy.setPercentage(a.getPercentage());
                    copy.setFixedAmount(a.getFixedAmount());
                    allocationRepo.save(copy);
                }
                if (!prevAllocs.isEmpty()) copiedFrom = previous.get().getMonth();
            }
            incomeRepo.save(s);
            existing = Optional.of(s);
        }

        return buildResponse(user, m, existing.orElse(null), copiedFrom);
    }

    @PutMapping
    public IncomeResponse setIncome(@AuthenticationPrincipal User user,
                                    @RequestParam(required = false) String month,
                                    @Valid @RequestBody IncomeRequest req) {
        migrateLegacyRows(user);
        String m = normalizeMonth(month);
        IncomeSettings s = getOrCreate(user, m);
        s.setMonthlyIncome(req.monthlyIncome());
        incomeRepo.save(s);
        return get(user, m);
    }

    @PostMapping("/allocations")
    public IncomeResponse addAllocation(@AuthenticationPrincipal User user,
                                        @RequestParam(required = false) String month,
                                        @Valid @RequestBody AllocationRequest req) {
        migrateLegacyRows(user);
        String m = normalizeMonth(month);
        validate(req);
        getOrCreate(user, m); // garante que o mês existe
        Allocation a = new Allocation();
        a.setUserId(user.getId());
        a.setMonth(m);
        apply(a, req);
        allocationRepo.save(a);
        return get(user, m);
    }

    @PutMapping("/allocations/{id}")
    public IncomeResponse updateAllocation(@AuthenticationPrincipal User user, @PathVariable Long id,
                                           @Valid @RequestBody AllocationRequest req) {
        validate(req);
        Allocation a = allocationRepo.findByIdAndUserId(id, user.getId()).orElseThrow();
        apply(a, req);
        allocationRepo.save(a);
        return get(user, a.getMonth());
    }

    @DeleteMapping("/allocations/{id}")
    public IncomeResponse deleteAllocation(@AuthenticationPrincipal User user, @PathVariable Long id) {
        Optional<Allocation> a = allocationRepo.findByIdAndUserId(id, user.getId());
        String m = a.map(Allocation::getMonth).orElse(null);
        a.ifPresent(allocationRepo::delete);
        return get(user, m);
    }

    // ---------- internals ----------

    private void validate(AllocationRequest req) {
        boolean hasPct = req.percentage() != null && req.percentage().signum() > 0;
        boolean hasFixed = req.fixedAmount() != null && req.fixedAmount().signum() > 0;
        if (hasPct == hasFixed) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Define a percentagem ou o valor fixo (apenas um dos dois, maior que zero).");
        }
        if (hasPct && req.percentage().compareTo(HUNDRED) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A percentagem não pode ser superior a 100%.");
        }
    }

    private void apply(Allocation a, AllocationRequest req) {
        boolean hasFixed = req.fixedAmount() != null && req.fixedAmount().signum() > 0;
        a.setName(req.name());
        a.setPercentage(hasFixed ? null : req.percentage());
        a.setFixedAmount(hasFixed ? req.fixedAmount() : null);
    }

    private IncomeResponse buildResponse(User user, String month, IncomeSettings settings, String copiedFrom) {
        BigDecimal income = settings != null ? settings.getMonthlyIncome() : BigDecimal.ZERO;

        List<AllocationDto> allocations = allocationRepo
                .findByUserIdAndMonthOrderByIdAsc(user.getId(), month).stream()
                .map(a -> {
                    BigDecimal amount = a.getFixedAmount() != null
                            ? a.getFixedAmount()
                            : income.multiply(a.getPercentage()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    BigDecimal effectivePct = income.signum() > 0
                            ? amount.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP)
                            : a.getPercentage();
                    return new AllocationDto(a.getId(), a.getName(), a.getPercentage(), a.getFixedAmount(),
                            amount, effectivePct);
                })
                .toList();

        BigDecimal totalAllocated = allocations.stream()
                .map(AllocationDto::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPct = income.signum() > 0
                ? totalAllocated.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP)
                : allocations.stream()
                    .map(AllocationDto::percentage)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unallocated = income.subtract(totalAllocated);

        List<String> availableMonths = incomeRepo.findByUserIdOrderByMonthAsc(user.getId()).stream()
                .map(IncomeSettings::getMonth)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new IncomeResponse(month, month.equals(currentMonth()), income, allocations,
                totalAllocated.setScale(2, RoundingMode.HALF_UP), totalPct,
                unallocated.setScale(2, RoundingMode.HALF_UP), availableMonths, copiedFrom);
    }
}
