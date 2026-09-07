package com.fiap.sast.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final HttpSessionSecurityContextRepository securityContexts;
    private final CsrfTokenRepository csrfTokens;

    public AuthController(
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository securityContexts,
            CsrfTokenRepository csrfTokens) {
        this.authenticationManager = authenticationManager;
        this.securityContexts = securityContexts;
        this.csrfTokens = csrfTokens;
    }

    public record Login(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record Session(String email) {
    }

    public record Token(String headerName, String token) {
    }

    @GetMapping("/csrf")
    public Token csrf(CsrfToken token) {
        return new Token(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/session")
    public Session session(Authentication authentication) {
        return new Session(authentication.getName());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody Login input,
            HttpServletRequest request, HttpServletResponse response) {
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

        if (request.getSession(false) != null) {
            request.changeSessionId();
        }

        // O token anterior deixa de ser válido após a autenticação.
        csrfTokens.saveToken(null, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContexts.saveContext(context, request, response);
        return ResponseEntity.ok(new Session(email));
    }
}
