package logger;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final RequestLoggerProperties properties;
    private final ObjectProvider<Tracer> tracerProvider;

    public RequestLoggingFilter(RequestLoggerProperties properties, ObjectProvider<Tracer> tracerProvider) {
        this.properties = properties;
        this.tracerProvider = tracerProvider;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!properties.isEnabled() ||
            !(request instanceof HttpServletRequest req) ||
            !(response instanceof HttpServletResponse res)) {
            chain.doFilter(request, response);
            return;
        }

        // Get traceId from Micrometer tracer (fallback to UUID if missing)
        String traceId; // fallback default

        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer != null) {
            var span = tracer.currentSpan();
            if (span != null) {
                traceId = span.context().traceId();
            } else {
                // fallback if current span or context is null
                traceId = UUID.randomUUID().toString().substring(0, 8);
            }
        } else {
            // fallback if tracer bean is not available
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }

        long startTime = System.currentTimeMillis();

        try {
            chain.doFilter(req, res);
        } finally {
            performLogging(startTime, req, res, traceId);
        }
    }

    private void performLogging(long startTime, HttpServletRequest req, HttpServletResponse res, String traceId) throws IOException {
        long duration = System.currentTimeMillis() - startTime;

        // Read request body if enabled
        String requestBody = "";
        if (properties.isLogBody() &&
                ("POST".equalsIgnoreCase(req.getMethod()) || "PUT".equalsIgnoreCase(req.getMethod()))) {

            byte[] bytes = StreamUtils.copyToByteArray(req.getInputStream());
            req = new CustomHttpServletRequestWrapper(req, bytes); // wrap for controller
            requestBody = new String(bytes, StandardCharsets.UTF_8);

            if (requestBody.length() > properties.getMaxBodySize()) {
                requestBody = requestBody.substring(0, properties.getMaxBodySize()) + "...(truncated)";
            }
        }

        // Mask headers
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if(properties.getHeaders().contains(name)) {
                String value = req.getHeader(name);
                if (properties.getMaskHeaders() != null && properties.getMaskHeaders().contains(name)) {
                    value = "****";
                }
                headers.put(name, value);
            }
        }

        Map<String, String[]> paramMap = req.getParameterMap();
        StringBuilder paramsLog = new StringBuilder();
        if (!paramMap.isEmpty()) {
            paramMap.forEach((key, values) -> {
                paramsLog.append(key).append("=")
                        .append(Arrays.toString(values))
                        .append(" ");
            });
        }

        String logMessage = String.format(
                "%s %s | %d %s | %dms | traceId=%s | Headers: %s",
                req.getMethod(),
                req.getRequestURI(),
                res.getStatus(),
                HttpStatusMessage.getMessage(res.getStatus()),
                duration,
                traceId,
                headers.toString().trim()
        );

        if (properties.isLogBody() && !requestBody.isEmpty()) {
            logMessage += " | Params: " + paramsLog.toString().trim();
        }

        if (properties.isLogBody() && !requestBody.isEmpty()) {
            logMessage += " | Request Body: " + requestBody;
        }

        log.info(logMessage);
    }

    // Optional mapping from status code to string
    private static class HttpStatusMessage {
        private static final Map<Integer, String> STATUS = Map.ofEntries(
                Map.entry(200, "OK"),
                Map.entry(201, "CREATED"),
                Map.entry(204, "NO CONTENT"),
                Map.entry(400, "BAD REQUEST"),
                Map.entry(401, "UNAUTHORIZED"),
                Map.entry(403, "FORBIDDEN"),
                Map.entry(404, "NOT FOUND"),
                Map.entry(500, "INTERNAL SERVER ERROR")
        );

        public static String getMessage(int code) {
            return STATUS.getOrDefault(code, "");
        }
    }
}
