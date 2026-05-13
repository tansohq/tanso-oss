package com.tansoflow.tansocore.filter;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.tansoflow.tansocore.auth.JwtService;
import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.auth.UserContextAuthentication;
import com.tansoflow.tansocore.model.auth.JwtClaims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        // Skip if already authenticated (e.g., by API key)
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);

            // Skip machine keys (handled by ApiKeyAuthFilter)
            if (token.startsWith("sk_live_") || token.startsWith("sk_test_")) {
                chain.doFilter(request, response);
                return;
            }

            try {
                JwtClaims claims = jwtService.parseAndValidate(token);
                UserContext principal = new UserContext(
                        claims.subject(),
                        claims.accountId(),
                        claims.email(),
                        null
                );

                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_TANSO_UI"));

                UserContextAuthentication auth = new UserContextAuthentication(principal, authorities);
                // If you keep this class, consider adding a setter to inject authorities or move to AbstractAuthenticationToken.
                // For now, the class returns empty authorities; update it to use 'authorities' above.

                auth.setAuthenticated(true);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);

                MDC.put("userId", claims.subject());
                MDC.put("accountId", claims.accountId());
            } catch (JWTVerificationException e) {
                log.warn("JWT authentication failed: reason={}, path={}", e.getMessage(), request.getRequestURI());
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"invalid_or_expired_token\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}

