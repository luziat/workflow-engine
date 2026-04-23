package com.luziatcode.demoworkflowengine.service.workflow.executor.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luziatcode.demoworkflowengine.service.workflow.domain.common.NodeType;
import com.luziatcode.demoworkflowengine.service.workflow.engine.NodeExecutionContext;
import com.luziatcode.demoworkflowengine.service.workflow.executor.NodeExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * 실제 REST 요청을 전송하고 응답을 workflow context 에 기록하는 executor.
 *
 * <p>params 예시:
 * <pre>{@code
 * {
 *   "method": "POST",
 *   "url": "https://api.example.com/orders/<<order.id>>",
 *   "headers": {
 *     "Authorization": "Bearer <<token>>",
 *     "Content-Type": "application/json"
 *   },
 *   "queryParams": {
 *     "tenantId": "<<tenant.id>>"
 *   },
 *   "body": {
 *     "name": "<<customer.name>>"
 *   },
 *   "timeoutMillis": 3000
 * }
 * }</pre>
 */
@Component
public class HttpNodeExecutor implements NodeExecutor {
    private static final String HTTP_OUTPUTS_KEY = "httpOutputs";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public HttpNodeExecutor() {
        this(WebClient.builder().build(), new ObjectMapper());
    }

    public HttpNodeExecutor(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this(webClientBuilder.build(), objectMapper);
    }

