package xyz.byqy.vane.iam.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IamMfaRecoveryCodeRepository extends JpaRepository<IamMfaRecoveryCode, UUID> {

    List<IamMfaRecoveryCode> findByUserIdAndUsedAtIsNull(UUID userId);

    Optional<IamMfaRecoveryCode> findByUserIdAndCodeHashAndUsedAtIsNull(UUID userId, String codeHash);

    void deleteByUserId(UUID userId);
}
