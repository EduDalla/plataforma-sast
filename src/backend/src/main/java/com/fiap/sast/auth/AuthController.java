package com.fiap.sast.auth;

import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final HttpSessionSecurityContextRepository contexts;
    private final String dummyHash;
    public AuthController(UserRepository users, PasswordEncoder encoder, HttpSessionSecurityContextRepository contexts) {
        this.users = users; this.encoder = encoder; this.contexts = contexts;
        dummyHash = encoder.encode(UUID.randomUUID().toString());
    }
    public record Login(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}
    public record Session(String email) {}
    public record Token(String headerName, String token) {}
    @GetMapping("/csrf") public Token csrf(CsrfToken token) { return new Token(token.getHeaderName(), token.getToken()); }
    @GetMapping("/session") public Session session(Authentication auth) { return new Session(auth.getName()); }
    @PostMapping("/login") public ResponseEntity<?> login(@Valid @RequestBody Login input,
            HttpServletRequest request, HttpServletResponse response) {
        var user = users.findByEmail(input.email().trim().toLowerCase(Locale.ROOT));
        boolean matches = encoder.matches(input.password(), user.map(u -> u.passwordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) return ResponseEntity.status(401)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user.get().email, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        if (request.getSession(false) != null) request.changeSessionId();
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context); contexts.saveContext(context, request, response);
        return ResponseEntity.ok(new Session(user.get().email));
    }
}
