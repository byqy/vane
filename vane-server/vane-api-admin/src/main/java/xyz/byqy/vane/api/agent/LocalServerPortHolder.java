package xyz.byqy.vane.api.agent;

import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 记录本实例实际监听的端口：批量端点通过本机回环调用自身，避免复制一份业务逻辑。
 *
 * <p>测试用随机端口时也必须拿到真实端口，因此监听 {@link WebServerInitializedEvent}，
 * 而不是读 {@code server.port} 配置。</p>
 */
@Component
public class LocalServerPortHolder implements ApplicationListener<WebServerInitializedEvent> {

    private volatile int port = 8080;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        this.port = event.getWebServer().getPort();
    }

    public int port() {
        return port;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
