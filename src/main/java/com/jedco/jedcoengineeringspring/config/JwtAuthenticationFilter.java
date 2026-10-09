package com.jedco.jedcoengineeringspring.config;

import com.jedco.jedcoengineeringspring.services.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;
        if (StringUtils.isEmpty(authHeader)
                || (!StringUtils.startsWith(authHeader, "Bearer ") && !authHeader.equals("Bearer"))) {
            filterChain.doFilter(request, response);
            return;
        }
        jwt = authHeader.equals("Bearer") ? "" : authHeader.substring(7);
        if (StringUtils.isBlank(jwt)) {
            rejectToken(response, false);
            return;
        }
        try {
            username = jwtService.extractUserName(jwt);
            if (StringUtils.isEmpty(username)) {
                rejectToken(response, false);
                return;
            }
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!jwtService.isTokenValid(jwt, userDetails)) {
                    rejectToken(response, jwtService.isTokenExpired(jwt));
                    return;
                }
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                context.setAuthentication(authToken);
                SecurityContextHolder.setContext(context);
            }
        } catch (ExpiredJwtException ex) {
            rejectToken(response, true);
            return;
        } catch (JwtException ex) {
            rejectToken(response, false);
            return;
        }
        // Controller exceptions must remain outside the token-authentication catch block.
        filterChain.doFilter(request, response);

    }

    private void rejectToken(HttpServletResponse response, boolean expired) throws IOException {
        SecurityContextHolder.clearContext();
        log.debug("Bearer JWT rejected: {}.", expired ? "expired" : "invalid");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        String message = expired ? "Authentication token has expired." : "Invalid authentication token.";
        response.getWriter().write("{\"status\":401,\"title\":\"Unauthorized\",\"message\":\"" + message + "\"}");
    }
}
