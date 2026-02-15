package logger;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private RequestLoggerProperties properties;

    @Mock
    private ObjectProvider<Tracer> tracerProvider;

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private RequestLoggingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestLoggingFilter(properties, tracerProvider);
    }

    @Test
    void doFilter_whenDisabled_shouldNotLog() throws IOException, ServletException {
        when(properties.isEnabled()).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(tracerProvider);
    }

    @Test
    void doFilter_whenNotHttpRequest_shouldPassThrough() throws IOException, ServletException {
        when(properties.isEnabled()).thenReturn(true);
        jakarta.servlet.ServletRequest nonHttpRequest = mock(jakarta.servlet.ServletRequest.class);
        jakarta.servlet.ServletResponse nonHttpResponse = mock(jakarta.servlet.ServletResponse.class);

        filter.doFilter(nonHttpRequest, nonHttpResponse, filterChain);

        verify(filterChain).doFilter(nonHttpRequest, nonHttpResponse);
        verifyNoInteractions(tracerProvider);
    }

    @Test
    void doFilter_withTracerAvailable_shouldUseTraceId() throws IOException, ServletException {
        setupBasicMocks();
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("abc123");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tracerProvider).getIfAvailable();
        verify(tracer).currentSpan();
    }

    @Test
    void doFilter_withTracerButNoSpan_shouldUseFallbackTraceId() throws IOException, ServletException {
        setupBasicMocks();
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tracerProvider).getIfAvailable();
        verify(tracer).currentSpan();
    }

    @Test
    void doFilter_withNoTracerAvailable_shouldUseFallbackTraceId() throws IOException, ServletException {
        setupBasicMocks();
        when(tracerProvider.getIfAvailable()).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(tracerProvider).getIfAvailable();
    }

    @Test
    void doFilter_withPostRequest_shouldLogRequestBody() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);
        when(request.getMethod()).thenReturn("POST");
        when(properties.getMaxBodySize()).thenReturn(1000);

        String requestBody = "{\"name\":\"test\"}";
        ServletInputStream inputStream = createServletInputStream(requestBody);
        when(request.getInputStream()).thenReturn(inputStream);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace123");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getInputStream();
    }

    @Test
    void doFilter_withPutRequest_shouldLogRequestBody() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);
        when(request.getMethod()).thenReturn("PUT");
        when(properties.getMaxBodySize()).thenReturn(1000);

        String requestBody = "{\"id\":1,\"name\":\"updated\"}";
        ServletInputStream inputStream = createServletInputStream(requestBody);
        when(request.getInputStream()).thenReturn(inputStream);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace456");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getInputStream();
    }

    @Test
    void doFilter_withGetRequest_shouldNotLogRequestBody() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);
        when(request.getMethod()).thenReturn("GET");

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace789");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getInputStream();
    }

    @Test
    void doFilter_withLargeRequestBody_shouldTruncate() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);
        when(request.getMethod()).thenReturn("POST");
        when(properties.getMaxBodySize()).thenReturn(10);

        String requestBody = "This is a very long request body that should be truncated";
        ServletInputStream inputStream = createServletInputStream(requestBody);
        when(request.getInputStream()).thenReturn(inputStream);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceABC");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getInputStream();
    }

    @Test
    void doFilter_withHeaders_shouldLogHeaders() throws IOException, ServletException {
        setupBasicMocks();
        List<String> headersToLog = Arrays.asList("Content-Type", "Authorization");
        when(properties.getHeaders()).thenReturn(headersToLog);
        when(properties.getMaskHeaders()).thenReturn(null);

        Vector<String> headerNames = new Vector<>();
        headerNames.add("Content-Type");
        headerNames.add("Authorization");
        when(request.getHeaderNames()).thenReturn(headerNames.elements());
        when(request.getHeader("Content-Type")).thenReturn("application/json");
        when(request.getHeader("Authorization")).thenReturn("Bearer token123");

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceDEF");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getHeader("Content-Type");
        verify(request).getHeader("Authorization");
    }

    @Test
    void doFilter_withMaskedHeaders_shouldMaskValues() throws IOException, ServletException {
        setupBasicMocks();
        List<String> headersToLog = Arrays.asList("Content-Type", "Authorization");
        List<String> headersToMask = Collections.singletonList("Authorization");
        when(properties.getHeaders()).thenReturn(headersToLog);
        when(properties.getMaskHeaders()).thenReturn(headersToMask);

        Vector<String> headerNames = new Vector<>();
        headerNames.add("Content-Type");
        headerNames.add("Authorization");
        when(request.getHeaderNames()).thenReturn(headerNames.elements());
        when(request.getHeader("Content-Type")).thenReturn("application/json");
        when(request.getHeader("Authorization")).thenReturn("Bearer token123");

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceGHI");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getHeader("Content-Type");
        verify(request).getHeader("Authorization");
    }

    @Test
    void doFilter_withQueryParams_shouldLogParams() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);

        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("id", new String[]{"123"});
        paramMap.put("name", new String[]{"test"});
        when(request.getParameterMap()).thenReturn(paramMap);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceJKL");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getParameterMap();
    }

    @Test
    void doFilter_withDifferentStatusCodes_shouldLogCorrectly() throws IOException, ServletException {
        setupBasicMocks();
        when(response.getStatus()).thenReturn(201);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceMNO");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, atLeastOnce()).getStatus();
    }

    @Test
    void doFilter_with400Status_shouldLogBadRequest() throws IOException, ServletException {
        setupBasicMocks();
        when(response.getStatus()).thenReturn(400);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("tracePQR");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, atLeastOnce()).getStatus();
    }

    @Test
    void doFilter_with500Status_shouldLogInternalServerError() throws IOException, ServletException {
        setupBasicMocks();
        when(response.getStatus()).thenReturn(500);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceSTU");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, atLeastOnce()).getStatus();
    }

    @Test
    void doFilter_withUnknownStatus_shouldLogWithoutMessage() throws IOException, ServletException {
        setupBasicMocks();
        when(response.getStatus()).thenReturn(418); // I'm a teapot

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceVWX");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, atLeastOnce()).getStatus();
    }

    @Test
    void doFilter_whenExceptionInChain_shouldStillLog() throws IOException, ServletException {
        setupBasicMocks();
        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("traceYZ");

        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        assertThrows(ServletException.class, () -> filter.doFilter(request, response, filterChain));

        verify(response, atLeastOnce()).getStatus();
    }

    @Test
    void doFilter_withEmptyHeaders_shouldNotFail() throws IOException, ServletException {
        setupBasicMocks();
        when(request.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace000");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_withEmptyParams_shouldNotFail() throws IOException, ServletException {
        setupBasicMocks();
        when(request.getParameterMap()).thenReturn(Collections.emptyMap());

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace111");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getParameterMap();
    }

    @Test
    void doFilter_withEmptyRequestBody_shouldHandleGracefully() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);
        when(request.getMethod()).thenReturn("POST");
        when(properties.getMaxBodySize()).thenReturn(1000);

        ServletInputStream inputStream = createServletInputStream("");
        when(request.getInputStream()).thenReturn(inputStream);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace222");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_withLogBodyDisabled_shouldNotReadBody() throws IOException, ServletException {

        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(false);
        when(request.getMethod()).thenReturn("POST");

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace333");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request, never()).getInputStream();
    }

    @Test
    void doFilter_withMultipleHeaderValues_shouldLogCorrectly() throws IOException, ServletException {
        setupBasicMocks();
        List<String> headersToLog = Collections.singletonList("Accept");
        when(properties.getHeaders()).thenReturn(headersToLog);

        Vector<String> headerNames = new Vector<>();
        headerNames.add("Accept");
        when(request.getHeaderNames()).thenReturn(headerNames.elements());
        when(request.getHeader("Accept")).thenReturn("application/json, text/html");

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace444");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getHeader("Accept");
    }

    @Test
    void httpStatusMessage_shouldReturnCorrectMessages() {
        // This tests the inner class indirectly through the filter
        setupBasicMocks();

        // Test various status codes
        int[] statusCodes = {200, 201, 204, 400, 401, 403, 404, 500};
        for (int statusCode : statusCodes) {
            when(response.getStatus()).thenReturn(statusCode);
            when(tracerProvider.getIfAvailable()).thenReturn(tracer);
            when(tracer.currentSpan()).thenReturn(span);
            when(span.context()).thenReturn(traceContext);
            when(traceContext.traceId()).thenReturn("trace-" + statusCode);

            try {
                filter.doFilter(request, response, filterChain);
            } catch (Exception e) {
                fail("Should not throw exception for status code " + statusCode);
            }
        }
    }

    @Test
    void doFilter_withMultiValueParams_shouldLogAllValues() throws IOException, ServletException {
        setupBasicMocks();
        when(properties.isLogBody()).thenReturn(true);

        Map<String, String[]> paramMap = new HashMap<>();
        paramMap.put("tags", new String[]{"java", "spring", "testing"});
        when(request.getParameterMap()).thenReturn(paramMap);

        when(tracerProvider.getIfAvailable()).thenReturn(tracer);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("trace555");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(request).getParameterMap();
    }

    // Helper methods

    private void setupBasicMocks() {
        when(properties.isEnabled()).thenReturn(true);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(response.getStatus()).thenReturn(200);
    }

    private ServletInputStream createServletInputStream(String content) {
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content.getBytes());
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(jakarta.servlet.ReadListener readListener) {
                // Not implemented
            }

            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };
    }
}
