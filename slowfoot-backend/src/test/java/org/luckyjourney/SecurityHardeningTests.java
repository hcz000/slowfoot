package org.luckyjourney;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.hcz.config.JwtProperties;
import org.hcz.config.PasswordConfig;
import org.hcz.entity.user.User;
import org.hcz.util.JwtUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityHardeningTests {

    private static final String SECRET = "test-secret-must-have-at-least-32-bytes";
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUpJwtProperties() {
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret(SECRET);
        jwtProperties.setIssuer("luckyjourney-test");
        jwtProperties.setAccessTokenTtl(Duration.ofMinutes(30));
        jwtProperties.setRefreshTokenTtl(Duration.ofDays(30));
        new JwtUtils(jwtProperties);
    }

    @Test
    void jwtRejectsInvalidIssuerAndReturnsTheAuthenticatedUserId() {
        String token = JwtUtils.getAccessToken(42L, "tester");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", token);

        assertTrue(JwtUtils.checkAccessToken(token));
        assertEquals(42L, JwtUtils.getUserId(request));

        String wrongIssuer = Jwts.builder()
                .issuer("unexpected-issuer")
                .expiration(new Date(System.currentTimeMillis() + Duration.ofMinutes(1).toMillis()))
                .claim("id", "42")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(JwtUtils.checkAccessToken(wrongIssuer));
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String accessToken = JwtUtils.getAccessToken(42L, "tester");
        String refreshToken = JwtUtils.getRefreshToken(42L);

        assertTrue(JwtUtils.checkAccessToken(accessToken));
        assertFalse(JwtUtils.checkRefreshToken(accessToken));
        assertTrue(JwtUtils.checkRefreshToken(refreshToken));
        assertFalse(JwtUtils.checkAccessToken(refreshToken));
        assertEquals(42L, JwtUtils.getRefreshUserId(refreshToken));
    }

    @Test
    void passwordIsHashedAndNeverSerialized() throws Exception {
        PasswordEncoder passwordEncoder = new PasswordConfig().passwordEncoder();
        String encodedPassword = passwordEncoder.encode("plain-password");

        assertNotEquals("plain-password", encodedPassword);
        assertTrue(passwordEncoder.matches("plain-password", encodedPassword));
        assertFalse(passwordEncoder.matches("incorrect-password", encodedPassword));

        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword(encodedPassword);

        String responseBody = new ObjectMapper().writeValueAsString(user);
        assertFalse(responseBody.contains("password"));
        assertFalse(responseBody.contains(encodedPassword));
    }
}
