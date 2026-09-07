package com.fiap.sast.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.io.IOException;

@Configuration
public class SecurityConfig {
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(email)
                .map(user -> User.withUsername(user.email)
                        .password(user.passwordHash)
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            HttpSessionSecurityContextRepository securityContexts,
            CsrfTokenRepository csrfTokens,
            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokens))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health", "/api/auth/csrf", "/api/auth/login")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .securityContext(security -> security
                        .securityContextRepository(securityContexts))
                .requestCache(cache -> cache
                        .requestCache(new org.springframework.security.web.savedrequest.NullRequestCache()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                objectMapper, response, 401, "Sessão ausente ou expirada"))
                        .accessDeniedHandler((request, response, exception) -> writeProblem(
                                objectMapper, response, 403, "Requisição não autorizada; atualize a página")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
    }

    private static void writeProblem(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            int status,
            String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        var problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatus.valueOf(status), detail);
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
