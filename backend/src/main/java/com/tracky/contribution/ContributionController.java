package com.tracky.contribution;

import com.tracky.auth.User;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contributions")
public class ContributionController {

    private final ContributionService service;

    public ContributionController(ContributionService service) {
        this.service = service;
    }

    /**
     * Aplica os depósitos mensais do utilizador autenticado.
     * scope: all | goals | investments.
     * force=true simula o dia 1 do próximo mês (aplica um mês e avança o marcador).
     */
    @PostMapping("/apply")
    public ContributionService.ApplyResult apply(@AuthenticationPrincipal User user,
                                                 @RequestParam(defaultValue = "all") String scope,
                                                 @RequestParam(defaultValue = "false") boolean force) {
        return service.apply(user.getId(), scope, force);
    }
}
