package com.flagship.server.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flagship.server.config.FlagshipServerProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stateless HMAC-SHA256 JWT token generator and verifier (C3, C4).
 */
@Component
public class JwtTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final FlagshipServerProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public JwtTokenService(FlagshipServerProperties properties) {
        this.properties = properties;
    }

    public String generateToken(String username, List<String> roles) {
        try {
            long nowEpoch = Instant.now().getEpochSecond();
            long expEpoch = nowEpoch + properties.getSecurity().getJwtExpirationSeconds();

            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", username);
            payload.put("roles", roles != null ? roles : List.of());
            payload.put("iat", nowEpoch);
            payload.put("exp", expEpoch);

            String encodedHeader = base64UrlEncode(mapper.writeValueAsBytes(header));
            String encodedPayload = base64UrlEncode(mapper.writeValueAsBytes(payload));
            String signingInput = encodedHeader + "." + encodedPayload;
            String signature = sign(signingInput);
            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT token", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Optional<JwtClaims> verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            String signingInput = parts[0] + "." + parts[1];
            String expectedSig = sign(signingInput);
            if (!MessageDigest.isEqual(
                    expectedSig.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                return Optional.empty();
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> claims = mapper.readValue(payloadBytes, MAP_TYPE);
            String subject = (String) claims.get("sub");
            Number expNumber = (Number) claims.get("exp");
            if (subject == null || subject.isBlank() || expNumber == null) {
                return Optional.empty();
            }
            long nowEpoch = Instant.now().getEpochSecond();
            if (nowEpoch >= expNumber.longValue()) {
                return Optional.empty();
            }
            Object rawRoles = claims.get("roles");
            List<String> roles = (rawRoles instanceof List<?> list)
                    ? list.stream().map(String::valueOf).toList()
                    : List.of();
            return Optional.of(new JwtClaims(subject, roles, expNumber.longValue()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String signingInput) throws Exception {
        byte[] secretBytes = properties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8);
        Mac mac = Mac.getInstance(HMAC_ALG);
        mac.init(new SecretKeySpec(secretBytes, HMAC_ALG));
        byte[] rawHmac = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(rawHmac);
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record JwtClaims(String username, List<String> roles, long expiresAtEpochSeconds) {
    }
}
