package xyz.byqy.vane.iam.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface IamMfaTotpRepository extends JpaRepository<IamMfaTotp, UUID> {

    Optional<IamMfaTotp> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
