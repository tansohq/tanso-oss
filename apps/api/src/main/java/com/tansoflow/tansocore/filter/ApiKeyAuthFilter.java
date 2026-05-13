package com.tansoflow.tansocore.filter;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.auth.UserContextAuthentication;
import com.tansoflow.tansocore.entity.Account;
import com.tansoflow.tansocore.service.internal.account.AccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private final AccountService accountService;
    private static final AntPathMatcher PM = new AntPathMatcher();

    public ApiKeyAuthFilter(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        // Skip for public endpoints (keeps logs cleaner)
        return PM.match("/api/stripe/webhook", p)
                || PM.match("/actuator/health/**", p)
                || PM.match("/actuator/health", p)
                || PM.match("/actuator/info", p)
                || PM.match("/v3/api-docs/**", p)
                || PM.match("/swagger-ui/**", p)
                || "OPTIONS".equalsIgnoreCase(request.getMethod()); // allow CORS preflight
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        // If something already authenticated earlier, pass through
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            chain.doFilter(request, response);
            return;
        }

        // Accept either Authorization: Bearer <key> or X-API-Key: <key>
        String apiKey = extractApiKey(request);
        if (apiKey == null || apiKey.isBlank()) {
            // No key provided -> let the entry point return 401 later
            chain.doFilter(request, response);
            return;
        }


        if (apiKey.startsWith("sk_live_") || apiKey.startsWith("sk_test_")) {

            Account account = accountService.findByApiKey(apiKey);

            if (account == null) {
                String keyPrefix = apiKey.substring(0, Math.min(12, apiKey.length())) + "...";
                log.warn("API key authentication failed: keyPrefix={}, path={}", keyPrefix, request.getRequestURI());
                writeUnauthorized(response);
                return;
            }


            // Build principal (avoid keeping the raw apiKey in memory/logs)
            UserContext principal = new UserContext(
                    account.getId().toString(),
                    /* DO NOT store the raw key here; store keyId if you have one: */ null
            );

            List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_CLIENT"));

            UserContextAuthentication auth = new UserContextAuthentication(principal, authorities);
            // If you keep this class, consider adding a setter to inject authorities or move to AbstractAuthenticationToken.
            // For now, the class returns empty authorities; update it to use 'authorities' above.

            auth.setAuthenticated(true);
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(auth);

            MDC.put("accountId", account.getId().toString());
        }

        chain.doFilter(request, response);
    }

    private static String extractApiKey(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return h.substring(7).trim();
        }
        return request.getHeader("X-API-Key");
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + "invalid_api_key" + "\"}");
    }
}
