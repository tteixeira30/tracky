package com.tracky.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Testa o filtro JWT: só popula o SecurityContext com um Bearer válido. */
@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @Mock HttpServletRequest request;
    @Mock HttpServletResponse response;
    @Mock FilterChain chain;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private JwtAuthFilter filter() {
        return new JwtAuthFilter(jwtService, userRepository);
    }

    private Object runWith(String header) throws Exception {
        lenient().when(request.getHeader("Authorization")).thenReturn(header);
        filter().doFilterInternal(request, response, chain);
        verify(chain).doFilter(request, response); // a cadeia continua sempre
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getPrincipal();
    }

    @Test
    void semHeaderNaoAutentica() throws Exception {
        assertThat(runWith(null)).isNull();
    }

    @Test
    void headerSemBearerNaoAutentica() throws Exception {
        assertThat(runWith("Basic abc")).isNull();
    }

    @Test
    void tokenInvalidoNaoAutentica() throws Exception {
        when(jwtService.validate("mau")).thenReturn(Optional.empty());
        assertThat(runWith("Bearer mau")).isNull();
    }

    @Test
    void tokenValidoPopulaOContextoComOUtilizador() throws Exception {
        User user = new User();
        when(jwtService.validate("bom")).thenReturn(Optional.of(42L));
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        assertThat(runWith("Bearer bom")).isSameAs(user);
    }

    @Test
    void tokenValidoMasUtilizadorInexistenteNaoAutentica() throws Exception {
        when(jwtService.validate("bom")).thenReturn(Optional.of(99L));
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(runWith("Bearer bom")).isNull();
    }
}
