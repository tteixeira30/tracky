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
import java.util.List;
import java.util.Optional;

interface IncomeSettingsRepository extends JpaRepository<IncomeSettings, Long> {
    Optional<IncomeSettings> findByUserId(Long userId);
}

interface AllocationRepository extends JpaRepository<Allocation, Long> {
    List<Allocation> findByUserIdOrderByIdAsc(Long userId);
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
    public record IncomeResponse(BigDecimal monthlyIncome, List<AllocationDto> allocations,
                                 BigDecimal totalAllocated, BigDecimal totalPercentage, BigDecimal unallocated) {}
    public record IncomeRequest(@NotNull BigDecimal monthlyIncome) {}
    /** Ou percentage ou fixedAmount — exatamente um dos dois. */
    public record AllocationRequest(@NotBlank String name, BigDecimal percentage, BigDecimal fixedAmount) {}

    private IncomeSettings settings(User user) {
        return incomeRepo.findByUserId(user.getId()).orElseGet(() -> {
            IncomeSettings s = new IncomeSettings();
            s.setUserId(user.getId());
            return incomeRepo.save(s);
        });
    }

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

    @GetMapping
    public IncomeResponse get(@AuthenticationPrincipal User user) {
        IncomeSettings s = settings(user);
        BigDecimal income = s.getMonthlyIncome();

        List<AllocationDto> allocations = allocationRepo.findByUserIdOrderByIdAsc(user.getId()).stream()
                .map(a -> {
                    BigDecimal amount = a.getFixedAmount() != null
                            ? a.getFixedAmount()
                            : income.multiply(a.getPercentage()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
                    BigDecimal effectivePct = income.signum() > 0
                            ? amount.multiply(HUNDRED).divide(income, 1, RoundingMode.HALF_UP)
                            : a.getPercentage(); // sem rendimento definido, só a % declarada faz sentido
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
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unallocated = income.subtract(totalAllocated);

        return new IncomeResponse(income, allocations, totalAllocated.setScale(2, RoundingMode.HALF_UP),
                totalPct, unallocated.setScale(2, RoundingMode.HALF_UP));
    }

    @PutMapping
    public IncomeResponse setIncome(@AuthenticationPrincipal User user, @Valid @RequestBody IncomeRequest req) {
        IncomeSettings s = settings(user);
        s.setMonthlyIncome(req.monthlyIncome());
        incomeRepo.save(s);
        return get(user);
    }

    @PostMapping("/allocations")
    public IncomeResponse addAllocation(@AuthenticationPrincipal User user, @Valid @RequestBody AllocationRequest req) {
        validate(req);
        Allocation a = new Allocation();
        a.setUserId(user.getId());
        apply(a, req);
        allocationRepo.save(a);
        return get(user);
    }

    @PutMapping("/allocations/{id}")
    public IncomeResponse updateAllocation(@AuthenticationPrincipal User user, @PathVariable Long id,
                                           @Valid @RequestBody AllocationRequest req) {
        validate(req);
        Allocation a = allocationRepo.findByIdAndUserId(id, user.getId()).orElseThrow();
        apply(a, req);
        allocationRepo.save(a);
        return get(user);
    }

    @DeleteMapping("/allocations/{id}")
    public IncomeResponse deleteAllocation(@AuthenticationPrincipal User user, @PathVariable Long id) {
        allocationRepo.findByIdAndUserId(id, user.getId()).ifPresent(allocationRepo::delete);
        return get(user);
    }
}
