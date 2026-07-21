package com.tracky.investment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Com {@code ddl-auto: update} o Hibernate cria uma CHECK constraint que fixa os
 * valores possíveis do enum {@link Investment.Type}, mas nunca a atualiza quando
 * se adiciona um valor novo (ex.: PPR). Isso faria falhar a inserção de tipos
 * recém-criados. Removemos a constraint no arranque — idempotente e seguro:
 * o mapeamento JPA do enum já garante que só valores válidos são persistidos.
 */
@Component
public class InvestmentSchemaFixup {

    private static final Logger log = LoggerFactory.getLogger(InvestmentSchemaFixup.class);

    private final JdbcTemplate jdbc;

    public InvestmentSchemaFixup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropStaleTypeCheck() {
        try {
            jdbc.execute("ALTER TABLE investments DROP CONSTRAINT IF EXISTS investments_type_check");
        } catch (Exception e) {
            // não impede o arranque — apenas regista para diagnóstico
            log.warn("Não foi possível remover investments_type_check: {}", e.getMessage());
        }
    }
}
