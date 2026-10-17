package com.makinistatama.guvenlik;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security SPA reçetesi: CSRF belirteci ertelenmiştir, yalnız okunduğunda çereze yazılır.
 * Her yanıtta okuyarak {@code XSRF-TOKEN} çerezinin tarayıcıya ulaşmasını garanti eder (401 yanıtı dahil).
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest istek, HttpServletResponse yanit, FilterChain zincir)
            throws ServletException, IOException {
        CsrfToken belirtec = (CsrfToken) istek.getAttribute("_csrf");
        if (belirtec != null) {
            belirtec.getToken();
        }
        zincir.doFilter(istek, yanit);
    }
}
