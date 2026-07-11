package io.taskflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taskflow.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Custom entry point that emits our {@link ApiError} envelope on 401, instead of
 * Spring Security's default HTML page. Same shape as everywhere else in the API —
 * the SPA can branch on {@code body.code} uniformly.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        commenceWithCode(request, response, "UNAUTHORIZED", "Authentication required");
    }

    public void commenceWithCode(HttpServletRequest request, HttpServletResponse response,
                                 String code, String message) throws IOException {
        if (response.isCommitted()) return;
        ApiError body = ApiError.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .code(code)
                .message(message)
                .path(request.getRequestURI())
                .traceId(MDC.get("traceId"))
                .build();
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
