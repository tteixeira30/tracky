package com.tracky.currency;

import com.tracky.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@RestController
@RequestMapping("/api/currency")
public class CurrencyController {

    private final CurrencyService currencyService;

    public CurrencyController(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    /** rate = quantas unidades da moeda base valem 1 EUR (para converter valores EUR -> base no cliente). */
    public record CurrencyInfo(String base, BigDecimal rate, boolean rateLive, List<String> supported) {}

    @GetMapping
    public CurrencyInfo info(@AuthenticationPrincipal User user) {
        String base = user.getBaseCurrency();
        var rate = currencyService.rateFromEur(base);
        BigDecimal value = rate.orElse(BigDecimal.ONE).setScale(6, RoundingMode.HALF_UP);
        return new CurrencyInfo(base, value, rate.isPresent(), CurrencyService.SUPPORTED);
    }
}
