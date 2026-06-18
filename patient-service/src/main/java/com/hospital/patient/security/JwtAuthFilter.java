package com.hospital.patient.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Get Authorization header
        String authHeader = request.getHeader("Authorization");

        String token = null;
        String username = null;

        // 2. Check header format: "Bearer <token>"
        if (authHeader != null
                && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // remove "Bearer "
            try {
                username = jwtUtil.extractUsername(token);
            } catch (Exception e) {
                // Invalid token — log and continue
                // SecurityContext stays empty → 401 returned
                logger.warn("JWT token invalid: "
                        + e.getMessage());
            }
        }

        // 3. If valid token and not already authenticated
        if (username != null
                && SecurityContextHolder.getContext()
                .getAuthentication() == null) {

            if (jwtUtil.validateToken(token, username)) {
                // 4. Create authentication object
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                new ArrayList<>() // no roles yet
                        );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource()
                                .buildDetails(request));

                // 5. Set in SecurityContext
                // From this point, request is "authenticated"
                SecurityContextHolder.getContext()
                        .setAuthentication(authToken);
            }
        }

        // 6. Continue to next filter or controller
        filterChain.doFilter(request, response);
    }
}
