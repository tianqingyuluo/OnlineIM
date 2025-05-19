package icu.tianqingyuluo.onlineim.util;

import icu.tianqingyuluo.onlineim.pojo.entity.UserIDProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT工具类，用于生成和验证JWT令牌
 */
@Slf4j
@Component
public class JwtUtil {

    // 默认密钥，建议在生产环境中使用更安全的方式存储
    private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    
    // 令牌有效期（毫秒）
    private static final long JWT_TOKEN_VALIDITY = 24 * 60 * 60 * 1000; // 24小时

    public static String GET_EXPIRE_TIME() {
        return JWT_TOKEN_VALIDITY + "";
    }

    /**
     * 从令牌中获取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    /**
     * 从令牌中获取用户ID
     * @param token JWT令牌
     * @return userid
     */
    public String getUserIDFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("userid", String.class));
    }

    public String getDeviceIDFromToken(String token) {
        return getClaimFromToken(token, claims -> claims.get("deviceid", String.class));
    }

    /**
     * 从令牌中获取过期日期
     */
    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    /**
     * 从令牌中获取指定的声明
     */
    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    /**
     * 解析令牌获取所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token.substring(7))
                .getBody();
    }

    /**
     * 检查令牌是否已过期
     */
    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    /**
     * 为指定用户生成令牌
     */
    public String generateToken(UserDetails userDetails, String deviceId) {
        Map<String, Object> claims = new HashMap<>();
        if (userDetails instanceof UserIDProvider) {
            claims.put("userid", ((UserIDProvider)userDetails).getID());
            claims.put("deviceid", deviceId);
        }
        else log.error("UserDetails 未实现 UserIdProvider 接口, userId claim 将不会被添加");
        return doGenerateToken(claims, userDetails.getUsername());
    }

    /**
     * 生成令牌的核心方法
     */
    private String doGenerateToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY))
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * 验证令牌
     */
    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public long getRemainingValidityTime(String token) {
        return getExpirationDateFromToken(token).getTime();
    }
}