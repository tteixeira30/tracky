package com.tracky.auth;

import com.tracky.currency.CurrencyService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Se definido, o registo exige este código de convite. Vazio = registo aberto (uso local). */
    private final String inviteCode;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                          @Value("${tracky.invite-code:}") String inviteCode) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.inviteCode = inviteCode == null ? "" : inviteCode.trim();
    }

    public record RegisterRequest(@NotBlank String name,
                                  @NotBlank @Email String email,
                                  @NotBlank @Size(min = 6, message = "A palavra-passe deve ter pelo menos 6 caracteres") String password,
                                  String inviteCode) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}
    public record UserDto(Long id, String name, String email, String baseCurrency) {}
    public record AuthResponse(String token, UserDto user) {}
    public record CurrencyRequest(@NotBlank String baseCurrency) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        if (!inviteCode.isEmpty() && !inviteCode.equals(req.inviteCode() == null ? "" : req.inviteCode().trim())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Código de convite inválido."));
        }
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(email)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Já existe uma conta com este email."));
        }
        User user = new User();
        user.setName(req.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user = userRepository.save(user);
        return ResponseEntity.ok(new AuthResponse(jwtService.generate(user.getId()), toDto(user)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        Optional<User> user = userRepository.findByEmail(req.email().trim().toLowerCase(Locale.ROOT));
        if (user.isEmpty() || !passwordEncoder.matches(req.password(), user.get().getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Email ou palavra-passe incorretos."));
        }
        return ResponseEntity.ok(new AuthResponse(jwtService.generate(user.get().getId()), toDto(user.get())));
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal User user) {
        return toDto(user);
    }

    @PutMapping("/me/currency")
    public UserDto setCurrency(@AuthenticationPrincipal User user, @Valid @RequestBody CurrencyRequest req) {
        String c = req.baseCurrency().trim().toUpperCase(Locale.ROOT);
        if (!CurrencyService.SUPPORTED.contains(c)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Moeda não suportada: " + req.baseCurrency());
        }
        user.setBaseCurrency(c);
        userRepository.save(user);
        return toDto(user);
    }

    private UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getBaseCurrency());
    }
}
