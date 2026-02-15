package logger;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;

@Configuration
@EnableConfigurationProperties(RequestLoggerProperties.class)
@ConditionalOnProperty(prefix = "request-logger", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RequestLoggerAutoConfiguration {

    private final RequestLoggerProperties properties;

    public RequestLoggerAutoConfiguration(RequestLoggerProperties properties) {
        this.properties = properties;
    }

    @Bean
    public Filter requestLoggingFilter(RequestLoggerProperties properties, ObjectProvider<Tracer> tracerProvider) {
        return new RequestLoggingFilter(properties, tracerProvider);
    }
}