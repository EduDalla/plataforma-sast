package com.fiap.sast.auth;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean org.springframework.security.core.userdetails.UserDetailsService userDetailsService(UserRepository users) {
        return email -> {
            var user = users.findByEmail(email).orElseThrow(() ->
                    new org.springframework.security.core.userdetails.UsernameNotFoundException("Usuário não encontrado"));
            return org.springframework.security.core.userdetails.User.withUsername(user.email)
                    .password(user.passwordHash).roles("USER").build();
        };
    }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,
            HttpSessionSecurityContextRepository repository) throws Exception {
        return http
                .authorizeHttpRequests(a -> a.requestMatchers("/health", "/api/auth/csrf", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .securityContext(c -> c.securityContextRepository(repository))
                .requestCache(c -> c.requestCache(new org.springframework.security.web.savedrequest.NullRequestCache()))
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> problem(res, 401, "Sessão ausente ou expirada"))
                        .accessDeniedHandler((req, res, ex) -> problem(res, 403, "Requisição não autorizada; atualize a página")))
                .logout(l -> l.logoutUrl("/api/auth/logout").invalidateHttpSession(true).deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .build();
    }
    private static void problem(HttpServletResponse response, int status, String detail) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }
}
