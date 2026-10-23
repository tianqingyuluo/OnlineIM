package icu.tianqingyuluo.onlineim.filter;

import icu.tianqingyuluo.onlineim.service.JwtService;
import icu.tianqingyuluo.onlineim.service.impl.UserDetailsServiceImpl;
import icu.tianqingyuluo.onlineim.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 拦截所有请求，检查是否包含有效的JWT令牌
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final UserDetailsServiceImpl userDetailsService;

    private final JwtUtil jwtUtil;
    private final JwtService jwtService;

    public JwtAuthenticationFilter(UserDetailsServiceImpl userDetailsService, JwtUtil jwtUtil, JwtService jwtService) {
        this.userDetailsService = userDetailsService;
        this.jwtUtil = jwtUtil;
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorizationHeader = request.getHeader("Authorization");
        // <img>/<audio>/<a> 无法带 Authorization，聊天文件允许用查询参数携带同一个 Bearer token
        if ((authorizationHeader == null || !authorizationHeader.startsWith("Bearer "))
                && isChatFileRequest(request)) {
            String queryToken = request.getParameter("access_token");
            if (queryToken != null && !queryToken.isBlank()) {
                authorizationHeader = "Bearer " + queryToken.trim();
            }
        }

        String username = null;
        String jwt = null;

        // 检查请求头中是否包含Bearer令牌
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);

            // 检查黑名单
            if (jwtService.isBlockedToken(authorizationHeader)) {
                log.warn("拦截黑名单中的token：{}", jwt);
                writeUnauthorized(response, "token已经失效");
                return; // 中止请求
            }

            try {
                username = jwtUtil.getUsernameFromToken(authorizationHeader);
            } catch (Exception e) {
                log.warn("收到无效token：{}", jwt);
                writeUnauthorized(response, "无效Token");
                return;
            }
        }

        // 如果找到了用户名且当前没有认证信息
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // 从用户服务加载用户详情
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 如果令牌有效，则设置Spring Security认证信息
            if (jwtUtil.validateToken(authorizationHeader, userDetails)) {
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 设置当前用户为已认证
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }

        // 继续过滤器链
        filterChain.doFilter(request, response);
    }

    static boolean isChatFileRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri != null && uri.contains("/onlineim/chats/");
    }

    /**
     * 直接写出 401。sendError 会转发到 /error，而 /error 对未认证请求会被安全链改写成 403。
     */
    private static void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"401\",\"message\":\"" + message + "\"}");
    }
}
