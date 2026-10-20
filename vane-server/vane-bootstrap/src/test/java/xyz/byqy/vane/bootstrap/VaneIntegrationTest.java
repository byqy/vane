package xyz.byqy.vane.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 集成测试：真实 PostgreSQL（Testcontainers）+ 真实迁移脚本 + 真实 HTTP 契约。
 *
 * <p>令牌与挑战存内存，避免额外启动 Valkey；Flyway 建库与「初始管理员引导」本身也是被测对象。
 * 引导密码通过配置固定以便断言；未配置时引导生成随机密码并在启动日志输出一次。</p>
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VaneIntegrationTest {

    private static final String ADMIN = "admin";
    private static final String ADMIN_PASSWORD = "Integration@12345";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Container
    static GenericContainer<?> valkey = new GenericContainer<>(DockerImageName.parse("valkey/valkey:9"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("vane.auth.token-store", () -> "memory");
        registry.add("spring.data.redis.host", valkey::getHost);
        registry.add("spring.data.redis.port", () -> valkey.getMappedPort(6379));
        // 仅测试用的 32 字节零值主密钥
        registry.add("vane.auth.mfa.secret-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        registry.add("vane.file.local-root", () -> System.getProperty("java.io.tmpdir") + "/vane-it-files");
        registry.add("vane.iam.bootstrap-admin.password", () -> ADMIN_PASSWORD);
        // 测试用短信配置密钥环：版本 1，32 字节零值密钥
        registry.add("vane.sms.secret-keys", () -> "{\"1\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=\"}");
        registry.add("vane.sms.active-key-version", () -> "1");
        registry.add("vane.sms.campaign.window-enabled", () -> "false");
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Autowired
    JdbcTemplate jdbc;

    @LocalServerPort
    int port;

    // ---------- 迁移与种子 ----------

    @Test
    void migrationsRunAndAllSucceed() {
        Integer failed = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = false", Integer.class);
        String latest = jdbc.queryForObject(
                "select version from flyway_schema_history order by installed_rank desc limit 1", String.class);

        assertThat(failed).isZero();
        assertThat(latest).isEqualTo("14");
    }

    @Test
    void seedDataMatchesDocs() {
        assertThat(count("iam_role where builtin")).isEqualTo(4);
        assertThat(xyz.byqy.vane.iam.domain.PermissionCatalog.all()).hasSize(82);
        assertThat(count("iam_role_permission ur join iam_role r on r.id = ur.role_id where r.builtin"))
                .isEqualTo(200);
        assertThat(count("sys_config")).isEqualTo(18);
        assertThat(count("sys_dict_type where deleted_at is null")).isEqualTo(6);
        assertThat(count("sys_message_template where channel = 'EMAIL'")).isEqualTo(2);
    }

    @Test
    void bootstrapCreatesInitialAdminAndRootDept() {
        Integer admins = jdbc.queryForObject(
                "select count(*) from iam_user where username = ? and deleted_at is null", Integer.class, ADMIN);
        Boolean mustChange = jdbc.queryForObject(
                "select must_change_password from iam_user where username = ?", Boolean.class, ADMIN);
        Integer rootDepts = jdbc.queryForObject(
                "select count(*) from iam_dept where code = 'HQ' and deleted_at is null", Integer.class);

        assertThat(admins).isEqualTo(1);
        assertThat(mustChange).isTrue();
        assertThat(rootDepts).isEqualTo(1);
    }

    // ---------- 认证 ----------

    @Test
    void loginReturnsAccessTokenAndMeWorks() throws Exception {
        HttpResponse<String> login = post("/api/v1/auth/login",
                Map.of("username", ADMIN, "password", ADMIN_PASSWORD), null);

        assertThat(login.statusCode()).withFailMessage("login body: %s", login.body()).isEqualTo(200);
        JsonNode body = JSON.readTree(login.body());
        String accessToken = body.path("accessToken").asString();
        assertThat(accessToken).isNotBlank();
        assertThat(login.headers().firstValue(HttpHeaders.SET_COOKIE).orElse(""))
                .contains("__Host-REFRESH");

        HttpResponse<String> me = get("/api/v1/auth/me", accessToken);
        assertThat(me.statusCode()).withFailMessage("me body: %s", me.body()).isEqualTo(200);
        JsonNode profile = JSON.readTree(me.body());
        assertThat(profile.path("username").asString()).isEqualTo(ADMIN);
        assertThat(profile.path("permissions").size()).isGreaterThan(50);
        assertThat(profile.path("menus").size()).isGreaterThan(10);
    }

    @Test
    void unauthenticatedAdminRequestIsRejectedWithProblemDetails() throws Exception {
        HttpResponse<String> response = get("/api/v1/admin/users", null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("application/problem+json");
        JsonNode problem = JSON.readTree(response.body());
        assertThat(problem.path("code").asString()).startsWith("AUTH.");
        assertThat(problem.path("type").asString()).startsWith("/problems/");
        assertThat(problem.path("traceId").asString()).isNotBlank();
        assertThat(response.headers().firstValue("X-Request-Id").orElse(""))
                .isEqualTo(problem.path("traceId").asString());
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingUserExistence() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/login",
                Map.of("username", ADMIN, "password", "definitely-wrong"), null);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(response.body()).path("code").asString()).isEqualTo("AUTH.LOGIN.FAILED");
    }

    // ---------- 契约 ----------

    @Test
    void paginationSizeIsCappedAt100() throws Exception {
        String accessToken = accessToken();
        HttpResponse<String> response = get("/api/v1/admin/users?page%5Bsize%5D=500", accessToken);

        assertThat(response.statusCode()).withFailMessage("body: %s", response.body()).isEqualTo(200);
        JsonNode page = JSON.readTree(response.body()).path("page");
        assertThat(page.path("size").asInt()).isEqualTo(100);
    }

    @Test
    void publicEndpointNeedsNoToken() throws Exception {
        HttpResponse<String> response = get("/api/v1/public/notices", null);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body()).has("items")).isTrue();
    }

    @Test
    void userWithoutRoleGets403() throws Exception {
        String adminToken = accessToken();
        String deptId = JSON.readTree(get("/api/v1/auth/me", adminToken).body()).path("deptId").asString();
        String username = "no-role-" + UUID.randomUUID().toString().substring(0, 8);

        HttpResponse<String> created = post("/api/v1/admin/users", Map.of(
                "username", username,
                "password", ADMIN_PASSWORD,
                "nickname", "No Role",
                "deptId", deptId
        ), adminToken);
        assertThat(created.statusCode()).withFailMessage("create body: %s", created.body()).isEqualTo(201);

        String viewerToken = JSON.readTree(post("/api/v1/auth/login",
                Map.of("username", username, "password", ADMIN_PASSWORD), null).body())
                .path("accessToken").asString();

        HttpResponse<String> response = get("/api/v1/admin/users", viewerToken);

        assertThat(response.statusCode()).withFailMessage("body: %s", response.body()).isEqualTo(403);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("application/problem+json");
    }

    // ---------- 管理端业务流程 ----------

    @Test
    void noticeLifecycle() throws Exception {
        String token = accessToken();
        HttpResponse<String> created = post("/api/v1/admin/notices", Map.of(
                "level", "WARN",
                "pinned", true,
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "title", "系统维护", "body", "今晚 22:00"),
                        Map.of("locale", "en", "title", "Maintenance", "body", "Tonight 22:00")
                )), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String noticeId = JSON.readTree(created.body()).path("id").asString();

        assertThat(post("/api/v1/admin/notices/" + noticeId + "/publish", Map.of(), token).statusCode())
                .isEqualTo(200);

        HttpResponse<String> publicZh = get("/api/v1/public/notices", null);
        HttpResponse<String> publicEn = getWithLanguage("/api/v1/public/notices", null, "en");
        assertThat(publicZh.statusCode()).isEqualTo(200);
        assertThat(publicZh.body()).contains("系统维护");
        assertThat(publicEn.body()).contains("Maintenance");

        assertThat(delete("/api/v1/admin/notices/" + noticeId, token).statusCode()).isEqualTo(204);
    }

    @Test
    void pageLifecycleAndSlugConflict() throws Exception {
        String token = accessToken();
        String slug = "about-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> body = Map.of(
                "slug", slug,
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "title", "关于我们", "summary", "简介", "body", "<p>正文</p>"),
                        Map.of("locale", "en", "title", "About us", "body", "<p>Body</p>")
                ));

        HttpResponse<String> created = post("/api/v1/admin/pages", body, token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String pageId = JSON.readTree(created.body()).path("id").asString();

        // 未发布时门户不可见
        assertThat(get("/api/v1/public/pages/" + slug, null).statusCode()).isEqualTo(404);

        assertThat(post("/api/v1/admin/pages/" + pageId + "/publish", Map.of(), token).statusCode())
                .isEqualTo(200);
        HttpResponse<String> publicPage = get("/api/v1/public/pages/" + slug, null);
        assertThat(publicPage.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(publicPage.body()).path("title").asString()).isEqualTo("关于我们");

        // 重复 slug → 409
        assertThat(post("/api/v1/admin/pages", body, token).statusCode()).isEqualTo(409);
    }

    @Test
    void navAndBannerArePubliclyVisible() throws Exception {
        String token = accessToken();
        HttpResponse<String> nav = post("/api/v1/admin/navs", Map.of(
                "position", "HEADER", "labelKey", "nav.test", "target", "/test", "sortNo", 99), token);
        assertThat(nav.statusCode()).withFailMessage("body: %s", nav.body()).isEqualTo(201);

        HttpResponse<String> navs = get("/api/v1/public/navs?position=HEADER", null);
        assertThat(navs.statusCode()).isEqualTo(200);
        assertThat(navs.body()).contains("nav.test");

        String fileId = uploadPublicFile(token);
        HttpResponse<String> banner = post("/api/v1/admin/banners", Map.of(
                "position", "home.top", "imageFileId", fileId, "titleKey", "banner.test", "sortNo", 99), token);
        assertThat(banner.statusCode()).withFailMessage("body: %s", banner.body()).isEqualTo(201);

        HttpResponse<String> banners = get("/api/v1/public/banners?position=home.top", null);
        assertThat(banners.statusCode()).isEqualTo(200);
        assertThat(banners.body()).contains("banner.test");
    }

    @Test
    void fileUploadListAndDelete() throws Exception {
        String token = accessToken();
        String fileId = uploadPublicFile(token);

        assertEqualsStatus(get("/api/v1/public/files/" + fileId, null), 200);
        HttpResponse<String> list = get("/api/v1/admin/files", token);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(fileId);

        assertThat(delete("/api/v1/admin/files/" + fileId, token).statusCode()).isEqualTo(204);
        HttpResponse<String> afterDelete = get("/api/v1/admin/files", token);
        assertThat(afterDelete.statusCode()).isEqualTo(200);
        assertThat(afterDelete.body()).doesNotContain(fileId);
    }

    @Test
    void configUpdateAndReadonlyGuard() throws Exception {
        String token = accessToken();
        HttpResponse<String> list = get("/api/v1/admin/configs", token);
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(list.body()).size()).isEqualTo(18);

        assertThat(patch("/api/v1/admin/configs/security.password.min-length",
                Map.of("valueJson", "8"), token).statusCode()).isEqualTo(200);

        // 只读参数拒绝修改
        assertThat(patch("/api/v1/admin/configs/file.svg.sanitize",
                Map.of("valueJson", "false"), token).statusCode()).isEqualTo(409);
    }

    @Test
    void dictionaryLabelsFollowAcceptLanguage() throws Exception {
        String token = accessToken();
        assertThat(get("/api/v1/admin/dicts", token).statusCode()).isEqualTo(200);

        HttpResponse<String> zh = get("/api/v1/admin/dicts/common_status/items", token);
        HttpResponse<String> en = getWithLanguage("/api/v1/admin/dicts/common_status/items", token, "en");

        assertThat(zh.statusCode()).isEqualTo(200);
        assertThat(en.statusCode()).isEqualTo(200);
        assertThat(en.body()).contains("Active");
        assertThat(zh.body()).isNotEqualTo(en.body());
    }

    @Test
    void roleCreationAndPermissionAssignment() throws Exception {
        String token = accessToken();
        String code = "IT_ROLE_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        HttpResponse<String> created = post("/api/v1/admin/roles", Map.of(
                "code", code, "name", "Integration Role", "dataScope", "CUSTOM"), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String roleId = JSON.readTree(created.body()).path("id").asString();

        String permissionCode = "system:user";

        HttpResponse<String> assigned = put("/api/v1/admin/roles/" + roleId + "/permissions",
                Map.of("permissionCodes", java.util.List.of(permissionCode)), token);
        assertThat(assigned.statusCode()).withFailMessage("body: %s", assigned.body()).isIn(200, 204);

        // 自定义数据范围：分配组织后可读回
        String deptId = jdbc.queryForObject("select id from iam_dept where code = 'HQ'",
                (rs, rowNum) -> rs.getObject("id", UUID.class)).toString();
        assertThat(put("/api/v1/admin/roles/" + roleId + "/depts",
                Map.of("deptIds", java.util.List.of(deptId)), token).statusCode()).isEqualTo(204);
        HttpResponse<String> roleDepts = get("/api/v1/admin/roles/" + roleId + "/depts", token);
        assertThat(roleDepts.statusCode()).isEqualTo(200);
        assertThat(roleDepts.body()).contains(deptId);

        assertThat(delete("/api/v1/admin/roles/" + roleId, token).statusCode()).isIn(204);
    }

    @Test
    void deptTreeAndPermissionTree() throws Exception {
        String token = accessToken();
        assertThat(get("/api/v1/admin/depts/tree", token).statusCode()).isEqualTo(200);
        HttpResponse<String> tree = get("/api/v1/admin/permissions/tree", token);
        assertThat(tree.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(tree.body()).size()).isGreaterThan(0);

        HttpResponse<String> created = post("/api/v1/admin/depts", Map.of(
                "code", "IT_DEPT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                "name", "集成测试部门", "sortNo", 99), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String deptId = JSON.readTree(created.body()).path("id").asString();
        assertThat(delete("/api/v1/admin/depts/" + deptId, token).statusCode()).isEqualTo(204);
    }

    @Test
    void smsProviderConfigAndTestSend() throws Exception {
        String token = accessToken();
        String code = "it-sms-" + UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> config = Map.of(
                "accessKeyId", "ak-1234567890",
                "accessKeySecret", "sk-very-secret-value",
                "regionId", "cn-hangzhou");
        HttpResponse<String> created = post("/api/v1/admin/sms/providers", Map.of(
                "code", code, "type", "ALIYUN", "name", "集成测试服务商", "config", config,
                "countries", java.util.List.of("CN")), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        var node = JSON.readTree(created.body());
        assertThat(node.path("code").asString()).isEqualTo(code);
        // 密钥只回显掩码，响应体不出现明文
        assertThat(node.path("config").path("accessKeySecret").asString()).contains("****");
        assertThat(created.body()).doesNotContain("sk-very-secret-value");

        assertThat(get("/api/v1/admin/sms/providers", token).body()).contains(code);

        // 重复编码冲突
        assertThat(post("/api/v1/admin/sms/providers", Map.of(
                "code", code, "type", "TENCENT", "name", "重复服务商",
                "config", Map.of("secretId", "a", "secretKey", "b", "sdkAppId", "c", "region", "r"),
                "countries", java.util.List.of("CN")), token).statusCode()).isEqualTo(409);

        // 测试发送：未接入厂商 SDK 时降级为日志出口，返回成功
        HttpResponse<String> sent = post("/api/v1/admin/sms/providers/" + code + "/test",
                Map.of("phone", "+8613800138000", "signature", "vane", "templateCode", "TEST"), token);
        assertThat(sent.statusCode()).withFailMessage("body: %s", sent.body()).isEqualTo(200);
        assertThat(JSON.readTree(sent.body()).path("success").asBoolean()).isTrue();

        // 白名单外号码拒绝
        HttpResponse<String> rejected = post("/api/v1/admin/sms/providers/" + code + "/test",
                Map.of("phone", "+14155552671", "signature", "vane", "templateCode", "TEST"), token);
        assertThat(rejected.statusCode()).isEqualTo(422);
        assertThat(rejected.body()).contains("SMS.NUMBER.NOT_SUPPORTED");

        // 修改名称并设为默认
        HttpResponse<String> updated = patch("/api/v1/admin/sms/providers/" + code,
                Map.of("name", "集成测试服务商2", "isDefault", true), token);
        assertThat(updated.statusCode()).withFailMessage("body: %s", updated.body()).isEqualTo(200);
        assertThat(JSON.readTree(updated.body()).path("isDefault").asBoolean()).isTrue();
        assertThat(JSON.readTree(updated.body()).path("name").asString()).isEqualTo("集成测试服务商2");
    }

    @Test
    void smsSignatureBindingAndLogEndpoints() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String providerCode = "it-sms-sig-" + suffix;
        assertThat(post("/api/v1/admin/sms/providers", Map.of(
                "code", providerCode, "type", "ALIYUN", "name", "签名测试服务商",
                "config", Map.of("accessKeyId", "ak-1", "accessKeySecret", "sk-1", "regionId", "cn-hangzhou"),
                "countries", java.util.List.of("CN"), "isDefault", true), token).statusCode()).isEqualTo(201);

        HttpResponse<String> signature = post("/api/v1/admin/sms/signatures", Map.of(
                "providerCode", providerCode, "signName", "vane", "countryCode", "CN", "locale", "zh-CN"),
                token);
        assertThat(signature.statusCode()).withFailMessage("body: %s", signature.body()).isEqualTo(201);
        String signatureId = JSON.readTree(signature.body()).path("id").asString();
        assertThat(get("/api/v1/admin/sms/signatures?provider=" + providerCode, token).body()).contains("vane");

        // 绑定需要一条 SMS 通道模板：直接写入模板表（管理端当前只提供编辑译文）
        String templateCode = "it-sms-template-" + suffix;
        jdbc.update("insert into sys_message_template (code, channel, remark) values (?, 'SMS', '集成测试短信模板')",
                templateCode);
        String templateId = jdbc.queryForObject("select id from sys_message_template where code = ?",
                (rs, rowNum) -> rs.getObject("id", UUID.class), templateCode).toString();

        HttpResponse<String> bound = put("/api/v1/admin/sms/template-bindings/" + templateId, Map.of(
                "bindings", java.util.List.of(Map.of(
                        "providerCode", providerCode,
                        "providerTemplateCode", "SMS_123456",
                        "signatureId", signatureId,
                        "countryCode", "CN",
                        "locale", "zh-CN",
                        "paramMapping", Map.of("code", "code")))), token);
        assertThat(bound.statusCode()).withFailMessage("body: %s", bound.body()).isEqualTo(200);
        assertThat(JSON.readTree(bound.body()).size()).isEqualTo(1);
        assertThat(get("/api/v1/admin/sms/template-bindings?provider=" + providerCode, token).body())
                .contains("SMS_123456");

        // 日志分页接口可用（尚无发送记录）
        HttpResponse<String> logs = get("/api/v1/admin/sms/logs?page%5Bnumber%5D=0&page%5Bsize%5D=20", token);
        assertThat(logs.statusCode()).withFailMessage("body: %s", logs.body()).isEqualTo(200);
        assertThat(JSON.readTree(logs.body()).path("items").isArray()).isTrue();
    }

    @Test
    void smsVerificationCodeSendAndVerify() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String providerCode = "it-sms-code-" + suffix;
        assertThat(post("/api/v1/admin/sms/providers", Map.of(
                "code", providerCode, "type", "ALIYUN", "name", "验证码测试服务商",
                "config", Map.of("accessKeyId", "ak-1", "accessKeySecret", "sk-1", "regionId", "cn-hangzhou"),
                "countries", java.util.List.of("CN"), "isDefault", true), token).statusCode()).isEqualTo(201);

        String templateId = jdbc.queryForObject(
                "select id from sys_message_template where code = 'password.reset' and channel = 'SMS'",
                (rs, rowNum) -> rs.getObject("id", UUID.class)).toString();
        HttpResponse<String> bound = put("/api/v1/admin/sms/template-bindings/" + templateId, Map.of(
                "bindings", java.util.List.of(Map.of(
                        "providerCode", providerCode, "providerTemplateCode", "SMS_1",
                        "countryCode", "CN", "locale", "zh-CN"))), token);
        assertThat(bound.statusCode()).withFailMessage("body: %s", bound.body()).isEqualTo(200);

        HttpResponse<String> sent = post("/api/v1/auth/sms-code/send", Map.of(
                "scene", "LOGIN", "phone", "+8613800138000", "locale", "zh-CN"), null);
        assertThat(sent.statusCode()).withFailMessage("body: %s", sent.body()).isEqualTo(202);

        // 60 秒冷却
        HttpResponse<String> again = post("/api/v1/auth/sms-code/send", Map.of(
                "scene", "LOGIN", "phone", "+8613800138000", "locale", "zh-CN"), null);
        assertThat(again.statusCode()).withFailMessage("body: %s", again.body()).isEqualTo(429);
        assertThat(again.body()).contains("SMS.RATE_LIMITED");

        // 错误验证码
        HttpResponse<String> invalid = post("/api/v1/auth/sms-code/verify", Map.of(
                "scene", "LOGIN", "phone", "+8613800138000", "code", "000000"), null);
        assertThat(invalid.statusCode()).withFailMessage("body: %s", invalid.body()).isEqualTo(422);
        assertThat(invalid.body()).contains("SMS.CODE.INVALID");
    }

    @Test
    void smsCampaignImportAndWorker() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String providerCode = "it-sms-camp-" + suffix;
        assertThat(post("/api/v1/admin/sms/providers", Map.of(
                "code", providerCode, "type", "ALIYUN", "name", "群发测试服务商",
                "config", Map.of("accessKeyId", "ak-1", "accessKeySecret", "sk-1", "regionId", "cn-hangzhou"),
                "countries", java.util.List.of("CN"), "isDefault", true), token).statusCode()).isEqualTo(201);

        String templateId = jdbc.queryForObject(
                "select id from sys_message_template where code = 'password.reset' and channel = 'SMS'",
                (rs, rowNum) -> rs.getObject("id", UUID.class)).toString();
        assertThat(put("/api/v1/admin/sms/template-bindings/" + templateId, Map.of(
                "bindings", java.util.List.of(Map.of(
                        "providerCode", providerCode, "providerTemplateCode", "SMS_1",
                        "countryCode", "CN", "locale", "zh-CN"))), token).statusCode()).isEqualTo(200);

        HttpResponse<String> created = post("/api/v1/admin/sms/campaigns", Map.of(
                "name", "集成测试群发", "templateId", templateId, "providerCode", providerCode,
                "locale", "zh-CN", "throttlePerSecond", 1000), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String campaignId = JSON.readTree(created.body()).path("id").asString();

        String csv = "phone,params\n+8613800138000,\"{\"\"code\"\":\"\"1\"\",\"\"minutes\"\":\"\"5\"\"}\"\nbad,{}\n";
        HttpResponse<String> preview = postMultipart("/api/v1/admin/sms/campaigns/import/preview", csv, token);
        assertThat(preview.statusCode()).withFailMessage("body: %s", preview.body()).isEqualTo(200);
        assertThat(JSON.readTree(preview.body()).path("valid").asInt()).withFailMessage("preview=%s", preview.body()).isEqualTo(1);
        assertThat(JSON.readTree(preview.body()).path("invalid").asInt()).isEqualTo(1);

        HttpResponse<String> imported = postMultipart(
                "/api/v1/admin/sms/campaigns/" + campaignId + "/recipients/import", csv, token);
        assertThat(imported.statusCode()).withFailMessage("body: %s", imported.body()).isEqualTo(200);

        assertThat(post("/api/v1/admin/sms/campaigns/" + campaignId + "/start", Map.of(), token).statusCode())
                .isEqualTo(202);

        // 执行 worker（同一 JVM 同步执行），把 PENDING 发出去
        String jobs = get("/api/v1/admin/jobs", token).body();
        String jobId = null;
        for (JsonNode job : JSON.readTree(jobs)) {
            if ("sms-campaign-worker".equals(job.path("jobCode").asString())) {
                jobId = job.path("id").asString();
                break;
            }
        }
        assertThat(jobId).as("短信群发 worker 任务应已由 V13 种子创建").isNotNull();
        assertThat(post("/api/v1/admin/jobs/" + jobId + "/run", Map.of(), token).statusCode()).isEqualTo(202);

        HttpResponse<String> campaign = get("/api/v1/admin/sms/campaigns/" + campaignId, token);
        assertThat(campaign.statusCode()).withFailMessage("body: %s", campaign.body()).isEqualTo(200);
        assertThat(JSON.readTree(campaign.body()).path("status").asString()).isEqualTo("DONE");

        HttpResponse<String> recipients = get(
                "/api/v1/admin/sms/campaigns/" + campaignId + "/recipients?page%5Bsize%5D=10", token);
        assertThat(recipients.statusCode()).isEqualTo(200);
        boolean sent = false;
        for (JsonNode recipient : JSON.readTree(recipients.body()).path("items")) {
            if ("SENT".equals(recipient.path("status").asString())) {
                sent = true;
            }
        }
        assertThat(sent).as("有效收件人应发送成功").isTrue();
    }

    @Test
    void smsReceiptInboundAndOptout() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String providerCode = "it-sms-cb-" + suffix;
        String callbackToken = "it-token-" + suffix;
        assertThat(post("/api/v1/admin/sms/providers", Map.of(
                "code", providerCode, "type", "ALIYUN", "name", "回调测试服务商",
                "config", Map.of("accessKeyId", "ak-1", "accessKeySecret", "sk-1", "regionId", "cn-hangzhou",
                        "callbackToken", callbackToken),
                "countries", java.util.List.of("CN")), token).statusCode()).isEqualTo(201);

        String messageId = "it-msg-" + suffix;
        jdbc.update("""
                insert into sys_sms_log (template_code, phone_masked, phone_hash, country_code, provider_code,
                                         provider_message_id, result)
                values ('password.reset', '+8613****8000', ?, 'CN', ?, ?, 'SUCCESS')
                """, "hash-" + suffix, providerCode, messageId);

        // 回执：错误 token 403，正确 token 202
        assertThat(postWithCallbackToken("/api/v1/public/sms/receipt/" + providerCode,
                Map.of("providerMessageId", messageId, "status", "DELIVERED"), "bad-token").statusCode())
                .isEqualTo(403);
        assertThat(postWithCallbackToken("/api/v1/public/sms/receipt/" + providerCode,
                Map.of("providerMessageId", messageId, "status", "DELIVERED"), callbackToken).statusCode())
                .isEqualTo(202);

        HttpResponse<String> logs = get("/api/v1/admin/sms/logs?page%5Bsize%5D=10", token);
        assertThat(logs.statusCode()).isEqualTo(200);
        boolean delivered = false;
        for (JsonNode item : JSON.readTree(logs.body()).path("items")) {
            if (messageId.equals(item.path("providerMessageId").asString())
                    && "DELIVERED".equals(item.path("deliveryStatus").asString())) {
                delivered = true;
            }
        }
        assertThat(delivered).as("日志应展示最新回执状态").isTrue();

        // 上行 TD → 自动退订
        assertThat(postWithCallbackToken("/api/v1/public/sms/inbound/" + providerCode,
                Map.of("providerMessageId", "in-" + suffix, "phone", "+8613700137000", "content", "TD"),
                callbackToken).statusCode()).isEqualTo(202);

        HttpResponse<String> optouts = get("/api/v1/admin/sms/optouts?page%5Bsize%5D=20", token);
        assertThat(optouts.statusCode()).withFailMessage("body: %s", optouts.body()).isEqualTo(200);
        assertThat(JSON.readTree(optouts.body()).path("page").path("total").asInt()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void jobCreationRunAndTemplateSend() throws Exception {
        String token = accessToken();
        HttpResponse<String> job = post("/api/v1/admin/jobs", Map.of(
                "jobCode", "it-job-" + UUID.randomUUID().toString().substring(0, 6),
                "nameKey", "job.it", "handler", "cleanup-idempotency",
                "cron", "0 0 3 * * *", "concurrency", "FORBID"), token);
        assertThat(job.statusCode()).withFailMessage("body: %s", job.body()).isEqualTo(201);
        String jobId = JSON.readTree(job.body()).path("id").asString();
        assertThat(post("/api/v1/admin/jobs/" + jobId + "/run", Map.of(), token).statusCode()).isEqualTo(202);

        // 手动触发是同步执行：执行日志应立即可查
        HttpResponse<String> jobLogs = get("/api/v1/admin/jobs/" + jobId + "/logs", token);
        assertThat(jobLogs.statusCode()).withFailMessage("body: %s", jobLogs.body()).isEqualTo(200);
        JsonNode logPage = JSON.readTree(jobLogs.body());
        assertThat(logPage.path("page").path("total").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(logPage.path("items").path(0).path("success").asBoolean()).isTrue();

        HttpResponse<String> templates = get("/api/v1/admin/message-templates?channel=EMAIL", token);
        assertThat(templates.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(templates.body()).size()).isEqualTo(2);

        HttpResponse<String> sent = post("/api/v1/admin/message-templates/user.welcome/send", Map.of(
                "to", "it@example.com", "locale", "en",
                "variables", Map.of("siteName", "vane", "username", "IT")), token);
        assertThat(sent.statusCode()).withFailMessage("body: %s", sent.body()).isEqualTo(200);
        assertThat(sent.body()).contains("vane");
    }

    @Test
    void auditQueriesAndHealth() throws Exception {
        String token = accessToken();
        // 先造一条操作日志（创建用户），再验证列表能看到「操作了什么」：请求方法、路径与参数
        String username = "it-audit-oper-" + UUID.randomUUID().toString().substring(0, 6);
        String deptId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("deptId").asString();
        assertThat(post("/api/v1/admin/users", Map.of(
                "username", username, "password", "Audit@12345", "nickname", "操作日志用户", "deptId", deptId),
                token).statusCode()).isEqualTo(201);

        HttpResponse<String> operLogs = get("/api/v1/admin/oper-logs?module=user&page%5Bsize%5D=100", token);
        assertThat(operLogs.statusCode()).isEqualTo(200);
        JsonNode operItems = JSON.readTree(operLogs.body()).path("items");
        assertThat(operItems).isNotEmpty();
        boolean found = false;
        for (JsonNode item : operItems) {
            if (item.path("path").asString().equals("/api/v1/admin/users")) {
                found = true;
                assertThat(item.path("httpMethod").asString()).isEqualTo("POST");
                assertThat(item.path("params").asString()).contains("password").contains("***");
            }
        }
        assertThat(found).as("操作日志应包含请求路径与参数").isTrue();

        assertThat(get("/api/v1/admin/login-logs?result=SUCCESS", token).statusCode()).isEqualTo(200);

        HttpResponse<String> health = get("/api/v1/admin/health", token);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(health.body()).path("status").asString()).isEqualTo("UP");
    }

    @Test
    void mfaBindVerifyAndDisable() throws Exception {
        String token = accessToken();
        String password = ADMIN_PASSWORD;

        HttpResponse<String> bind = post("/api/v1/auth/mfa/bind", Map.of("password", password), token);
        assertThat(bind.statusCode()).withFailMessage("body: %s", bind.body()).isEqualTo(200);
        String secret = JSON.readTree(bind.body()).path("secretBase32").asString();
        assertThat(secret).isNotBlank();

        HttpResponse<String> confirm = post("/api/v1/auth/mfa/bind/confirm",
                Map.of("code", totp(secret, 0)), token);
        assertThat(confirm.statusCode()).withFailMessage("body: %s", confirm.body()).isEqualTo(200);
        assertThat(JSON.readTree(confirm.body()).path("recoveryCodes").size()).isEqualTo(10);

        // 已启用 MFA：登录只返回一次性挑战
        HttpResponse<String> login = post("/api/v1/auth/login",
                Map.of("username", ADMIN, "password", password), null);
        JsonNode challenge = JSON.readTree(login.body());
        assertThat(challenge.path("mfaRequired").asBoolean()).isTrue();
        String mfaToken = challenge.path("mfaToken").asString();

        HttpResponse<String> verify = post("/api/v1/auth/mfa/verify",
                Map.of("mfaToken", mfaToken, "code", totp(secret, 1)), null);
        assertThat(verify.statusCode()).withFailMessage("body: %s", verify.body()).isEqualTo(200);
        String newToken = JSON.readTree(verify.body()).path("accessToken").asString();
        assertThat(newToken).isNotBlank();

        // 关闭 MFA，恢复后续用例可登录
        assertThat(post("/api/v1/auth/mfa/disable", Map.of("password", password), newToken).statusCode())
                .isEqualTo(204);
    }

    @Test
    void userCrudRoleAssignmentAndPasswordReset() throws Exception {
        String token = accessToken();
        String deptId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("deptId").asString();
        String username = "it-user-" + UUID.randomUUID().toString().substring(0, 8);

        HttpResponse<String> created = post("/api/v1/admin/users", Map.of(
                "username", username,
                "password", "Integration@12345",
                "nickname", "IT User",
                "deptId", deptId
        ), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        JsonNode createdBody = JSON.readTree(created.body());
        String userId = createdBody.path("id").asString();
        assertThat(createdBody.path("deptName").asString()).isNotBlank();
        assertThat(createdBody.path("version").asLong()).isZero();

        HttpResponse<String> filtered = get("/api/v1/admin/users?username=" + username, token);
        assertThat(filtered.statusCode()).isEqualTo(200);
        JsonNode page = JSON.readTree(filtered.body());
        assertThat(page.path("page").path("total").asInt()).isEqualTo(1);
        JsonNode row = page.path("items").path(0);
        assertThat(row.path("deptName").asString()).isNotBlank();
        assertThat(row.path("roles").isArray()).isTrue();

        HttpResponse<String> detail = get("/api/v1/admin/users/" + userId, token);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(detail.headers().firstValue("ETag").orElse("")).contains("0");

        HttpResponse<String> patched = patchWithIfMatch("/api/v1/admin/users/" + userId,
                Map.of("nickname", "IT User 2", "deptId", deptId, "status", "ACTIVE"), token, "\"0\"");
        assertThat(patched.statusCode()).withFailMessage("body: %s", patched.body()).isEqualTo(200);
        HttpResponse<String> stale = patchWithIfMatch("/api/v1/admin/users/" + userId,
                Map.of("nickname", "IT User 3", "deptId", deptId, "status", "ACTIVE"), token, "\"0\"");
        assertThat(stale.statusCode()).isEqualTo(412);

        HttpResponse<String> roleList = get("/api/v1/admin/roles?page%5Bsize%5D=100", token);
        String roleId = null;
        for (JsonNode roleNode : JSON.readTree(roleList.body()).path("items")) {
            if ("ADMIN".equals(roleNode.path("code").asString())) {
                roleId = roleNode.path("id").asString();
                break;
            }
        }
        assertThat(roleId).withFailMessage("body: %s", roleList.body()).isNotBlank();
        HttpResponse<String> assigned = put("/api/v1/admin/users/" + userId + "/roles",
                Map.of("roleIds", java.util.List.of(roleId)), token);
        assertThat(assigned.statusCode()).isEqualTo(204);
        HttpResponse<String> roles = get("/api/v1/admin/users/" + userId + "/roles", token);
        assertThat(roles.body()).contains(roleId);

        HttpResponse<String> reset = post("/api/v1/admin/users/" + userId + "/reset-password", Map.of(), token);
        assertThat(reset.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(reset.body()).path("password").asString()).isNotBlank();

        assertThat(delete("/api/v1/admin/users/" + userId, token).statusCode()).isEqualTo(204);
        HttpResponse<String> afterDelete = get("/api/v1/admin/users?username=" + username, token);
        assertThat(JSON.readTree(afterDelete.body()).path("page").path("total").asInt()).isZero();
    }

    private HttpResponse<String> patchWithIfMatch(String path, Object body, String token, String ifMatch)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("If-Match", ifMatch)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }



    @Test
    void selfPasswordChangeFlow() throws Exception {
        String adminToken = accessToken();
        String deptId = JSON.readTree(get("/api/v1/auth/me", adminToken).body()).path("deptId").asString();
        String username = "it-chpass-" + UUID.randomUUID().toString().substring(0, 8);
        String initial = "Init@123456";

        HttpResponse<String> created = post("/api/v1/admin/users", Map.of(
                "username", username, "password", initial, "nickname", "ChPass", "deptId", deptId), adminToken);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String userId = JSON.readTree(created.body()).path("id").asString();

        String token = JSON.readTree(post("/api/v1/auth/login",
                Map.of("username", username, "password", initial), null).body())
                .path("accessToken").asString();

        // 当前密码错误 → 422
        assertThat(post("/api/v1/auth/password",
                Map.of("currentPassword", "Wrong@123", "newPassword", "Next@123456"), token).statusCode())
                .isEqualTo(422);
        // 低于最小长度 → 422
        assertThat(post("/api/v1/auth/password",
                Map.of("currentPassword", initial, "newPassword", "abc"), token).statusCode())
                .isEqualTo(422);
        // 与原密码相同 → 422
        assertThat(post("/api/v1/auth/password",
                Map.of("currentPassword", initial, "newPassword", initial), token).statusCode())
                .isEqualTo(422);

        // 成功 → 204
        assertThat(post("/api/v1/auth/password",
                Map.of("currentPassword", initial, "newPassword", "Next@123456"), token).statusCode())
                .isEqualTo(204);

        // 旧密码失效、新密码生效
        assertThat(post("/api/v1/auth/login", Map.of("username", username, "password", initial), null)
                .statusCode()).isEqualTo(401);
        assertThat(post("/api/v1/auth/login", Map.of("username", username, "password", "Next@123456"), null)
                .statusCode()).isEqualTo(200);

        assertThat(delete("/api/v1/admin/users/" + userId, adminToken).statusCode()).isEqualTo(204);
    }

    @Test
    void editingNoticePageBannerAndJob() throws Exception {
        String token = accessToken();

        // 公告：创建 → 编辑标题 → 列表可见新标题
        HttpResponse<String> notice = post("/api/v1/admin/notices", Map.of(
                "level", "INFO", "pinned", false,
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "标题A", "body", "正文"))), token);
        assertThat(notice.statusCode()).isEqualTo(201);
        String noticeId = JSON.readTree(notice.body()).path("id").asString();
        HttpResponse<String> noticePatched = patch("/api/v1/admin/notices/" + noticeId, Map.of(
                "level", "URGENT", "pinned", true,
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "标题B", "body", "正文2"))), token);
        assertThat(noticePatched.statusCode()).withFailMessage("body: %s", noticePatched.body()).isEqualTo(200);
        assertThat(JSON.readTree(noticePatched.body()).path("level").asString()).isEqualTo("URGENT");
        assertThat(JSON.readTree(noticePatched.body()).path("translations").path(0).path("title").asString())
                .isEqualTo("标题B");

        // 内容页：创建 → 编辑译文
        String slug = "edit-" + UUID.randomUUID().toString().substring(0, 8);
        HttpResponse<String> page = post("/api/v1/admin/pages", Map.of(
                "slug", slug,
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "页A", "body", "<p>a</p>"))), token);
        assertThat(page.statusCode()).isEqualTo(201);
        String pageId = JSON.readTree(page.body()).path("id").asString();
        HttpResponse<String> pagePatched = patch("/api/v1/admin/pages/" + pageId, Map.of(
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "页B", "body", "<p>b</p>"))), token);
        assertThat(pagePatched.statusCode()).withFailMessage("body: %s", pagePatched.body()).isEqualTo(200);
        assertThat(JSON.readTree(pagePatched.body()).path("translations").path(0).path("title").asString())
                .isEqualTo("页B");

        // 轮播：创建 → 编辑标题 key 与投放状态
        String fileId = uploadPublicFile(token);
        HttpResponse<String> banner = post("/api/v1/admin/banners", Map.of(
                "position", "home.top", "imageFileId", fileId, "titleKey", "banner.a", "sortNo", 1), token);
        assertThat(banner.statusCode()).isEqualTo(201);
        String bannerId = JSON.readTree(banner.body()).path("id").asString();
        HttpResponse<String> bannerPatched = patch("/api/v1/admin/banners/" + bannerId, Map.of(
                "imageFileId", fileId, "titleKey", "banner.b", "linkUrl", "/x", "sortNo", 2, "status", "DISABLED"), token);
        assertThat(bannerPatched.statusCode()).withFailMessage("body: %s", bannerPatched.body()).isEqualTo(200);
        assertThat(JSON.readTree(bannerPatched.body()).path("status").asString()).isEqualTo("DISABLED");

        // 定时任务：创建 → 编辑为停用
        HttpResponse<String> job = post("/api/v1/admin/jobs", Map.of(
                "jobCode", "edit-job-" + UUID.randomUUID().toString().substring(0, 6),
                "nameKey", "job.edit", "handler", "cleanup-idempotency",
                "cron", "0 0 4 * * *", "concurrency", "FORBID"), token);
        assertThat(job.statusCode()).isEqualTo(201);
        String jobId = JSON.readTree(job.body()).path("id").asString();
        HttpResponse<String> jobPatched = patch("/api/v1/admin/jobs/" + jobId, Map.of(
                "nameKey", "job.edit2", "cron", "0 0 5 * * *", "status", "PAUSED", "concurrency", "ALLOW"), token);
        assertThat(jobPatched.statusCode()).withFailMessage("body: %s", jobPatched.body()).isEqualTo(200);
        assertThat(JSON.readTree(jobPatched.body()).path("status").asString()).isEqualTo("PAUSED");
        assertThat(JSON.readTree(jobPatched.body()).path("concurrency").asString()).isEqualTo("ALLOW");
    }

    @Test
    void dictionaryCrud() throws Exception {
        String token = accessToken();
        String typeCode = "it_dict_" + UUID.randomUUID().toString().substring(0, 6);

        assertThat(post("/api/v1/admin/dicts", Map.of(
                "typeCode", typeCode, "nameKey", "dict.it", "remark", "集成测试字典"), token).statusCode())
                .isEqualTo(201);

        // 字典项：中英标签
        assertThat(post("/api/v1/admin/dicts/" + typeCode + "/items", Map.of(
                "value", "A", "color", "success", "sortNo", 1,
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "label", "甲"),
                        Map.of("locale", "en", "label", "Alpha"))), token).statusCode()).isEqualTo(201);

        HttpResponse<String> zhItems = get("/api/v1/admin/dicts/" + typeCode + "/items", token);
        HttpResponse<String> enItems = getWithLanguage("/api/v1/admin/dicts/" + typeCode + "/items", token, "en");
        assertThat(zhItems.body()).contains("甲");
        assertThat(enItems.body()).contains("Alpha");
        assertThat(zhItems.body()).contains("\"builtin\":false");

        // 编辑字典项标签
        assertThat(patch("/api/v1/admin/dicts/" + typeCode + "/items/A", Map.of(
                "color", "warning", "sortNo", 2, "status", "ACTIVE",
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "label", "乙"))), token).statusCode())
                .isEqualTo(204);
        assertThat(get("/api/v1/admin/dicts/" + typeCode + "/items", token).body()).contains("乙");

        // 字典下仍有字典项时禁止删除类型
        assertThat(delete("/api/v1/admin/dicts/" + typeCode, token).statusCode()).isEqualTo(409);
        // 内置字典项禁止删除
        assertThat(delete("/api/v1/admin/dicts/common_status/items/ACTIVE", token).statusCode()).isEqualTo(409);

        assertThat(delete("/api/v1/admin/dicts/" + typeCode + "/items/A", token).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/admin/dicts/" + typeCode, token).statusCode()).isEqualTo(204);
    }

    @Test
    void auditLogCsvExport() throws Exception {
        String token = accessToken();

        HttpResponse<String> oper = get("/api/v1/admin/oper-logs/export", token);
        assertThat(oper.statusCode()).isEqualTo(200);
        assertThat(oper.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("text/csv");
        assertThat(oper.headers().firstValue(HttpHeaders.CONTENT_DISPOSITION).orElse(""))
                .contains("oper-logs.csv");
        assertThat(oper.body()).contains("traceId").contains("时间");

        HttpResponse<String> login = get("/api/v1/admin/login-logs/export", token);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("text/csv");
        assertThat(login.body()).contains("traceId").contains("结果");

        HttpResponse<String> usersCsv = get("/api/v1/admin/users/export", token);
        assertThat(usersCsv.statusCode()).isEqualTo(200);
        assertThat(usersCsv.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("text/csv");
        assertThat(usersCsv.body()).contains("用户名").contains("admin");
    }

    @Test
    void errorMessagesLocalizedForAllSupportedLocales() throws Exception {
        String[] tags = {"zh-CN", "zh-TW", "en", "ja", "ko", "ru", "de", "fr", "es", "pt-BR",
                "it", "nl", "pl", "tr", "uk", "vi", "th", "id", "ar", "hi"};
        String zhDetail = null;
        String jaDetail = null;
        String arDetail = null;

        for (String tag : tags) {
            HttpResponse<String> response = getWithLanguage("/api/v1/auth/me", null, tag);
            assertThat(response.statusCode()).as(tag).isEqualTo(401);
            String detail = JSON.readTree(response.body()).path("detail").asString();
            assertThat(detail).as(tag).isNotBlank();
            switch (tag) {
                case "zh-CN" -> zhDetail = detail;
                case "ja" -> jaDetail = detail;
                case "ar" -> arDetail = detail;
                default -> {
                }
            }
        }

        assertThat(jaDetail).isNotEqualTo(zhDetail);
        assertThat(arDetail).isNotEqualTo(zhDetail);
    }

    @Test
    void chineseTextRoundTripThroughApi() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        String deptCode = "IT_CN_" + suffix;
        String username = "it-cn-" + suffix;

        // 组织名：中文写入后按 UTF-8 原样返回（防止编码链路回归）
        HttpResponse<String> createdDept = post("/api/v1/admin/depts", Map.of(
                "parentId", JSON.nullNode(), "code", deptCode, "name", "集成测试部门", "sortNo", 1), token);
        assertThat(createdDept.statusCode()).withFailMessage("body: %s", createdDept.body()).isEqualTo(201);
        String deptId = JSON.readTree(createdDept.body()).path("id").asString();

        HttpResponse<String> tree = get("/api/v1/admin/depts/tree", token);
        assertThat(tree.body()).contains("集成测试部门");

        // 用户昵称：中文写入后列表可见
        HttpResponse<String> createdUser = post("/api/v1/admin/users", Map.of(
                "username", username, "password", "Cn@123456", "nickname", "中文昵称用户", "deptId", deptId), token);
        assertThat(createdUser.statusCode()).withFailMessage("body: %s", createdUser.body()).isEqualTo(201);
        String userId = JSON.readTree(createdUser.body()).path("id").asString();

        String list = get("/api/v1/admin/users?page%5Bnumber%5D=0&page%5Bsize%5D=100&username=" + username, token).body();
        assertThat(list).contains("中文昵称用户").contains("集成测试部门");

        assertThat(delete("/api/v1/admin/users/" + userId, token).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/admin/depts/" + deptId, token).statusCode()).isIn(200, 204);
    }

    @Test
    void auditColumnsRecordActingUser() throws Exception {
        String adminToken = accessToken();
        String adminId = JSON.readTree(get("/api/v1/auth/me", adminToken).body()).path("id").asString();
        String deptId = JSON.readTree(get("/api/v1/auth/me", adminToken).body()).path("deptId").asString();

        // A（admin）创建用户
        String username = "it-audit-" + UUID.randomUUID().toString().substring(0, 6);
        HttpResponse<String> created = post("/api/v1/admin/users", Map.of(
                "username", username, "password", "Audit@12345", "nickname", "审计用户", "deptId", deptId), adminToken);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String userId = JSON.readTree(created.body()).path("id").asString();

        UUID createdBy = jdbc.queryForObject("select created_by from iam_user where id = ?::uuid",
                (rs, rowNum) -> rs.getObject("created_by", UUID.class), userId);
        assertThat(createdBy).as("created_by 应为 admin").hasToString(adminId);
        UUID updatedBy = jdbc.queryForObject("select updated_by from iam_user where id = ?::uuid",
                (rs, rowNum) -> rs.getObject("updated_by", UUID.class), userId);
        assertThat(updatedBy).as("初始 updated_by 应为 admin").hasToString(adminId);

        // 另一位超级管理员（B）修改昵称，updated_by 应变为 B
        String secondName = "it-audit2-" + UUID.randomUUID().toString().substring(0, 6);
        HttpResponse<String> second = post("/api/v1/admin/users", Map.of(
                "username", secondName, "password", "Audit@12345", "nickname", "第二管理员", "deptId", deptId),
                adminToken);
        assertThat(second.statusCode()).withFailMessage("body: %s", second.body()).isEqualTo(201);
        String secondId = JSON.readTree(second.body()).path("id").asString();

        String roleId = null;
        for (var role : JSON.readTree(get("/api/v1/admin/roles", adminToken).body()).path("items")) {
            if ("SUPER_ADMIN".equals(role.path("code").asString())) {
                roleId = role.path("id").asString();
            }
        }
        assertThat(roleId).isNotNull();
        assertThat(put("/api/v1/admin/users/" + secondId + "/roles",
                Map.of("roleIds", java.util.List.of(roleId)), adminToken).statusCode()).isIn(200, 204);

        String secondToken = JSON.readTree(post("/api/v1/auth/login",
                Map.of("username", secondName, "password", "Audit@12345"), null).body())
                .path("accessToken").asString();

        HttpResponse<String> patched = patchWithIfMatch("/api/v1/admin/users/" + userId,
                Map.of("nickname", "被 B 修改", "deptId", deptId, "status", "ACTIVE"), secondToken, "\"0\"");
        assertThat(patched.statusCode()).withFailMessage("body: %s", patched.body()).isEqualTo(200);

        UUID afterUpdate = jdbc.queryForObject("select updated_by from iam_user where id = ?::uuid",
                (rs, rowNum) -> rs.getObject("updated_by", UUID.class), userId);
        assertThat(afterUpdate).as("updated_by 应为第二个管理员").hasToString(secondId);

        assertThat(delete("/api/v1/admin/users/" + userId, adminToken).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/admin/users/" + secondId, adminToken).statusCode()).isEqualTo(204);
    }

    @Test
    void auditColumnsOnJdbcWrittenTables() throws Exception {
        String token = accessToken();
        String adminId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("id").asString();
        String deptId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("deptId").asString();
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        // 字典类型（JdbcTemplate 写入）：created_by 应为 admin
        String dictCode = "it_audit_dict_" + suffix;
        assertThat(post("/api/v1/admin/dicts", Map.of("typeCode", dictCode, "nameKey", "dict.audit",
                "remark", "审计验证"), token).statusCode()).isEqualTo(201);
        UUID dictCreatedBy = jdbc.queryForObject(
                "select created_by from sys_dict_type where type_code = ?",
                (rs, rowNum) -> rs.getObject("created_by", UUID.class), dictCode);
        assertThat(dictCreatedBy).as("sys_dict_type.created_by").hasToString(adminId);

        // 角色 + 授权（关联表 JdbcTemplate 写入）
        String roleCode = "IT_AUDIT_" + suffix;
        HttpResponse<String> role = post("/api/v1/admin/roles", Map.of(
                "code", roleCode, "name", "审计角色", "dataScope", "DEPT"), token);
        assertThat(role.statusCode()).withFailMessage("body: %s", role.body()).isEqualTo(201);
        String roleId = JSON.readTree(role.body()).path("id").asString();

        String permissionCode = null;
        for (var node : JSON.readTree(get("/api/v1/admin/permissions/tree", token).body())) {
            permissionCode = node.path("code").asString();
            break;
        }
        assertThat(permissionCode).isNotNull();
        assertThat(put("/api/v1/admin/roles/" + roleId + "/permissions",
                Map.of("permissionCodes", java.util.List.of(permissionCode)), token).statusCode()).isIn(200, 204);

        UUID permissionCreatedBy = jdbc.queryForObject(
                "select created_by from iam_role_permission where role_id = ?::uuid",
                (rs, rowNum) -> rs.getObject("created_by", UUID.class), roleId);
        assertThat(permissionCreatedBy).as("iam_role_permission.created_by").hasToString(adminId);

        // 用户 + 角色分配（关联表）
        String username = "it-audit-role-" + suffix;
        HttpResponse<String> user = post("/api/v1/admin/users", Map.of(
                "username", username, "password", "Audit@12345", "nickname", "审计角色用户", "deptId", deptId), token);
        assertThat(user.statusCode()).withFailMessage("body: %s", user.body()).isEqualTo(201);
        String userId = JSON.readTree(user.body()).path("id").asString();
        assertThat(put("/api/v1/admin/users/" + userId + "/roles",
                Map.of("roleIds", java.util.List.of(roleId)), token).statusCode()).isIn(200, 204);

        UUID userRoleCreatedBy = jdbc.queryForObject(
                "select created_by from iam_user_role where user_id = ?::uuid",
                (rs, rowNum) -> rs.getObject("created_by", UUID.class), userId);
        assertThat(userRoleCreatedBy).as("iam_user_role.created_by").hasToString(adminId);

        // 系统参数（JdbcTemplate 更新）：updated_by 应为 admin
        assertThat(patch("/api/v1/admin/configs/security.password.min-length",
                Map.of("valueJson", "6"), token).statusCode()).isEqualTo(200);
        UUID configUpdatedBy = jdbc.queryForObject(
                "select updated_by from sys_config where config_key = 'security.password.min-length'",
                (rs, rowNum) -> rs.getObject("updated_by", UUID.class));
        assertThat(configUpdatedBy).as("sys_config.updated_by").hasToString(adminId);

        assertThat(delete("/api/v1/admin/users/" + userId, token).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/admin/roles/" + roleId, token).statusCode()).isIn(200, 204);
        assertThat(delete("/api/v1/admin/dicts/" + dictCode, token).statusCode()).isIn(200, 204);
    }

    @Test
    void userLocalePreferencePersisted() throws Exception {
        String token = accessToken();
        String adminId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("id").asString();

        assertThat(patch("/api/v1/auth/me/preferences", Map.of("locale", "en"), token).statusCode()).isEqualTo(204);

        String locale = JSON.readTree(get("/api/v1/auth/me", token).body()).path("locale").asString();
        assertThat(locale).isEqualTo("en");
        String stored = jdbc.queryForObject("select locale from iam_user where id = ?::uuid", String.class, adminId);
        assertThat(stored).isEqualTo("en");

        // 不支持的语言标签 → 422，且不改动已保存的偏好
        assertThat(patch("/api/v1/auth/me/preferences", Map.of("locale", "xx-YY"), token).statusCode()).isEqualTo(422);
        String stillEn = JSON.readTree(get("/api/v1/auth/me", token).body()).path("locale").asString();
        assertThat(stillEn).isEqualTo("en");

        // 大小写与下划线写法归一到资源文件的标签
        assertThat(patch("/api/v1/auth/me/preferences", Map.of("locale", "ZH_cn"), token).statusCode()).isEqualTo(204);
        assertThat(JSON.readTree(get("/api/v1/auth/me", token).body()).path("locale").asString()).isEqualTo("zh-CN");

        // 时区偏好：IANA ID 持久化，异步场景可用
        assertThat(patch("/api/v1/auth/me/preferences", Map.of("timezone", "Asia/Tokyo"), token).statusCode())
                .isEqualTo(204);
        assertThat(JSON.readTree(get("/api/v1/auth/me", token).body()).path("timezone").asString())
                .isEqualTo("Asia/Tokyo");
        assertThat(jdbc.queryForObject("select timezone from iam_user where id = ?::uuid", String.class, adminId))
                .isEqualTo("Asia/Tokyo");

        // 非法时区（非 IANA ID）→ 422，已有值不变
        assertThat(patch("/api/v1/auth/me/preferences", Map.of("timezone", "Not/AZone"), token).statusCode())
                .isEqualTo(422);
        assertThat(JSON.readTree(get("/api/v1/auth/me", token).body()).path("timezone").asString())
                .isEqualTo("Asia/Tokyo");

        // 空 body → 422
        assertThat(patch("/api/v1/auth/me/preferences", Map.of(), token).statusCode()).isEqualTo(422);

        // 恢复默认时区
        assertThat(patch("/api/v1/auth/me/preferences", Map.of("timezone", "Asia/Shanghai"), token).statusCode())
                .isEqualTo(204);
    }

    @Test
    void publicSeoEndpoints() throws Exception {
        String token = accessToken();
        String slug = "it-seo-" + UUID.randomUUID().toString().substring(0, 6);
        String body = """
                {"slug":"%s","translations":[
                  {"locale":"zh-CN","title":"SEO 测试页","summary":"摘要","body":"<p>正文</p>"},
                  {"locale":"en","title":"SEO test page","summary":"summary","body":"<p>body</p>"}
                ]}
                """.formatted(slug);
        HttpResponse<String> created = post("/api/v1/admin/pages", JSON.readTree(body), token);
        assertThat(created.statusCode()).withFailMessage("body: %s", created.body()).isEqualTo(201);
        String pageId = JSON.readTree(created.body()).path("id").asString();
        assertThat(post("/api/v1/admin/pages/" + pageId + "/publish", Map.of(), token).statusCode()).isEqualTo(200);

        // 站点元信息：名称、描述、根地址与支持语言
        HttpResponse<String> site = get("/api/v1/public/site", null);
        assertThat(site.statusCode()).isEqualTo(200);
        JsonNode siteJson = JSON.readTree(site.body());
        assertThat(siteJson.path("siteName").asString()).isNotBlank();
        assertThat(siteJson.path("baseUrl").asString()).startsWith("http");
        assertThat(siteJson.path("locales")).hasSize(20);
        assertThat(siteJson.path("defaultLocale").asString()).isEqualTo("zh-CN");

        // 可索引内容清单
        HttpResponse<String> pages = get("/api/v1/public/pages", null);
        assertThat(pages.statusCode()).isEqualTo(200);
        assertThat(pages.body()).contains(slug);

        // sitemap：含首页与内容页的本地化 URL、hreflang 互指
        HttpResponse<String> sitemap = get("/sitemap.xml", null);
        assertThat(sitemap.statusCode()).isEqualTo(200);
        assertThat(sitemap.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElse(""))
                .startsWith("application/xml");
        assertThat(sitemap.body()).contains("<urlset").contains("xmlns:xhtml");
        assertThat(sitemap.body()).contains(siteJson.path("baseUrl").asString() + "/zh-CN/" + slug);
        assertThat(sitemap.body()).contains("hreflang=\"en\"");

        // robots：指向 sitemap 并禁止抓取后台
        HttpResponse<String> robots = get("/robots.txt", null);
        assertThat(robots.statusCode()).isEqualTo(200);
        assertThat(robots.body()).contains("Sitemap: ").contains("Disallow: /admin/");
    }

    // ---------- 内容体系：分类 / 文章 / 标签 ----------

    @Test
    void categoryCrudAndDeleteProtection() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String code = "cat-" + suffix;
        String categoryId = createCategory(token, code, "分类" + suffix);

        HttpResponse<String> tree = get("/api/v1/admin/categories", token);
        assertThat(tree.statusCode()).isEqualTo(200);
        assertThat(tree.body()).contains(code).contains("分类" + suffix);

        // 改名与排序
        HttpResponse<String> patched = patch("/api/v1/admin/categories/" + categoryId, Map.of(
                "code", code, "sortNo", 20, "status", "ACTIVE",
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "name", "改名" + suffix))), token);
        assertThat(patched.statusCode()).withFailMessage("body: %s", patched.body()).isEqualTo(200);
        assertThat(patched.body()).contains("改名" + suffix);

        // 重复 code → 409
        HttpResponse<String> duplicate = post("/api/v1/admin/categories", Map.of(
                "code", code, "translations", java.util.List.of(Map.of("locale", "zh-CN", "name", "重复"))), token);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).path("code").asString())
                .isEqualTo("CONTENT.CATEGORY_CODE_TAKEN");
        assertThat(JSON.readTree(duplicate.body()).path("detail").asString()).isEqualTo("分类编码已存在");
        assertThat(duplicate.headers().firstValue(HttpHeaders.CONTENT_LANGUAGE).orElse("")).isEqualTo("zh-CN");

        // 分类下仍有文章 → 删除被拒绝
        String slug = "cat-art-" + suffix;
        String articleId = createArticle(token, slug, categoryId, "分类文章" + suffix);
        HttpResponse<String> blocked = delete("/api/v1/admin/categories/" + categoryId, token);
        assertThat(blocked.statusCode()).withFailMessage("body: %s", blocked.body()).isEqualTo(409);
        assertThat(JSON.readTree(blocked.body()).path("code").asString())
                .isEqualTo("CONTENT.CATEGORY_HAS_ARTICLES");

        assertThat(delete("/api/v1/admin/articles/" + articleId, token).statusCode()).isEqualTo(204);
        assertThat(delete("/api/v1/admin/categories/" + categoryId, token).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/admin/categories", token).body()).doesNotContain(code);
    }

    @Test
    void articleLifecycleAndPublicVisibility() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String code = "post-cat-" + suffix;
        String categoryId = createCategory(token, code, "文章分类" + suffix);
        String slug = "post-" + suffix;
        String titleZh = "文章标题" + suffix;

        String articleId = createArticle(token, slug, categoryId, titleZh);
        HttpResponse<String> created = get("/api/v1/admin/articles?keyword="
                + java.net.URLEncoder.encode(titleZh, java.nio.charset.StandardCharsets.UTF_8), token);
        assertThat(created.statusCode()).isEqualTo(200);
        assertThat(created.body()).contains(slug).contains("\"status\":\"DRAFT\"");

        // 多语言编辑：改中文标题并新增日文译文
        HttpResponse<String> patched = patch("/api/v1/admin/articles/" + articleId, Map.of(
                "slug", slug,
                "categoryId", categoryId,
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "title", titleZh + "改", "summary", "新摘要",
                                "body", "<p>修改后的正文</p>"),
                        Map.of("locale", "en", "title", "English " + titleZh,
                                "summary", "summary", "body", "<p>English body</p>"),
                        Map.of("locale", "ja", "title", "日本語 " + titleZh,
                                "summary", "概要", "body", "<p>日本語の本文</p>"))), token);
        assertThat(patched.statusCode()).withFailMessage("body: %s", patched.body()).isEqualTo(200);
        assertThat(patched.body()).contains(titleZh + "改").contains("\"locale\":\"ja\"");

        // 未发布：门户详情 404、列表不可见
        HttpResponse<String> draftDetail = get("/api/v1/public/articles/" + slug, null);
        assertThat(draftDetail.statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(draftDetail.body()).path("code").asString())
                .isEqualTo("CONTENT.ARTICLE_NOT_FOUND");
        assertThat(get("/api/v1/public/articles?keyword="
                + java.net.URLEncoder.encode(titleZh, java.nio.charset.StandardCharsets.UTF_8), null).body())
                .doesNotContain(slug);

        // 发布后按语言返回
        HttpResponse<String> published = post("/api/v1/admin/articles/" + articleId + "/publish", Map.of(), token);
        assertThat(published.statusCode()).withFailMessage("body: %s", published.body()).isEqualTo(200);
        assertThat(JSON.readTree(published.body()).path("status").asString()).isEqualTo("PUBLISHED");

        HttpResponse<String> detailZh = get("/api/v1/public/articles/" + slug, null);
        assertThat(detailZh.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(detailZh.body()).path("title").asString()).isEqualTo(titleZh + "改");
        HttpResponse<String> detailEn = getWithLanguage("/api/v1/public/articles/" + slug, null, "en");
        assertThat(detailEn.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(detailEn.body()).path("title").asString()).isEqualTo("English " + titleZh);
        HttpResponse<String> detailJa = getWithLanguage("/api/v1/public/articles/" + slug, null, "ja");
        assertThat(detailJa.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(detailJa.body()).path("title").asString()).isEqualTo("日本語 " + titleZh);

        // 列表：关键词 / 分类过滤，单页上限 100
        String encodedTitle = java.net.URLEncoder.encode(titleZh, java.nio.charset.StandardCharsets.UTF_8);
        HttpResponse<String> byKeyword = get("/api/v1/public/articles?keyword=" + encodedTitle, null);
        assertThat(byKeyword.statusCode()).isEqualTo(200);
        assertThat(byKeyword.body()).contains(slug);
        HttpResponse<String> byCategory = get("/api/v1/public/articles?category=" + code, null);
        assertThat(byCategory.statusCode()).isEqualTo(200);
        assertThat(byCategory.body()).contains(slug);
        assertThat(get("/api/v1/public/articles?category=missing-" + suffix, null).body()).doesNotContain(slug);
        assertThat(JSON.readTree(get("/api/v1/public/articles?page%5Bsize%5D=500", null).body())
                .path("page").path("size").asInt()).isEqualTo(100);

        // 门户分类含文章数
        HttpResponse<String> publicCategories = get("/api/v1/public/categories", null);
        assertThat(publicCategories.statusCode()).isEqualTo(200);
        assertThat(publicCategories.body()).contains(code);
        JsonNode categoryNode = null;
        for (JsonNode node : JSON.readTree(publicCategories.body())) {
            if (code.equals(node.path("code").asString())) {
                categoryNode = node;
            }
        }
        assertThat(categoryNode).withFailMessage("body: %s", publicCategories.body()).isNotNull();
        assertThat(categoryNode.path("articleCount").asInt()).isGreaterThanOrEqualTo(1);

        // 下线后重新 404
        assertThat(post("/api/v1/admin/articles/" + articleId + "/unpublish", Map.of(), token).statusCode())
                .isEqualTo(200);
        assertThat(get("/api/v1/public/articles/" + slug, null).statusCode()).isEqualTo(404);
    }

    @Test
    void tagCreationAndReplacement() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String categoryId = createCategory(token, "tag-cat-" + suffix, "标签分类" + suffix);
        String slug = "tag-post-" + suffix;
        String articleId = createArticle(token, slug, categoryId, "标签文章" + suffix);
        String tagCode1 = "tag-a-" + suffix;
        String tagCode2 = "tag-b-" + suffix;
        String tag1 = createTag(token, tagCode1);
        String tag2 = createTag(token, tagCode2);

        // 重复标签 code → 409
        HttpResponse<String> duplicate = post("/api/v1/admin/tags", Map.of("code", tagCode1), token);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).path("code").asString())
                .isEqualTo("CONTENT.TAG_CODE_TAKEN");

        // 标签整体替换
        assertThat(put("/api/v1/admin/articles/" + articleId + "/tags",
                Map.of("tagIds", java.util.List.of(tag1)), token).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/admin/articles/" + articleId + "/tags", token).body()).contains(tagCode1);
        assertThat(put("/api/v1/admin/articles/" + articleId + "/tags",
                Map.of("tagIds", java.util.List.of(tag1, tag2)), token).statusCode()).isEqualTo(204);
        JsonNode replaced = JSON.readTree(get("/api/v1/admin/articles/" + articleId + "/tags", token).body());
        assertThat(replaced).hasSize(2);

        // 未发布前公开标签计数为 0；发布后可见
        assertThat(post("/api/v1/admin/articles/" + articleId + "/publish", Map.of(), token).statusCode())
                .isEqualTo(200);
        HttpResponse<String> publicTags = get("/api/v1/public/tags", null);
        assertThat(publicTags.statusCode()).isEqualTo(200);
        JsonNode tagNode = null;
        for (JsonNode node : JSON.readTree(publicTags.body())) {
            if (tagCode1.equals(node.path("code").asString())) {
                tagNode = node;
            }
        }
        assertThat(tagNode).withFailMessage("body: %s", publicTags.body()).isNotNull();
        assertThat(tagNode.path("articleCount").asInt()).isGreaterThanOrEqualTo(1);

        assertThat(get("/api/v1/public/articles?tag=" + tagCode1, null).body()).contains(slug);
        assertThat(get("/api/v1/public/articles?tag=missing-" + suffix, null).body()).doesNotContain(slug);

        // 删除标签后不再出现在标签列表
        assertThat(delete("/api/v1/admin/tags/" + tag2, token).statusCode()).isEqualTo(204);
        assertThat(get("/api/v1/admin/tags", token).body()).doesNotContain(tagCode2);
    }

    @Test
    void sitemapIncludesPublishedArticles() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String categoryId = createCategory(token, "seo-cat-" + suffix, "SEO 分类" + suffix);
        String slug = "seo-post-" + suffix;
        String articleId = createArticle(token, slug, categoryId, "SEO 文章" + suffix);
        assertThat(post("/api/v1/admin/articles/" + articleId + "/publish", Map.of(), token).statusCode())
                .isEqualTo(200);

        HttpResponse<String> sitemap = get("/sitemap.xml", null);
        assertThat(sitemap.statusCode()).isEqualTo(200);
        String baseUrl = JSON.readTree(get("/api/v1/public/site", null).body()).path("baseUrl").asString();
        assertThat(sitemap.body()).contains(baseUrl + "/zh-CN/articles/" + slug);
        assertThat(sitemap.body()).contains(baseUrl + "/en/articles/" + slug);
        assertThat(sitemap.body()).contains("hreflang=\"en\"");
    }

    @Test
    void articleStatusCodesAndValidation() throws Exception {
        String token = accessToken();
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 未认证 → 401
        assertThat(get("/api/v1/admin/articles", null).statusCode()).isEqualTo(401);

        // 校验失败：缺 slug / 空译文 / 非法 slug → 422
        assertThat(post("/api/v1/admin/articles", Map.of(
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "无 slug"))), token)
                .statusCode()).isEqualTo(422);
        assertThat(post("/api/v1/admin/articles", Map.of(
                "slug", "invalid-" + suffix, "translations", java.util.List.of()), token).statusCode())
                .isEqualTo(422);
        assertThat(post("/api/v1/admin/articles", Map.of(
                "slug", "Invalid Slug", "translations",
                java.util.List.of(Map.of("locale", "zh-CN", "title", "标题"))), token).statusCode())
                .isEqualTo(422);

        // 不存在 → 404
        assertThat(patch("/api/v1/admin/articles/" + UUID.randomUUID(), Map.of(
                "slug", "missing-" + suffix, "translations",
                java.util.List.of(Map.of("locale", "zh-CN", "title", "标题"))), token).statusCode())
                .isEqualTo(404);
        assertThat(post("/api/v1/admin/articles/" + UUID.randomUUID() + "/publish", Map.of(), token).statusCode())
                .isEqualTo(404);

        // 重复 slug → 409
        String categoryId = createCategory(token, "code-cat-" + suffix, "状态码分类" + suffix);
        String slug = "code-post-" + suffix;
        createArticle(token, slug, categoryId, "状态码文章" + suffix);
        HttpResponse<String> duplicate = post("/api/v1/admin/articles", Map.of(
                "slug", slug,
                "translations", java.util.List.of(Map.of("locale", "zh-CN", "title", "重复"))), token);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(duplicate.body()).path("code").asString()).isEqualTo("CONTENT.SLUG_TAKEN");
        assertThat(JSON.readTree(duplicate.body()).path("detail").asString()).isEqualTo("该路径已被占用");

        // 无权限角色 → 403
        String deptId = JSON.readTree(get("/api/v1/auth/me", token).body()).path("deptId").asString();
        String username = "it-content-norole-" + suffix;
        assertThat(post("/api/v1/admin/users", Map.of(
                "username", username, "password", "Integration@12345", "nickname", "Content No Role",
                "deptId", deptId), token).statusCode()).isEqualTo(201);
        String viewerToken = JSON.readTree(post("/api/v1/auth/login",
                Map.of("username", username, "password", "Integration@12345"), null).body())
                .path("accessToken").asString();
        assertThat(get("/api/v1/admin/articles", viewerToken).statusCode()).isEqualTo(403);
    }

    // ---------- 辅助 ----------

    private String createCategory(String token, String code, String name) throws Exception {
        HttpResponse<String> created = post("/api/v1/admin/categories", Map.of(
                "code", code, "sortNo", 10, "status", "ACTIVE",
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "name", name, "description", "描述"),
                        Map.of("locale", "en", "name", "Category " + code))), token);
        assertThat(created.statusCode()).withFailMessage("category body: %s", created.body()).isEqualTo(201);
        return JSON.readTree(created.body()).path("id").asString();
    }

    private String createArticle(String token, String slug, String categoryId, String titleZh) throws Exception {
        HttpResponse<String> created = post("/api/v1/admin/articles", Map.of(
                "slug", slug,
                "categoryId", categoryId,
                "translations", java.util.List.of(
                        Map.of("locale", "zh-CN", "title", titleZh, "summary", "摘要" + titleZh,
                                "body", "<p>中文正文</p>"),
                        Map.of("locale", "en", "title", "English " + titleZh, "summary", "summary",
                                "body", "<p>English body</p>"))), token);
        assertThat(created.statusCode()).withFailMessage("article body: %s", created.body()).isEqualTo(201);
        return JSON.readTree(created.body()).path("id").asString();
    }

    private String createTag(String token, String code) throws Exception {
        HttpResponse<String> created = post("/api/v1/admin/tags", Map.of("code", code), token);
        assertThat(created.statusCode()).withFailMessage("tag body: %s", created.body()).isEqualTo(201);
        return JSON.readTree(created.body()).path("id").asString();
    }

    private void assertEqualsStatus(HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).withFailMessage("body: %s", response.body()).isEqualTo(expected);
    }

    private String uploadPublicFile(String token) throws Exception {
        String boundary = "----vane" + UUID.randomUUID();
        byte[] png = java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
        byte[] body = concat(
                ("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"it.png\"\r\n"
                        + "Content-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                png,
                ("\r\n--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"visibility\"\r\n\r\nPUBLIC\r\n"
                        + "--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v1/files"))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).withFailMessage("upload body: %s", response.body()).isEqualTo(201);
        return JSON.readTree(response.body()).path("id").asString();
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }

    /** 生成 TOTP（RFC 6238，30 秒步长、6 位、SHA1）。offsetSteps 用于取相邻时间步。 */
    private String totp(String base32Secret, int offsetSteps) throws Exception {
        byte[] key = base32Decode(base32Secret);
        long step = System.currentTimeMillis() / 1000 / 30 + offsetSteps;
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (step & 0xFF);
            step >>= 8;
        }
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"));
        byte[] hash = mac.doFinal(data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        return String.format("%06d", binary % 1_000_000);
    }

    private byte[] base32Decode(String encoded) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : encoded.toCharArray()) {
            int value = alphabet.indexOf(Character.toUpperCase(c));
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private int count(String fromClause) {
        return jdbc.queryForObject("select count(*) from " + fromClause, Integer.class);
    }

    private String accessToken() throws Exception {
        return JSON.readTree(post("/api/v1/auth/login",
                Map.of("username", ADMIN, "password", ADMIN_PASSWORD), null).body())
                .path("accessToken").asString();
    }

    private HttpResponse<String> postMultipart(String path, String csv, String token) throws Exception {
        String boundary = "----vane-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"recipients.csv\"\r\n"
                + "Content-Type: text/csv; charset=UTF-8\r\n\r\n"
                + csv + "\r\n--" + boundary + "--\r\n";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(30))
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, java.nio.charset.StandardCharsets.UTF_8));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postWithCallbackToken(String path, Object body, String callbackToken)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-SMS-Callback-Token", callbackToken)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)))
                .build();
        return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .GET();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, Object body, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> delete(String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .DELETE();
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> patch(String path, Object body, String token) throws Exception {
        return sendWithBody("PATCH", path, body, token, null);
    }

    private HttpResponse<String> put(String path, Object body, String token) throws Exception {
        return sendWithBody("PUT", path, body, token, null);
    }

    private HttpResponse<String> getWithLanguage(String path, String token, String language) throws Exception {
        return sendWithBody("GET", path, null, token, language);
    }

    private HttpResponse<String> sendWithBody(String method, String path, Object body, String token,
                                              String language) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .timeout(Duration.ofSeconds(20))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (language != null) {
            builder.header("Accept-Language", language);
        }
        switch (method) {
            case "PATCH" -> builder.method("PATCH",
                    HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));
            default -> builder.GET();
        }
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}

































