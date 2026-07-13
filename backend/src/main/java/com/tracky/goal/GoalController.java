package com.tracky.goal;

import com.tracky.auth.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalRepository repo;

    public GoalController(GoalRepository repo) {
        this.repo = repo;
    }

    public record GoalRequest(@NotBlank String name, @NotNull @Positive BigDecimal targetAmount,
                              @NotNull @Positive BigDecimal monthlyAllocation, BigDecimal savedAmount,
                              Boolean autoDeposit) {}
    public record ContributionRequest(@NotNull BigDecimal amount) {}
    public record GoalDto(Long id, String name, BigDecimal targetAmount, BigDecimal monthlyAllocation,
                          BigDecimal savedAmount, BigDecimal progressPercent, Integer monthsRemaining,
                          LocalDate estimatedDate, boolean autoDeposit) {}

    @GetMapping
    public List<GoalDto> list(@AuthenticationPrincipal User user) {
        return repo.findByUserIdOrderByIdAsc(user.getId()).stream().map(this::toDto).toList();
    }

    @PostMapping
    public GoalDto create(@AuthenticationPrincipal User user, @Valid @RequestBody GoalRequest req) {
        Goal g = new Goal();
        g.setUserId(user.getId());
        apply(g, req);
        return toDto(repo.save(g));
    }

    @PutMapping("/{id}")
    public GoalDto update(@AuthenticationPrincipal User user, @PathVariable Long id,
                          @Valid @RequestBody GoalRequest req) {
        Goal g = repo.findByIdAndUserId(id, user.getId()).orElseThrow();
        apply(g, req);
        return toDto(repo.save(g));
    }

    @PostMapping("/{id}/contribute")
    public GoalDto contribute(@AuthenticationPrincipal User user, @PathVariable Long id,
                              @Valid @RequestBody ContributionRequest req) {
        Goal g = repo.findByIdAndUserId(id, user.getId()).orElseThrow();
        g.setSavedAmount(g.getSavedAmount().add(req.amount()).max(BigDecimal.ZERO));
        return toDto(repo.save(g));
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        repo.findByIdAndUserId(id, user.getId()).ifPresent(repo::delete);
    }

    private void apply(Goal g, GoalRequest req) {
        g.setName(req.name());
        g.setTargetAmount(req.targetAmount());
        g.setMonthlyAllocation(req.monthlyAllocation());
        if (req.savedAmount() != null) g.setSavedAmount(req.savedAmount());
        boolean auto = Boolean.TRUE.equals(req.autoDeposit());
        // ao ativar, marca o mês atual como já aplicado — o primeiro depósito automático é no próximo mês
        if (auto && !g.isAutoDeposit()) g.setLastAppliedMonth(YearMonth.now().toString());
        g.setAutoDeposit(auto);
    }

    private GoalDto toDto(Goal g) {
        BigDecimal remaining = g.getTargetAmount().subtract(g.getSavedAmount());
        BigDecimal progress = g.getTargetAmount().signum() == 0 ? BigDecimal.ZERO
                : g.getSavedAmount().multiply(BigDecimal.valueOf(100))
                    .divide(g.getTargetAmount(), 1, RoundingMode.HALF_UP)
                    .min(BigDecimal.valueOf(100));
        Integer months = null;
        LocalDate estimated = null;
        if (remaining.signum() <= 0) {
            months = 0;
        } else if (g.getMonthlyAllocation() != null && g.getMonthlyAllocation().signum() > 0) {
            months = remaining.divide(g.getMonthlyAllocation(), 0, RoundingMode.CEILING).intValue();
            estimated = LocalDate.now().plusMonths(months);
        }
        return new GoalDto(g.getId(), g.getName(), g.getTargetAmount(), g.getMonthlyAllocation(),
                g.getSavedAmount(), progress, months, estimated, g.isAutoDeposit());
    }
}
