package xyz.byqy.vane.api.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import jakarta.servlet.http.HttpServletRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import xyz.byqy.vane.common.error.ApiException;
import xyz.byqy.vane.common.error.CommonErrors;

/**
 * 批量端点（ADR-030 P1）：一次提交多个写操作，逐项返回结果。
 *
 * <p>实现方式是「本机回环转发」：按顺序把每项原样转发给同一个服务实例，
 * 复用既有校验、权限、审计与幂等逻辑，避免另写一套。一项失败不影响其余项。</p>
 *
 * <p>限制（防止被当成任意代理）：最多 20 项；方法限 POST/PATCH/PUT/DELETE；
 * 路径必须以 {@code /api/v1/} 开头，且不得指向 {@code /api/v1/agent/} 与 {@code /api/v1/auth/}。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentBatchController {

    private static final int MAX_OPERATIONS = 20;
    private static final Set<String> ALLOWED_METHODS = Set.of("POST", "PATCH", "PUT", "DELETE");
    private static final List<String> BLOCKED_PREFIXES =
            List.of("/api/v1/agent/", "/api/v1/auth/", "/api/v1/public/sms/");

    private final LocalServerPortHolder portHolder;
    private final ObjectMapper mapper;
    private final RestClient client = RestClient.create();

    public AgentBatchController(LocalServerPortHolder portHolder, ObjectMapper mapper) {
        this.portHolder = portHolder;
        this.mapper = mapper;
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BatchResult> batch(@RequestBody BatchRequest request,
                                             @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization,
                                             HttpServletRequest servletRequest) {
        if (request == null || request.operations() == null || request.operations().isEmpty()) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "operations 不能为空");
        }
        if (request.operations().size() > MAX_OPERATIONS) {
            throw new ApiException(CommonErrors.REQUEST_MALFORMED, "单次批量最多 " + MAX_OPERATIONS + " 项");
        }
        List<BatchResult.ItemResult> results = new ArrayList<>(request.operations().size());
        int succeeded = 0;
        String traceId = servletRequest.getHeader("X-Request-Id");
        for (BatchRequest.Item item : request.operations()) {
            BatchResult.ItemResult result = execute(item, authorization, traceId);
            results.add(result);
            if (result.status() >= 200 && result.status() < 300) {
                succeeded++;
            }
        }
        BatchResult body = new BatchResult(results.size(), succeeded, results.size() - succeeded, results);
        // 全部成功用 200，存在失败用 207（Multi-Status），让调用方既能批量提交也能逐项处理
        return ResponseEntity.status(succeeded == results.size() ? 200 : 207).body(body);
    }

    private BatchResult.ItemResult execute(BatchRequest.Item item, String authorization, String traceId) {
        String invalid = validate(item);
        if (invalid != null) {
            // 单项不合法时只让这一项失败，其余照常执行
            String body = mapper.writeValueAsString(Map.of(
                    "code", CommonErrors.REQUEST_MALFORMED.code(),
                    "action", CommonErrors.REQUEST_MALFORMED.action().name(),
                    "detail", invalid));
            return new BatchResult.ItemResult(item == null ? null : item.id(), 400, parse(body), "FIX_PARAMS", false);
        }
        HttpMethod method = HttpMethod.valueOf(item.method().toUpperCase(java.util.Locale.ROOT));
        try {
            return client.method(method)
                    .uri(portHolder.baseUrl() + item.path())
                    .headers(headers -> {
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        if (authorization != null) {
                            headers.set(HttpHeaders.AUTHORIZATION, authorization);
                        }
                        if (traceId != null) {
                            headers.set("X-Request-Id", traceId);
                        }
                        if (item.headers() != null) {
                            item.headers().forEach((name, value) -> {
                                if (!HttpHeaders.AUTHORIZATION.equalsIgnoreCase(name)) {
                                    headers.set(name, value);
                                }
                            });
                        }
                    })
                    .body(item.body())
                    .exchange((request, response) -> {
                        String raw = readBody(response.getBody());
                        return new BatchResult.ItemResult(
                                item.id(),
                                response.getStatusCode().value(),
                                parse(raw),
                                actionOf(raw),
                                "true".equalsIgnoreCase(response.getHeaders().getFirst("Idempotent-Replayed")));
                    });
        } catch (RuntimeException e) {
            String body = mapper.writeValueAsString(Map.of(
                    "code", CommonErrors.SYS_DEPENDENCY_UNAVAILABLE.code(),
                    "action", CommonErrors.SYS_DEPENDENCY_UNAVAILABLE.action().name(),
                    "detail", "批量子请求执行失败：" + e.getClass().getSimpleName()));
            return new BatchResult.ItemResult(item.id(), 502, parse(body), "RETRY", false);
        }
    }

    /** 校验单项，返回 null 表示通过，否则返回失败原因。 */
    private String validate(BatchRequest.Item item) {
        if (item == null || item.path() == null || item.method() == null) {
            return "批量项缺少 method 或 path";
        }
        String method = item.method().toUpperCase(java.util.Locale.ROOT);
        if (!ALLOWED_METHODS.contains(method)) {
            return "批量只支持 POST/PATCH/PUT/DELETE";
        }
        if (!item.path().startsWith("/api/v1/")) {
            return "批量项路径必须以 /api/v1/ 开头";
        }
        for (String blocked : BLOCKED_PREFIXES) {
            if (item.path().startsWith(blocked)) {
                return "批量不支持转发到 " + blocked;
            }
        }
        return null;
    }

    private static String readBody(java.io.InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private Object parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(raw);
            return node == null ? null : mapper.convertValue(node, Object.class);
        } catch (RuntimeException e) {
            return raw;
        }
    }

    private String actionOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(raw);
            return node == null ? null : node.path("action").asString(null);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
