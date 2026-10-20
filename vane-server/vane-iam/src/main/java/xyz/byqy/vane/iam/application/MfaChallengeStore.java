package xyz.byqy.vane.iam.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** 登录第二步的一次性挑战令牌（03 §14：mfaToken，5 分钟、一次性）。 */
public interface MfaChallengeStore {

    void store(String tokenHash, UUID userId, Duration ttl);

    /** 取出即删除，保证一次性。 */
    Optional<UUID> consume(String tokenHash);
}
