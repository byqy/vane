-- V14: 短信回执/上行/保留期清理（ADR-022 P3）。
-- 清理任务：每日 03:00 删除超过保留期的发送日志、回执、收件人与上行消息。
INSERT INTO sys_job (job_code, name_key, handler, cron, params, status, concurrency)
VALUES ('sms-retention', 'job.smsRetention', 'sms-retention', '0 0 3 * * *', NULL, 'ACTIVE', 'FORBID');
