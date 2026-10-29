package org.hcz.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.hcz.common.config.JwtProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;


// JWT 签发和校验工具。Access Token 与 Refresh Token 使用同一签名密钥，但用途严格区分。

@Component
public class JwtUtils {

    public static final String TOKEN_HEADER = "token";
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static volatile JwtProperties jwtProperties;

    public JwtUtils(JwtProperties jwtProperties) {
        JwtUtils.jwtProperties = jwtProperties;
    }

    public static String getAccessToken(Long id, String nickname) {
        return buildToken(id, nickname, ACCESS_TOKEN_TYPE, properties().getAccessTokenTtl());
    }

    public static String getRefreshToken(Long id) {
        return buildToken(id, null, REFRESH_TOKEN_TYPE, properties().getRefreshTokenTtl());
    }

    private static String buildToken(Long id, String nickname, String tokenType, Duration ttl) {
        var builder = Jwts.builder()
                .subject("luckyjourney-user")
                .issuer(properties().getIssuer())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttl.toMillis()))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .claim("id", String.valueOf(id));
        if (StringUtils.hasText(nickname)) {
            builder.claim("nickname", nickname);
        }
        return builder.signWith(signingKey()).compact();
    }

    public static boolean checkAccessToken(String jwtToken) {
        return parseToken(jwtToken, ACCESS_TOKEN_TYPE) != null;
    }

    public static boolean checkAccessToken(HttpServletRequest request) {
        return request != null && checkAccessToken(resolveToken(request));
    }

    // 浏览器发起的视频/图片资源请求（<video src>/<img src>）无法携带自定义请求头，
    // 因此在请求头缺失时回退到查询参数 token，保证媒体播放地址仍能通过鉴权。
    private static String resolveToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER);
        if (!StringUtils.hasText(token)) {
            token = request.getParameter(TOKEN_HEADER);
        }
        return token;
    }

    public static boolean checkRefreshToken(String refreshToken) {
        return parseToken(refreshToken, REFRESH_TOKEN_TYPE) != null;
    }

    public static Long getUserId(HttpServletRequest request) {
        if (request == null) return null;
        return getUserId(resolveToken(request), ACCESS_TOKEN_TYPE);
    }

    public static Long getRefreshUserId(String refreshToken) {
        return getUserId(refreshToken, REFRESH_TOKEN_TYPE);
    }

    private static Long getUserId(String token, String expectedType) {
        Claims claims = parseToken(token, expectedType);
        if (claims == null) return null;
        try {
            return Long.valueOf(String.valueOf(claims.get("id")));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Claims parseToken(String token, String expectedType) {
        if (!StringUtils.hasText(token)) return null;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey())
                    .requireIssuer(properties().getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return expectedType.equals(claims.get(TOKEN_TYPE_CLAIM, String.class)) ? claims : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static SecretKey signingKey() {
        return Keys.hmacShaKeyFor(properties().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private static JwtProperties properties() {
        if (jwtProperties == null) {
            throw new IllegalStateException("JWT properties have not been initialized");
        }
        return jwtProperties;
    }
}
