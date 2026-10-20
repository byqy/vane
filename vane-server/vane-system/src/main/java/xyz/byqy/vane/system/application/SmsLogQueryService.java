package xyz.byqy.vane.system.application;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import xyz.byqy.vane.system.infrastructure.SmsLogRepository;
import xyz.byqy.vane.system.infrastructure.SmsLogRepository.PageResult;

/** 短信发送日志查询（ADR-022）：分页与按模板/服务商/结果/时间过滤。 */
@Service
public class SmsLogQueryService {

    private final SmsLogRepository logs;

    public SmsLogQueryService(SmsLogRepository logs) {
        this.logs = logs;
    }

    @Transactional(readOnly = true)
    public PageResult<SmsLogRepository.SmsLogView> page(String templateCode, String providerCode, String result,
                                                        Instant from, Instant to, int pageNumber, int pageSize) {
        return logs.page(templateCode, providerCode, result, from, to, pageNumber, pageSize);
    }
}
