package com.flagship.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.server.config.FlagshipServerProperties;
import com.flagship.server.exception.GlobalExceptionHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            FlagshipServerProperties properties,
            PasswordEncoder passwordEncoder
    ) {
        FlagshipServerProperties.SecurityProperties sec = properties.getSecurity();
        UserDetails admin = User.builder()
                .username(sec.getAdminUsername())
                .password(passwordEncoder.encode(sec.getAdminPassword()))
                .roles("ADMIN")
                .build();
        UserDetails approver = User.builder()
                .username(sec.getApproverUsername())
                .password(passwordEncoder.encode(sec.getApproverPassword()))
                .roles("APPROVER")
                .build();
        UserDetails editor = User.builder()
                .username(sec.getEditorUsername())
                .password(passwordEncoder.encode(sec.getEditorPassword()))
                .roles("EDITOR")
                .build();
        UserDetails viewer = User.builder()
                .username(sec.getViewerUsername())
                .password(passwordEncoder.encode(sec.getViewerPassword()))
                .roles("VIEWER")
                .build();
        return new InMemoryUserDetailsManager(admin, approver, editor, viewer);
    }

    @Bean
    public AuthenticationEntryPoint problemDetailAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED,
                    authException.getMessage() != null
                            ? authException.getMessage()
                            : "Full authentication is required to access this resource"
            );
            problem.setTitle("Unauthorized");
            problem.setType(URI.create("https://flagship.dev/problems/unauthorized"));
            GlobalExceptionHandler.enrichProblem(problem, request);
            writeProblemDetail(response, HttpStatus.UNAUTHORIZED, problem);
        };
    }

    @Bean
    public AccessDeniedHandler problemDetailAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN,
                    accessDeniedException.getMessage() != null
                            ? accessDeniedException.getMessage()
                            : "Insufficient permissions for this resource"
            );
            problem.setTitle("Forbidden");
            problem.setType(URI.create("https://flagship.dev/problems/forbidden"));
            GlobalExceptionHandler.enrichProblem(problem, request);
            writeProblemDetail(response, HttpStatus.FORBIDDEN, problem);
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAndApiKeyAuthenticationFilter jwtAndApiKeyFilter,
            AuthenticationEntryPoint authenticationEntryPoint,
            AccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/token",
                                "/actuator/health",
                                "/actuator/health/**",
                                "/actuator/info",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .httpBasic( basic -> basic.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAndApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private void writeProblemDetail(
            HttpServletResponse response,
            HttpStatus status,
            ProblemDetail problem
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        if (problem.getType() != null) {
            body.put("type", problem.getType().toString());
        }
        if (problem.getTitle() != null) {
            body.put("title", problem.getTitle());
        }
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
