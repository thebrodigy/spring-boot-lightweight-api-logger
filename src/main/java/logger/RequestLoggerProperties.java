package logger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "request-logger")
@Getter
@Setter
public class RequestLoggerProperties {

    private boolean enabled = true;
    private boolean logParams = false;
    private boolean logBody = false;
    private int maxBodySize = 2000;
    private List<String> headers;
    private List<String> maskHeaders;

}