package com.tansoflow.tansocore.filter;

import com.tansoflow.tansocore.auth.UserContext;
import com.tansoflow.tansocore.model.entitlement.response.EntitlementResponse;
import com.tansoflow.tansocore.service.client.ClientEntitlementService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class EntitlementAuthFilter extends OncePerRequestFilter {

    private final ClientEntitlementService clientEntitlementService;
    private final com.tansoflow.tansocore.property.AppProperty appProperty;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!appProperty.isDogfoodingEnabled()) {
            return true;
        }
        String p = request.getRequestURI();
        // Skip for public endpoints, health checks, and docs
        // Also skip for subscription endpoints - users need these before they have entitlements
        return p.startsWith("/public/")
                || p.startsWith("/api/v1/account/subscribe")
                || p.startsWith("/api/v1/account/subscription-status")
                || p.startsWith("/api/v1/tanso/account/onboarding")
                || p.startsWith("/actuator/")
                || p.startsWith("/v3/api-docs")
                || p.startsWith("/swagger-ui")
                || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserContext principal) {
            if (!principal.getAccountId().equals(appProperty.getMasterAccountId())) {
                // For now, let's gate API access globally if they are using the platform UI
                // This is a dogfooding example of feature gating.
                EntitlementResponse entitlement = clientEntitlementService.checkEntitlement(
                        principal.getAccountId(),
                        appProperty.getMasterAccountId(),
                        "feature_api_access",
                        false
                );

                if (!entitlement.isAllowed()) {
                    log.warn("Account {} is not entitled to feature_api_access", principal.getAccountId());
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"error\":\"entitlement_denied\",\"feature\":\"feature_api_access\",\"upgradeUrl\":\"/select-plan\"}"
                    );
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }
}
