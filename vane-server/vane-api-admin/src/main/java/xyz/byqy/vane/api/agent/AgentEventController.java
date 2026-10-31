package xyz.byqy.vane.api.agent;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import xyz.byqy.vane.audit.application.AgentEventPage;
import xyz.byqy.vane.audit.application.AgentEventQueryService;

/**
 * 审计事件的只读投影（ADR-030 P1）。
 *
 * <p>供代理在被唤醒或定时轮询时「自上次以来发生了什么」，支持 since/until 时间窗与游标推进。</p>
 */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentEventController {

    private final AgentEventQueryService events;

    public AgentEventController(AgentEventQueryService events) {
        this.events = events;
    }

    @GetMapping("/events")
    @PreAuthorize("hasAuthority(T(xyz.byqy.vane.iam.domain.PermissionCatalog).AUDIT_OPER_LOG.code)")
    public AgentEventPage events(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant until,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return events.events(since, until, cursor, limit);
    }
}
