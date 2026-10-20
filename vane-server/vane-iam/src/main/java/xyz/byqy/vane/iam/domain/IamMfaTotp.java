package xyz.byqy.vane.iam.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** iam_mfa_totp（05 §4.7）：secret 以 AES-256-GCM 密文存储。 */
@Entity
@Table(name = "iam_mfa_totp")
public class IamMfaTotp {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "secret_cipher", nullable = false)
    private String secretCipher;

    @Column(name = "key_version", nullable = false)
    private int keyVersion;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IamMfaTotp() {
    }

    public static IamMfaTotp create(UUID id, UUID userId, String secretCipher, int keyVersion) {
        IamMfaTotp totp = new IamMfaTotp();
        totp.id = id;
        totp.userId = userId;
        totp.secretCipher = secretCipher;
        totp.keyVersion = keyVersion;
        totp.createdAt = Instant.now();
        totp.updatedAt = totp.createdAt;
        return totp;
    }

    public void confirm(Instant now) {
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    /** 记录已使用的时间步，防同一验证码重放。 */
    public void markUsedStep(long step, Instant now) {
        this.lastUsedStep = step;
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Long getLastUsedStep() {
        return lastUsedStep;
    }

    public boolean isConfirmed() {
        return confirmedAt != null;
    }
}
