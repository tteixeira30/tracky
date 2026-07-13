package com.tracky.currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Câmbio EUR -> outra moeda, para apresentar valores na moeda base do utilizador.
 * O cálculo interno da app é sempre em EUR; esta conversão é apenas de apresentação.
 * Usa Yahoo Finance (par EUR{CUR}=X), sem API key, com cache.
 */
@Service
public class CurrencyService {

    private static final Logger log = LoggerFactory.getLogger(CurrencyService.class);
    private static final long TTL_MS = 600_000; // 10 min

    /** Moedas suportadas (ISO 4217). EUR é a referência interna. */
    public static final List<String> SUPPORTED =
            List.of("EUR", "USD", "GBP", "BRL", "CHF", "CAD", "AUD", "JPY");

    private final RestClient http = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private record Cached(Optional<BigDecimal> rate, long expiresAt) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public boolean isSupported(String currency) {
        return currency != null && SUPPORTED.contains(currency.toUpperCase(Locale.ROOT));
    }

    public String normalize(String currency) {
        if (currency == null) return "EUR";
        String c = currency.trim().toUpperCase(Locale.ROOT);
        return isSupported(c) ? c : "EUR";
    }

    /** Quantas unidades de {@code currency} valem 1 EUR (taxa EUR -> currency). */
    public Optional<BigDecimal> rateFromEur(String currency) {
        if (currency == null || currency.isBlank() || currency.equalsIgnoreCase("EUR")) {
            return Optional.of(BigDecimal.ONE);
        }
        String cur = currency.toUpperCase(Locale.ROOT);
        Cached cached = cache.get(cur);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.rate();

        Optional<BigDecimal> rate;
        try {
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/EUR" + cur
                    + "=X?range=1d&interval=1d";
            String body = http.get().uri(url).retrieve().body(String.class);
            JsonNode result = mapper.readTree(body).path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) throw new IllegalStateException("sem cotação");
            BigDecimal r = new BigDecimal(result.get(0).path("meta").path("regularMarketPrice").asText());
            rate = r.signum() > 0 ? Optional.of(r) : Optional.empty();
        } catch (Exception e) {
            log.warn("Falha ao obter câmbio EUR->{}: {}", cur, e.getMessage());
            rate = Optional.empty();
        }
        cache.put(cur, new Cached(rate, System.currentTimeMillis() + TTL_MS));
        return rate;
    }

    /** Converte um valor em EUR para a moeda indicada; se a taxa falhar, devolve o valor original. */
    public BigDecimal convertFromEur(BigDecimal eur, String currency) {
        if (eur == null) return null;
        return rateFromEur(currency).map(r -> eur.multiply(r, MathContext.DECIMAL64)).orElse(eur);
    }
}
