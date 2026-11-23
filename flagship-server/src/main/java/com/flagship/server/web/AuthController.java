package com.flagship.server.web;

import com.flagship.server.config.FlagshipServerProperties;
import com.flagship.server.dto.AuthTokenRequest;
import com.flagship.server.dto.AuthTokenResponse;
import com.flagship.server.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "FlagShip Auth")
public class AuthController {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final FlagshipServerProperties properties;

    public AuthController(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            FlagshipServerProperties properties
    ) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
    }

    @PostMapping("/token")
    @Operation(summary = "Exchange FlagShip credentials for a JWT bearer token")
    public AuthTokenResponse issueToken(@Valid @RequestBody AuthTokenRequest request) {
        final UserDetails user;
        try {
            user = userDetailsService.loadUserByUsername(request.username());
        } catch (UsernameNotFoundException ex) {
            throw new BadCredentialsException("Invalid FlagShip credentials");
        }
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid FlagShip credentials");
        }
        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        String token = jwtTokenService.generateToken(user.getUsername(), roles);
        return new AuthTokenResponse(
                token,
                "Bearer",
                properties.getSecurity().getJwtExpirationSeconds(),
                user.getUsername(),
                roles
        );
    }

    @GetMapping("/me")
    @Operation(summary = "Inspect authenticated FlagShip principal")
    public Map<String, Object> currentPrincipal(Authentication authentication) {
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return Map.of(
                "username", authentication.getName(),
                "roles", roles
        );
    }
}
