package com.tracky.contribution;

import com.tracky.auth.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testa que o controller delega no serviço com o utilizador, scope e force certos. */
@ExtendWith(MockitoExtension.class)
class ContributionControllerTest {

    @Mock ContributionService service;

    @Test
    void applyDelegaNoServicoComOsParametrosRecebidos() {
        var controller = new ContributionController(service);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        var expected = new ContributionService.ApplyResult(List.of(), BigDecimal.ZERO);
        when(service.apply(7L, "goals", true)).thenReturn(expected);

        var result = controller.apply(user, "goals", true);

        assertThat(result).isSameAs(expected);
        verify(service).apply(7L, "goals", true);
    }
}
