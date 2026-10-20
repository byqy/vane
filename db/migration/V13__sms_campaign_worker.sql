-- V13: 短信群发（ADR-022 P2）：活动增加默认语言；登记 worker 调度任务。
ALTER TABLE sys_sms_campaign ADD COLUMN locale text NOT NULL DEFAULT 'zh-CN';

INSERT INTO sys_job (job_code, name_key, handler, cron, params, status, concurrency)
VALUES ('sms-campaign-worker', 'job.smsCampaign', 'sms-campaign', '*/10 * * * * *', NULL, 'ACTIVE', 'ALLOW');
