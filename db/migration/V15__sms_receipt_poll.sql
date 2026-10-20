-- V15: 回执轮询（ADR-022 P3）：发送日志保存号码密文供厂商查询回执；登记轮询任务。
ALTER TABLE sys_sms_log ADD COLUMN phone_cipher text;
ALTER TABLE sys_sms_log ADD COLUMN phone_key_version text;

INSERT INTO sys_job (job_code, name_key, handler, cron, params, status, concurrency)
VALUES ('sms-receipt-poll', 'job.smsReceiptPoll', 'sms-receipt-poll', '0 */10 * * * *', NULL, 'ACTIVE', 'FORBID');
