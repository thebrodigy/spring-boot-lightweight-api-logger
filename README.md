# Spring Boot Lightweight API Logger

A lightweight Spring Boot starter that automatically logs:

* HTTP requests and responses
* Request & response bodies (optional)
* Query parameters
* Headers (with masking support)
* Response time
* Micrometer trace IDs (with safe fallback)

Designed to be simple, minimal, and easy to drop into existing Spring Boot applications.

---

## ✨ Features

* ✅ Auto request logging
* ✅ Optional request parameters/body logging
* ✅ Query parameter logging
* ✅ Header masking (Authorization, API keys, etc.)
* ✅ Response time measurement
* ✅ Micrometer tracing integration (uses existing traceId)
* ✅ UUID fallback when tracing is unavailable
* ✅ Spring Boot starter style (minimal setup)

---

## 📦 Installation

### 1. Add dependency

```xml
<dependency>
    <groupId>io.github.brodigy</groupId>
    <artifactId>spring-boot-lightweight-api-logger</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

### 2. Enable the logger

```java
@EnableRequestLogging
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

---

## ⚙️ Configuration

Add to `application.yml`:

```yaml
request-logger:
  enabled: true
  log-body: true
  log-params: true
  max-body-size: 2000
  headers:
    - Authorization
    - X-Api-Key
  mask-headers:
    - Authorization
    - X-Api-Key
```

### Configuration Options

| Property        | Default | Description                      |
|-----------------| ------- |----------------------------------|
| `enabled`       | `true`  | Enable/disable request logging   |
| `log-body`      | `false` | Log request and response bodies  |
| `log-params`    | `false` | Log request parameters           |
| `max-body-size` | `2000`  | Max characters before truncation |
| `headers`       | `[]`    | Headers to add in logs           |
| `mask-headers`  | `[]`    | Headers to mask in logs          |

---

## 🧾 Example Logs

```
POST /api/users/42 | 200 OK | 15ms | traceId=8f4d91ab
Params: verbose=[true]
Request Body: {"name":"Billy"}
Response Body: {"status":"ok"}
```

---

## 🔎 Trace ID Support

The logger automatically uses:

* Micrometer tracing (`Tracer.currentSpan().context().traceId()`)

If tracing is unavailable:

* Falls back to a generated UUID.

No extra setup required.

---

## 🧩 How It Works

The library uses:

* Servlet Filter for request interception
* Custom request/response wrappers for safe body reading
* Spring Boot auto-configuration
* Optional Micrometer tracing integration

---

## 🚨 Notes

* Body logging can impact performance for very large payloads.
* Consider disabling body logging in production if unnecessary.
* Sensitive headers should always be masked.

---

## 🧪 Local Development

Build and install locally:

```bash
mvn clean install
```

Then use it in another Spring Boot service:

```xml
<dependency>
    <groupId>io.github.billylabay</groupId>
    <artifactId>spring-boot-lightweight-api-logger</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🛣️ Roadmap

* [ ] Log filtering by path
* [ ] JSON pretty-print support
* [ ] Async/non-blocking logging
* [ ] Structured JSON logs
* [ ] Header whitelist mode
* [ ] Exclude endpoints configuration

---

## 🤝 Contributing

Issues and pull requests are welcome.

---

## 📄 License

MIT License
