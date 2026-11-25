package com.flagship.server.security;

import com.flagship.server.config.FlagshipServerProperties;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates requests bearing either a JWT Bearer token or an {@code X-API-Key} SDK header.
 */
@Component
public class JwtAndApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final FlagshipServerProperties properties;

    public JwtAndApiKeyAuthenticationFilter(
            JwtTokenService jwtTokenService,
            FlagshipServerProperties properties
    ) {
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String authHeader = request.getHeader(AUTHORIZATION_HEADER);
            String apiKeyHeader = request.getHeader(API_KEY_HEADER);

            String token = null;
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                token = authHeader.substring(BEARER_PREFIX.length()).trim();
            } else if (request.getParameter("token") != null) {
                token = request.getParameter("token").trim();
            } else if (request.getParameter("access_token") != null) {
                token = request.getParameter("access_token").trim();
            }

            String apiKey = apiKeyHeader != null ? apiKeyHeader.trim() : null;
            if (apiKey == null) {
                if (request.getParameter("apiKey") != null) {
                    apiKey = request.getParameter("apiKey").trim();
                } else if (request.getParameter("api_key") != null) {
                    apiKey = request.getParameter("api_key").trim();
                }
            }

            if (token != null && !token.isBlank()) {
                Optional<JwtTokenService.JwtClaims> claimsOpt = jwtTokenService.verifyToken(token);
                if (claimsOpt.isPresent()) {
                    JwtTokenService.JwtClaims claims = claimsOpt.get();
                    List<SimpleGrantedAuthority> authorities = claims.roles().stream()
                            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                            .map(SimpleGrantedAuthority::new)
                            .toList();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(claims.username(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } else if (apiKey != null && !apiKey.isBlank()) {
                String configuredKey = properties.getSecurity().getBootstrapApiKey();
                if (configuredKey != null && MessageDigest.isEqual(
                        configuredKey.getBytes(StandardCharsets.UTF_8),
                        apiKey.getBytes(StandardCharsets.UTF_8)
                )) {
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_ADMIN"),
                            new SimpleGrantedAuthority("ROLE_APPROVER"),
                            new SimpleGrantedAuthority("ROLE_EDITOR"),
                            new SimpleGrantedAuthority("ROLE_VIEWER"),
                            new SimpleGrantedAuthority("ROLE_SDK")
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken("bootstrap-admin", null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
