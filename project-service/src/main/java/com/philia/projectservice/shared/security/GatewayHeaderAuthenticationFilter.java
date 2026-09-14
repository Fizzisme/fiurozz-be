package com.philia.projectservice.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public final class GatewayHeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-ID";
    public static final String USER_EMAIL_HEADER = "X-User-Email";
    public static final String USER_ROLES_HEADER = "X-User-Roles";
    public static final String USER_DISPLAY_NAME_HEADER = "X-User-Display-Name";
    public static final String USER_AVATAR_URL_HEADER = "X-User-Avatar-URL";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticateFromTrustedGatewayHeaders(request);
        }
        filterChain.doFilter(request, response);
    }

    private static void authenticateFromTrustedGatewayHeaders(HttpServletRequest request) {
        // The gateway verifies the JWT and strips Authorization before proxying,
        // so X-User-ID is the only identity signal that reaches this service.
        var rawUserId = trimToNull(request.getHeader(USER_ID_HEADER));
        if (rawUserId == null) {
            return;
        }

        final UUID userId;
        try {
            userId = UUID.fromString(rawUserId);
        } catch (IllegalArgumentException exception) {
            return;
        }

        var email = trimToNull(request.getHeader(USER_EMAIL_HEADER));
        var displayName = trimToNull(request.getHeader(USER_DISPLAY_NAME_HEADER));
        if (displayName == null) {
            displayName = email == null ? userId.toString() : email;
        }
        var avatarUrl = trimToNull(request.getHeader(USER_AVATAR_URL_HEADER));
        var principal = new GatewayActorPrincipal(userId, email, displayName, avatarUrl);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities(request.getHeader(USER_ROLES_HEADER))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private static List<SimpleGrantedAuthority> authorities(String rawRoles) {
        if (rawRoles == null || rawRoles.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawRoles.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
