package com.fiap.sast.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthController(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public record Login(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record Session(String email, String accessToken, String tokenType, long expiresIn) {
    }

    public record CurrentSession(String email) {
    }

    @org.springframework.web.bind.annotation.GetMapping("/session")
    public CurrentSession session(Authentication authentication) {
        return new CurrentSession(authentication.getName());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody Login input) {
        var email = input.email().trim().toLowerCase(Locale.ROOT);
        Authentication authentication;

        try {
            var credentials = UsernamePasswordAuthenticationToken.unauthenticated(
                    email,
                    input.password());
            authentication = authenticationManager.authenticate(credentials);
        } catch (AuthenticationException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ProblemDetail.forStatusAndDetail(
                            HttpStatus.UNAUTHORIZED,
                            "E-mail ou senha inválidos"));
        }

        var issued = jwtService.issue(authentication.getName());
        return ResponseEntity.ok(new Session(
                authentication.getName(),
                issued.accessToken(),
                "Bearer",
                issued.expiresIn()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        // JWT é stateless: o cliente descarta o token em memória.
        return ResponseEntity.noContent().build();
    }
}
