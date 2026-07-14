package com.moneyflowbackend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final Pattern INVITATIONS = Pattern.compile("^/api/workspaces/([^/]+)/invitations$");
    private static final Pattern EXPORT = Pattern.compile("^/api/workspaces/([^/]+)/transactions/export\\.csv$");
    private static final Pattern VOICE_AUDIO = Pattern.compile("^/api/voice-records/([^/]+)/audio$");

    private final RateLimitService rateLimitService;
    private final Clock clock;
    private final boolean trustForwardedFor;
    private final int authPerMinute;
    private final int refreshPerMinute;
    private final int invitationsPerHour;
    private final int voiceUploadsPerHour;
    private final int exportsPerHour;

    public RateLimitFilter(
            RateLimitService rateLimitService,
            Clock clock,
            @Value("${app.security.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor,
            @Value("${app.security.rate-limit.auth-per-minute:10}") int authPerMinute,
            @Value("${app.security.rate-limit.refresh-per-minute:30}") int refreshPerMinute,
            @Value("${app.security.rate-limit.invitations-per-hour:20}") int invitationsPerHour,
            @Value("${app.security.rate-limit.voice-uploads-per-hour:20}") int voiceUploadsPerHour,
            @Value("${app.security.rate-limit.exports-per-hour:10}") int exportsPerHour) {
        this.rateLimitService = rateLimitService;
        this.clock = clock;
        this.trustForwardedFor = trustForwardedFor;
        this.authPerMinute = authPerMinute;
        this.refreshPerMinute = refreshPerMinute;
        this.invitationsPerHour = invitationsPerHour;
        this.voiceUploadsPerHour = voiceUploadsPerHour;
        this.exportsPerHour = exportsPerHour;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            applyLimit(request);
        } catch (RateLimitExceededException ex) {
            writeRateLimited(response, ex.getRetryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void applyLimit(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && ("/api/public/auth/login".equals(path) || "/api/public/auth/register".equals(path))) {
            rateLimitService.check("auth:" + clientIp(request), authPerMinute, Duration.ofMinutes(1));
            return;
        }
        if ("POST".equals(method) && "/api/public/auth/refresh".equals(path)) {
            rateLimitService.check("refresh:" + userOrIp(request), refreshPerMinute, Duration.ofMinutes(1));
            return;
        }
        Matcher invitation = INVITATIONS.matcher(path);
        if ("POST".equals(method) && invitation.matches()) {
            rateLimitService.check("invitation:" + userOrIp(request) + ":" + invitation.group(1), invitationsPerHour, Duration.ofHours(1));
            return;
        }
        if ("POST".equals(method) && VOICE_AUDIO.matcher(path).matches()) {
            rateLimitService.check("voice-upload:" + userOrIp(request), voiceUploadsPerHour, Duration.ofHours(1));
            return;
        }
        Matcher export = EXPORT.matcher(path);
        if ("GET".equals(method) && export.matches()) {
            rateLimitService.check("transaction-export:" + userOrIp(request) + ":" + export.group(1), exportsPerHour, Duration.ofHours(1));
        }
    }

    private String userOrIp(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            try {
                return "user:" + UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
                return "user:authenticated";
            }
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimited(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"success\":false,\"code\":\"RATE_LIMITED\",\"message\":\"Bạn thao tác quá nhanh. Vui lòng thử lại sau.\",\"timestamp\":\""
                + clock.instant() + "\",\"retryAfterSeconds\":" + retryAfterSeconds + ",\"fieldErrors\":{}}");
    }
}
