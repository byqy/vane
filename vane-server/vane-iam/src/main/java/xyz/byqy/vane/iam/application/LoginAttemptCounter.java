package xyz.byqy.vane.iam.application;

import java.time.Duration;

/** 登录失败计数：按账号与 IP 双维度，带自动过期（01 §10）。 */
public interface LoginAttemptCounter {

    long increment(String key, Duration ttl);

    long count(String key);

    void clear(String key);
}
