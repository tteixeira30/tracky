package com.tracky.contribution;

import com.tracky.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Aplica os depósitos mensais automaticamente. Corre no arranque (catch-up de
 * meses perdidos enquanto a app esteve desligada) e diariamente às 00:10 —
 * só há efeito quando o mês pendente chegou ao dia configurado do reforço.
 */
@Component
public class ContributionScheduler {

    private static final Logger log = LoggerFactory.getLogger(ContributionScheduler.class);

    private final ContributionService service;
    private final UserRepository userRepository;

    public ContributionScheduler(ContributionService service, UserRepository userRepository) {
        this.service = service;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        applyAll();
    }

    @Scheduled(cron = "0 10 0 * * *")
    public void daily() {
        applyAll();
    }

    private void applyAll() {
        userRepository.findAll().forEach(user -> {
            try {
                var result = service.apply(user.getId(), "all", false);
                if (!result.applied().isEmpty()) {
                    log.info("Depósitos mensais aplicados ao utilizador {}: {} itens, {}€",
                            user.getId(), result.applied().size(), result.totalAmount());
                }
            } catch (Exception e) {
                log.error("Falha ao aplicar depósitos do utilizador {}: {}", user.getId(), e.getMessage());
            }
        });
    }
}
