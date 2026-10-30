package xyz.byqy.vane.api.agent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import xyz.byqy.vane.common.error.ErrorCode;
import xyz.byqy.vane.common.error.ErrorCodeRegistry;
import xyz.byqy.vane.iam.domain.PermissionCatalog;

/**
 * 能力清单构建器（ADR-030 能力层）。
 *
 * <p>清单完全由运行时元数据生成：操作与路径来自 Spring 的请求映射，权限码来自
 * {@link PermissionCatalog} 与方法上的 {@code @PreAuthorize}，错误码目录来自
 * {@link ErrorCodeRegistry}。不手工维护，避免与实现漂移。</p>
 */
@Component
public class CapabilityManifestBuilder {

    private static final Pattern CATALOG_CONSTANT =
            Pattern.compile("PermissionCatalog\\)\\.([A-Z0-9_]+)\\.code");

    /** 触发动作而非创建资源的路径后缀。 */
    private static final List<String> ACTION_SUFFIXES = List.of(
            "/publish", "/unpublish", "/run", "/cancel", "/pause", "/start",
            "/test", "/verify", "/send", "/import", "/logout", "/reset-password", "/bind", "/disable");

    private final RequestMappingHandlerMapping handlerMapping;
    private final Map<String, String> permissionCodeByConstant;

    public CapabilityManifestBuilder(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
        Map<String, String> codes = new TreeMap<>();
        for (PermissionCatalog value : PermissionCatalog.values()) {
            codes.put(value.name(), value.getCode());
        }
        this.permissionCodeByConstant = Map.copyOf(codes);
    }

    public CapabilityManifest build() {
        List<CapabilityManifest.Operation> operations = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod handler = entry.getValue();
            List<String> permissions = permissionsOf(handler);
            info.getPathPatternsCondition().getPatterns().forEach(pattern -> {
                String path = pattern.getPatternString();
                info.getMethodsCondition().getMethods().forEach(method -> {
                    String httpMethod = method.name();
                    operations.add(new CapabilityManifest.Operation(
                            httpMethod,
                            path,
                            permissions.isEmpty() ? null : permissions.get(0),
                            List.copyOf(permissions),
                            sideEffect(httpMethod, path),
                            idempotent(httpMethod),
                            openApiRef(path, httpMethod)));
                });
            });
        }
        operations.sort(Comparator.comparing(CapabilityManifest.Operation::path)
                .thenComparing(CapabilityManifest.Operation::method));

        List<CapabilityManifest.ErrorEntry> errors = ErrorCodeRegistry.all().stream()
                .sorted(Comparator.comparing(ErrorCode::code))
                .map(code -> new CapabilityManifest.ErrorEntry(
                        code.code(),
                        code.status().value(),
                        code.type().uri(),
                        code.action().name(),
                        code.retryable()))
                .toList();

        return new CapabilityManifest("vane", List.copyOf(operations), errors);
    }

    /** 从方法或类上的 {@code @PreAuthorize} 解析所需权限码。 */
    private List<String> permissionsOf(HandlerMethod handler) {
        PreAuthorize annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), PreAuthorize.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), PreAuthorize.class);
        }
        if (annotation == null) {
            return List.of();
        }
        List<String> codes = new ArrayList<>();
        Matcher matcher = CATALOG_CONSTANT.matcher(annotation.value());
        while (matcher.find()) {
            String code = permissionCodeByConstant.get(matcher.group(1));
            if (code != null && !codes.contains(code)) {
                codes.add(code);
            }
        }
        codes.sort(String::compareTo);
        return codes;
    }

    /** 按方法与路径推导副作用类型。 */
    private String sideEffect(String method, String path) {
        return switch (method) {
            case "GET", "HEAD", "OPTIONS" -> "read";
            case "DELETE" -> "delete";
            case "PATCH", "PUT" -> "update";
            case "POST" -> ACTION_SUFFIXES.stream().anyMatch(path::endsWith) ? "action" : "create";
            default -> "read";
        };
    }

    /** 幂等性：写操作当前依赖 {@code If-Match} 乐观锁；创建类待幂等键落地（ADR-030 P0-4）。 */
    private boolean idempotent(String method) {
        return !"POST".equals(method);
    }

    /** 对应 OpenAPI 文档中的锚点。 */
    private String openApiRef(String path, String method) {
        return "#/paths/" + path.replace("/", "~1") + "/" + method.toLowerCase(java.util.Locale.ROOT);
    }
}
