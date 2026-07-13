/*
 * Tanso Core - open-source B2B SaaS monetization engine
 * Copyright (C) 2026  Douglas Baek
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.tansoflow.tansocore.config;

import com.tansoflow.tansocore.auth.JwtService;
import com.tansoflow.tansocore.filter.ApiKeyAuthFilter;
import com.tansoflow.tansocore.filter.EntitlementAuthFilter;
import com.tansoflow.tansocore.filter.JwtAuthFilter;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor

@Slf4j
public class SecurityConfig {

    private final AccountService accountService;
    private final JwtService jwtService;
    private final com.tansoflow.tansocore.service.client.ClientEntitlementService clientEntitlementService;
    private final com.tansoflow.tansocore.property.AppProperty appProperty;

    @Bean
    @Order(0)
    @ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
    public SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(accountService);
        http
                .securityMatcher("/mcp/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/mcp/**").authenticated()
                        .anyRequest().denyAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                )
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain filterChainClient(HttpSecurity http) throws Exception {
        ApiKeyAuthFilter apiKeyFilter = new ApiKeyAuthFilter(accountService);
        EntitlementAuthFilter entitlementAuthFilter = new EntitlementAuthFilter(clientEntitlementService, appProperty);

        http
                // API only: no sessions, no login pages, no httpBasic
                .securityMatcher("/api/v1/client/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // CORS (define bean below)
                .cors(Customizer.withDefaults())

                // Precise authorization
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/client/**").authenticated()
                        .anyRequest().denyAll()
                )

                // JSON 401/403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"forbidden\"}");
                        })
                )

                // Place your API key filter early (before Anonymous)
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(entitlementAuthFilter, ApiKeyAuthFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChainTanso(HttpSecurity http) throws Exception {
        // TODO: Replace this JWTFilter later with a custom one
        JwtAuthFilter apiKeyFilter = new JwtAuthFilter(jwtService);
        EntitlementAuthFilter entitlementAuthFilter = new EntitlementAuthFilter(clientEntitlementService, appProperty);

        http
                // API only: no sessions, no login pages, no httpBasic
                .securityMatcher("/api/v1/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // CORS (define bean below)
                .cors(Customizer.withDefaults())

                // Precise authorization
                .authorizeHttpRequests(auth -> auth
                        // CORS preflight
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                )

                // JSON 401/403
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"unauthorized\"}");
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json");
                            res.getWriter().write("{\"error\":\"forbidden\"}");
                        })
                )

                // Place your API key filter early (before Anonymous)
                .addFilterBefore(apiKeyFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(entitlementAuthFilter, JwtAuthFilter.class);

        return http.build();
    }

    @Bean
    @Order(99)
    public SecurityFilterChain catchAll(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html","/public/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/admin/**", "/public/stripe/ingest/webhook/**").permitAll()
                        // TODO: Remove this after Stripe integration for webhooks is completed.
                        .requestMatchers("/public").denyAll()
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(appProperty.getCorsAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-API-Key", "X-Requested-With"));
        cfg.setExposedHeaders(List.of());
        boolean hasWildcard = appProperty.getCorsAllowedOrigins().contains("*");
        cfg.setAllowCredentials(!hasWildcard);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    // Optionally define a no-op UserDetailsService to avoid the default user creation /log line:
    @Bean
    UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
