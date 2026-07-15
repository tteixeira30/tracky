package com.tracky.investment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Preços em tempo real e históricos, sempre convertidos para EUR.
 * Ações/ETFs: Yahoo Finance (sem API key). Cripto: CoinGecko (sem API key).
 */
@Service
public class PriceService {

    private static final Logger log = LoggerFactory.getLogger(PriceService.class);
    private static final long PRICE_TTL_MS = 60_000;      // 1 min para preços
    private static final long HISTORY_TTL_MS = 600_000;   // 10 min para históricos

    private final RestClient http = RestClient.builder()
            .defaultHeader(HttpHeaders.USER_AGENT,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    private record Cached<T>(T value, long expiresAt) {}
    private final Map<String, Cached<Optional<BigDecimal>>> priceCache = new ConcurrentHashMap<>();
    private final Map<String, Cached<List<PricePoint>>> historyCache = new ConcurrentHashMap<>();
    private final Map<String, String> coinIdCache = new ConcurrentHashMap<>();

    public record PricePoint(LocalDate date, BigDecimal price) {}

    /** Preço atual em EUR, ou vazio se não for possível obter. */
    public Optional<BigDecimal> getPriceEur(String symbol, Investment.Type type) {
        if (symbol == null || symbol.isBlank() || type == Investment.Type.OTHER) return Optional.empty();
        String key = type + ":" + symbol.toUpperCase(Locale.ROOT);
        Cached<Optional<BigDecimal>> cached = priceCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();

        Optional<BigDecimal> price = type == Investment.Type.CRYPTO
                ? cryptoPriceEur(symbol)
                : yahooPriceEur(symbol);
        priceCache.put(key, new Cached<>(price, System.currentTimeMillis() + PRICE_TTL_MS));
        return price;
    }

    /**
     * Descarta o preço em cache de um ativo, para forçar nova cotação no próximo pedido.
     * Usado pelo refresh manual — permite ler dados em tempo real sem esperar pelo TTL.
     */
    public void evictPrice(String symbol, Investment.Type type) {
        if (symbol == null || symbol.isBlank() || type == null) return;
        priceCache.remove(type + ":" + symbol.toUpperCase(Locale.ROOT));
    }

    /**
     * Série diária de preços em EUR para o intervalo pedido.
     * range: 1mo, 3mo, 6mo, 1y
     */
    public List<PricePoint> getHistoryEur(String symbol, Investment.Type type, String range) {
        if (symbol == null || symbol.isBlank() || type == Investment.Type.OTHER) return List.of();
        String key = type + ":" + symbol.toUpperCase(Locale.ROOT) + ":" + range;
        Cached<List<PricePoint>> cached = historyCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();

        List<PricePoint> history = type == Investment.Type.CRYPTO
                ? cryptoHistoryEur(symbol, range)
                : yahooHistoryEur(symbol, range);
        historyCache.put(key, new Cached<>(history, System.currentTimeMillis() + HISTORY_TTL_MS));
        return history;
    }

    // ---------- Yahoo Finance (ações / ETFs) ----------

    private Optional<BigDecimal> yahooPriceEur(String symbol) {
        try {
            JsonNode meta = yahooChart(symbol, "1d").path("meta");
            BigDecimal price = new BigDecimal(meta.path("regularMarketPrice").asText());
            return convertToEur(price, meta.path("currency").asText("EUR"));
        } catch (Exception e) {
            log.warn("Falha ao obter preço Yahoo para {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    private List<PricePoint> yahooHistoryEur(String symbol, String range) {
        try {
            JsonNode result = yahooChart(symbol, range);
            JsonNode timestamps = result.path("timestamp");
            JsonNode closes = result.path("indicators").path("quote").get(0).path("close");
            String currency = result.path("meta").path("currency").asText("EUR");
            BigDecimal fx = fxToEur(currency).orElse(null);
            if (fx == null) return List.of();

            List<PricePoint> points = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes.get(i) == null || closes.get(i).isNull()) continue;
                LocalDate date = Instant.ofEpochSecond(timestamps.get(i).asLong())
                        .atZone(ZoneOffset.UTC).toLocalDate();
                BigDecimal price = new BigDecimal(closes.get(i).asText()).multiply(fx);
                points.add(new PricePoint(date, price));
            }
            return points;
        } catch (Exception e) {
            log.warn("Falha ao obter histórico Yahoo para {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    private JsonNode yahooChart(String symbol, String range) throws Exception {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + symbol
                + "?range=" + range + "&interval=1d";
        String body = http.get().uri(url).retrieve().body(String.class);
        JsonNode result = mapper.readTree(body).path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) throw new IllegalStateException("símbolo não encontrado");
        return result.get(0);
    }

    // ---------- CoinGecko (cripto) ----------

    private Optional<String> resolveCoinId(String symbol) {
        String sym = symbol.toLowerCase(Locale.ROOT);
        String cached = coinIdCache.get(sym);
        if (cached != null) return Optional.of(cached);
        try {
            String body = http.get()
                    .uri("https://api.coingecko.com/api/v3/search?query=" + sym)
                    .retrieve().body(String.class);
            for (JsonNode coin : mapper.readTree(body).path("coins")) {
                if (coin.path("symbol").asText().equalsIgnoreCase(sym)) {
                    String id = coin.path("id").asText();
                    coinIdCache.put(sym, id);
                    return Optional.of(id);
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao resolver cripto {}: {}", symbol, e.getMessage());
        }
        return Optional.empty();
    }

    private Optional<BigDecimal> cryptoPriceEur(String symbol) {
        return resolveCoinId(symbol).flatMap(id -> {
            try {
                String body = http.get()
                        .uri("https://api.coingecko.com/api/v3/simple/price?ids=" + id + "&vs_currencies=eur")
                        .retrieve().body(String.class);
                JsonNode price = mapper.readTree(body).path(id).path("eur");
                return price.isMissingNode() ? Optional.empty() : Optional.of(new BigDecimal(price.asText()));
            } catch (Exception e) {
                log.warn("Falha ao obter preço CoinGecko para {}: {}", symbol, e.getMessage());
                return Optional.empty();
            }
        });
    }

    private List<PricePoint> cryptoHistoryEur(String symbol, String range) {
        int days = switch (range) {
            case "1mo" -> 30;
            case "3mo" -> 90;
            case "6mo" -> 180;
            default -> 365;
        };
        return resolveCoinId(symbol).map(id -> {
            try {
                String body = http.get()
                        .uri("https://api.coingecko.com/api/v3/coins/" + id
                                + "/market_chart?vs_currency=eur&days=" + days + "&interval=daily")
                        .retrieve().body(String.class);
                List<PricePoint> points = new ArrayList<>();
                for (JsonNode pair : mapper.readTree(body).path("prices")) {
                    LocalDate date = Instant.ofEpochMilli(pair.get(0).asLong())
                            .atZone(ZoneOffset.UTC).toLocalDate();
                    BigDecimal price = new BigDecimal(pair.get(1).asText());
                    // interval=daily pode devolver mais do que um ponto por dia; fica o último
                    if (!points.isEmpty() && points.getLast().date().equals(date)) {
                        points.removeLast();
                    }
                    points.add(new PricePoint(date, price));
                }
                return points;
            } catch (Exception e) {
                log.warn("Falha ao obter histórico CoinGecko para {}: {}", symbol, e.getMessage());
                return List.<PricePoint>of();
            }
        }).orElse(List.of());
    }

    // ---------- Câmbio ----------

    private Optional<BigDecimal> convertToEur(BigDecimal amount, String currency) {
        return fxToEur(currency).map(fx -> amount.multiply(fx, MathContext.DECIMAL64));
    }

    private Optional<BigDecimal> fxToEur(String currency) {
        if (currency == null || currency.isBlank() || currency.equalsIgnoreCase("EUR")) {
            return Optional.of(BigDecimal.ONE);
        }
        // Yahoo cota pences para ações de Londres
        if (currency.equalsIgnoreCase("GBp")) {
            return fxToEur("GBP").map(fx -> fx.divide(BigDecimal.valueOf(100), MathContext.DECIMAL64));
        }
        String key = "FX:" + currency.toUpperCase(Locale.ROOT);
        Cached<Optional<BigDecimal>> cached = priceCache.get(key);
        if (cached != null && cached.expiresAt() > System.currentTimeMillis()) return cached.value();
        Optional<BigDecimal> rate;
        try {
            JsonNode meta = yahooChart(currency.toUpperCase(Locale.ROOT) + "EUR=X", "1d").path("meta");
            rate = Optional.of(new BigDecimal(meta.path("regularMarketPrice").asText()));
        } catch (Exception e) {
            log.warn("Falha ao obter câmbio {}->EUR: {}", currency, e.getMessage());
            rate = Optional.empty();
        }
        priceCache.put(key, new Cached<>(rate, System.currentTimeMillis() + HISTORY_TTL_MS));
        return rate;
    }
}
