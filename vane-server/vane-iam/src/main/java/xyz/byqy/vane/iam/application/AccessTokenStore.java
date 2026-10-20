package xyz.byqy.vane.iam.application;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** access 令牌的服务端状态（03 §14）：只存哈希，可即时撤销。 */
public interface AccessTokenStore {

    void store(String tokenHash, UUID userId, Duration ttl);

    Optional<UUID> resolve(String tokenHash);

    void revoke(String tokenHash);
}
