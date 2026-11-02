package com.smallbiz.reconciliation.account;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class AccountSecurityConfiguration {
    @Bean Clock accountClock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    // Prevent default generated-password users; only database-backed opaque bearer sessions are accepted.
    @Bean UserDetailsService disabledPasswordLogin() { return username -> { throw new UsernameNotFoundException("Unavailable"); }; }

    @Bean SecurityFilterChain accountSecurity(HttpSecurity http, AuthService auth, ObjectMapper mapper,
            Environment environment, CorsConfigurationSource browserCors,
            @Value("${app.legacy.enabled:false}") boolean enabled) throws Exception {
        boolean legacy = enabled && environment.matchesProfiles("legacy-dev");
        http.csrf(csrf -> csrf.disable()) // No credential cookies or browser HTTP authentication.
                .cors(cors -> cors.configurationSource(browserCors))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable()).httpBasic(basic -> basic.disable()).logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request,response,ex) -> error(response,mapper,AccountException.unauthenticated()))
                        .accessDeniedHandler((request,response,ex) -> error(response,mapper,AccountException.forbidden())))
                .authorizeHttpRequests(rules -> {
                    rules.requestMatchers(HttpMethod.POST,"/api/v1/auth/signup","/api/v1/auth/login").permitAll();
                    rules.requestMatchers("/api/v1/auth/**","/api/v1/tenants/**",
                            "/api/v1/ai-review/reviews","/api/v1/ai-review/capabilities").authenticated();
                    if (legacy) rules.requestMatchers("/api/**").permitAll();
                    else rules.requestMatchers("/api/**").authenticated();
                    rules.anyRequest().permitAll();
                })
                .addFilterBefore(new BearerSessionFilter(auth, mapper, legacy), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private static void error(HttpServletResponse response, ObjectMapper mapper, AccountException ex) throws IOException {
        response.setStatus(ex.status()); response.setContentType("application/json");
        response.getWriter().write(mapper.writeValueAsString(Map.of("code",ex.code(),"message",ex.getMessage())));
    }

    private static final class BearerSessionFilter extends OncePerRequestFilter {
        private final AuthService auth;
        private final ObjectMapper mapper;
        private final boolean legacy;
        BearerSessionFilter(AuthService auth, ObjectMapper mapper, boolean legacy) {
            this.auth = auth; this.mapper = mapper; this.legacy = legacy;
        }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            // Match servletPath, as Spring Security does; the firewall rejects encoded/traversal path variants.
            String path = request.getServletPath();
            if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
            if (path.startsWith("/api/")) response.setHeader("Cache-Control","no-store");
            try {
                var headers = java.util.Collections.list(request.getHeaders("Authorization"));
                if (!headers.isEmpty()) {
                    if (headers.size() != 1 || !headers.getFirst().startsWith("Bearer ")) throw AccountException.unauthenticated();
                    var principal = auth.authenticate(headers.getFirst().substring(7));
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
                    SecurityContextHolder.setContext(context);
                }
                boolean scoped = path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/tenants/")
                        || path.equals("/api/v1/ai-review/reviews") || path.equals("/api/v1/ai-review/capabilities");
                if (path.startsWith("/api/") && !scoped) {
                    if (SecurityContextHolder.getContext().getAuthentication() != null) throw AccountException.forbidden();
                    if (!legacy) throw AccountException.unauthenticated();
                }
            } catch (AccountException ex) { error(response,mapper,ex); return; }
            chain.doFilter(request,response);
        }
    }
}
