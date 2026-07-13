package com.tracky.contribution;

import com.tracky.goal.Goal;
import com.tracky.goal.GoalRepository;
import com.tracky.investment.Investment;
import com.tracky.investment.InvestmentRepository;
import com.tracky.investment.PriceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Aplica os depósitos mensais automáticos a objetivos e investimentos.
 * Modo calendário: aplica os meses em atraso desde o último aplicado (catch-up,
 * porque a app corre localmente e pode estar desligada no dia 1).
 * Modo forçado (botão "Simular"): aplica exatamente um mês e avança o marcador,
 * para o scheduler não voltar a aplicar o mesmo mês.
 */
@Service
public class ContributionService {

    private static final Logger log = LoggerFactory.getLogger(ContributionService.class);

    private final GoalRepository goalRepository;
    private final InvestmentRepository investmentRepository;
    private final PriceService priceService;

    public ContributionService(GoalRepository goalRepository, InvestmentRepository investmentRepository,
                               PriceService priceService) {
        this.goalRepository = goalRepository;
        this.investmentRepository = investmentRepository;
        this.priceService = priceService;
    }

    public record AppliedItem(String type, String name, int months, BigDecimal amount) {}
    public record ApplyResult(List<AppliedItem> applied, BigDecimal totalAmount) {}

    @Transactional
    public ApplyResult apply(Long userId, String scope, boolean force) {
        List<AppliedItem> applied = new ArrayList<>();

        if (!"investments".equals(scope)) {
            for (Goal goal : goalRepository.findByUserIdOrderByIdAsc(userId)) {
                if (!goal.isAutoDeposit() || goal.getMonthlyAllocation() == null
                        || goal.getMonthlyAllocation().signum() <= 0) continue;
                int months = monthsToApply(goal.getLastAppliedMonth(), force);
                if (months <= 0) continue;
                BigDecimal amount = goal.getMonthlyAllocation().multiply(BigDecimal.valueOf(months));
                goal.setSavedAmount(goal.getSavedAmount().add(amount));
                goal.setLastAppliedMonth(advance(goal.getLastAppliedMonth(), months));
                goalRepository.save(goal);
                applied.add(new AppliedItem("goal", goal.getName(), months, amount));
            }
        }

        if (!"goals".equals(scope)) {
            for (Investment inv : investmentRepository.findByUserIdOrderByIdAsc(userId)) {
                if (inv.getMonthlyContribution() == null || inv.getMonthlyContribution().signum() <= 0) continue;
                int months = monthsToApply(inv.getLastAppliedMonth(), force);
                if (months <= 0) continue;
                BigDecimal amount = inv.getMonthlyContribution().multiply(BigDecimal.valueOf(months));

                if (inv.getQuantity() != null && inv.getSymbol() != null) {
                    // compra de unidades ao preço atual; sem preço disponível fica pendente e tenta na próxima
                    Optional<BigDecimal> price = priceService.getPriceEur(inv.getSymbol(), inv.getType());
                    if (price.isEmpty() || price.get().signum() <= 0) {
                        log.warn("Reforço de {} adiado: sem preço para {}", inv.getName(), inv.getSymbol());
                        continue;
                    }
                    inv.setQuantity(inv.getQuantity().add(amount.divide(price.get(), 8, RoundingMode.HALF_UP)));
                } else {
                    inv.setFallbackValue(inv.getFallbackValue().add(amount));
                }
                inv.setInitialValue(inv.getInitialValue().add(amount));
                inv.setLastAppliedMonth(advance(inv.getLastAppliedMonth(), months));
                investmentRepository.save(inv);
                applied.add(new AppliedItem("investment", inv.getName(), months, amount));
            }
        }

        BigDecimal total = applied.stream().map(AppliedItem::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ApplyResult(applied, total.setScale(2, RoundingMode.HALF_UP));
    }

    private int monthsToApply(String lastAppliedMonth, boolean force) {
        if (force) return 1;
        if (lastAppliedMonth == null) return 0;
        long pending = ChronoUnit.MONTHS.between(YearMonth.parse(lastAppliedMonth), YearMonth.now());
        return (int) Math.max(0, pending);
    }

    private String advance(String lastAppliedMonth, int months) {
        YearMonth base = lastAppliedMonth == null ? YearMonth.now() : YearMonth.parse(lastAppliedMonth);
        return base.plusMonths(months).toString();
    }
}
