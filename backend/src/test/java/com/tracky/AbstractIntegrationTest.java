package com.tracky;

import com.tracky.contribution.ContributionScheduler;
import com.tracky.currency.CurrencyService;
import com.tracky.investment.PriceService;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base dos testes de integração: Postgres real (Testcontainers) partilhado por
 * todas as classes, MockMvc, e os serviços com efeitos externos substituídos
 * por mocks (cotações Yahoo/CoinGecko e o scheduler de depósitos, que corre
 * no arranque do contexto).
 *
 * O container é singleton e nunca é parado explicitamente — o Ryuk do
 * Testcontainers limpa-o no fim. Como o schema é partilhado (ddl-auto: update),
 * o isolamento de dados faz-se com emails únicos por teste, não por truncation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @ServiceConnection
    static PostgreSQLContainer<?> serviceConnection = POSTGRES;

    /** Evita chamadas reais ao Yahoo Finance/CoinGecko durante os testes. */
    @MockitoBean
    protected PriceService priceService;

    @MockitoBean
    protected CurrencyService currencyService;

    /** Evita o catch-up de depósitos automáticos no arranque do contexto. */
    @MockitoBean
    protected ContributionScheduler contributionScheduler;
}