    HttpNodeExecutor(WebClient webClient, ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public NodeType getType() {
        return NodeType.HTTP;
    }

    @Override
    public void execute(NodeExecutionContext context) {
        String method = normalizeMethod(context.resolvedParams().get("method"));
        URI uri = buildUri(context.resolvedParams());
        Duration timeout = resolveTimeout(context.resolvedParams().get("timeoutMillis"));
        Map<String, String> headers = normalizeHeaders(context.resolvedParams().get("headers"));

        context.execution().getContext().put("lastHttpUrl", uri.toString());

        Object rawBody = context.resolvedParams().get("body");
        String requestBody = serializeRequestBody(rawBody, headers);

        ResponseEntity<byte[]> response;
        try {
            response = buildRequest(method, uri, headers, requestBody)
                    .timeout(timeout)
                    .block();
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof TimeoutException) {
                throw new IllegalStateException("HTTP request timed out: " + method + " " + uri, exception);
            }
            throw exception;
        } catch (WebClientException exception) {
            throw new IllegalStateException("HTTP request failed: " + method + " " + uri + " (" + exception.getMessage() + ")", exception);
        }

        if (response == null) {
            throw new IllegalStateException("HTTP request failed: " + method + " " + uri + " (empty response)");
        }

        Object responseBody = parseResponseBody(response);
        storeResponse(context, method, uri, response, responseBody);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("HTTP request returned status " + response.getStatusCode().value() + ": " + method + " " + uri);
        }
    }

    private Mono<ResponseEntity<byte[]>> buildRequest(String method, URI uri, Map<String, String> headers, String requestBody) {
        WebClient.RequestBodySpec requestSpec = webClient.method(HttpMethod.valueOf(method))
                .uri(uri)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add));

        if (requestBody == null) {
            return requestSpec.exchangeToMono(response -> response.toEntity(byte[].class));
        }
        return requestSpec.bodyValue(requestBody)
                .exchangeToMono(response -> response.toEntity(byte[].class));
    }

    private String normalizeMethod(Object rawMethod) {
        if (rawMethod == null) {
            return "GET";
        }
        String method = String.valueOf(rawMethod).trim().toUpperCase(Locale.ROOT);
        if (method.isBlank()) {
            throw new IllegalArgumentException("HTTP node method must not be blank");
        }
        return method;
    }

    private URI buildUri(Map<String, Object> params) {
        String rawUrl = requireText(params.get("url"), "HTTP node requires url");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(rawUrl);

        Object rawQueryParams = params.get("queryParams");
        if (rawQueryParams != null && !(rawQueryParams instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("HTTP node queryParams must be an object");
        }
        if (rawQueryParams instanceof Map<?, ?> queryParams) {
            for (Map.Entry<?, ?> entry : queryParams.entrySet()) {
                String key = requireText(entry.getKey(), "HTTP node query param name must not be blank");
                addQueryParam(builder, key, entry.getValue());
            }
        }

        return builder.build(true).toUri();
    }

    private void addQueryParam(UriComponentsBuilder builder, String key, Object value) {
        if (value instanceof List<?> values) {
            for (Object item : values) {
                builder.queryParam(key, item == null ? "" : String.valueOf(item));
            }
            return;
        }
        builder.queryParam(key, value == null ? "" : String.valueOf(value));
    }

    private Duration resolveTimeout(Object rawTimeoutMillis) {
        if (rawTimeoutMillis == null) {
            return DEFAULT_TIMEOUT;
        }

        long timeoutMillis;
        if (rawTimeoutMillis instanceof Number number) {
            timeoutMillis = number.longValue();
        } else {
            try {
                timeoutMillis = Long.parseLong(String.valueOf(rawTimeoutMillis).trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("HTTP node timeoutMillis must be a positive number");
            }
        }

        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("HTTP node timeoutMillis must be a positive number");
        }
        return Duration.ofMillis(timeoutMillis);
    }

    private Map<String, String> normalizeHeaders(Object rawHeaders) {
        if (rawHeaders == null) {
            return new LinkedHashMap<>();
        }
        if (!(rawHeaders instanceof Map<?, ?> headersMap)) {
            throw new IllegalArgumentException("HTTP node headers must be an object");
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : headersMap.entrySet()) {
            String name = requireText(entry.getKey(), "HTTP node header name must not be blank");
            normalized.put(name, requireText(entry.getValue(), "HTTP node header value must not be blank"));
        }
        return normalized;
    }

    private String serializeRequestBody(Object rawBody, Map<String, String> headers) {
        if (rawBody == null) {
            return null;
        }
        if (rawBody instanceof String text) {
            return text;
        }

        try {
            if (headers.keySet().stream().noneMatch(name -> "content-type".equalsIgnoreCase(name))) {
                headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            }
            return objectMapper.writeValueAsString(rawBody);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("HTTP node body must be JSON serializable", exception);
        }
    }

    private Object parseResponseBody(ResponseEntity<byte[]> response) {
        byte[] responseBytes = response.getBody();
        if (responseBytes == null || responseBytes.length == 0) {
            return "";
        }

        String responseText = new String(responseBytes, StandardCharsets.UTF_8);
        MediaType contentType = response.getHeaders().getContentType();
        if (contentType == null || !contentType.toString().toLowerCase(Locale.ROOT).contains("json")) {
            return responseText;
        }

        try {
            return objectMapper.readValue(responseBytes, Object.class);
        } catch (Exception exception) {
            return responseText;
        }
    }

    private void storeResponse(
            NodeExecutionContext context,
            String method,
            URI uri,
            ResponseEntity<byte[]> response,
            Object responseBody
    ) {
        context.execution().getContext().put("httpStatus", response.getStatusCode().value());
        context.execution().getContext().put("httpBody", responseBody);

        Map<String, Object> nodeOutput = new LinkedHashMap<>();
        nodeOutput.put("method", method);
        nodeOutput.put("url", uri.toString());
        nodeOutput.put("status", response.getStatusCode().value());
        nodeOutput.put("headers", normalizeResponseHeaders(response.getHeaders()));
        nodeOutput.put("body", responseBody);

        @SuppressWarnings("unchecked")
        Map<String, Object> httpOutputs = (Map<String, Object>) context.execution()
                .getContext()
                .computeIfAbsent(HTTP_OUTPUTS_KEY, key -> new LinkedHashMap<String, Object>());
        httpOutputs.put(context.node().getId(), nodeOutput);
    }

    private Map<String, Object> normalizeResponseHeaders(HttpHeaders responseHeaders) {
        Map<String, Object> headers = new LinkedHashMap<>();
        responseHeaders.forEach((name, values) -> {
            if (values == null || values.isEmpty()) {
                headers.put(name, "");
            } else if (values.size() == 1) {
                headers.put(name, values.getFirst());
            } else {
                headers.put(name, List.copyOf(values));
            }
        });
        return headers;
    }

    private String requireText(Object value, String errorMessage) {
        if (value == null) {
            throw new IllegalArgumentException(errorMessage);
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return text;
    }
}
