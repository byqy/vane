package xyz.byqy.vane.system.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SysMessageTemplateRepository extends JpaRepository<SysMessageTemplate, UUID> {

    List<SysMessageTemplate> findByDeletedAtIsNullOrderByCodeAsc();

    List<SysMessageTemplate> findByChannelAndDeletedAtIsNullOrderByCodeAsc(String channel);

    Optional<SysMessageTemplate> findByCodeAndChannelAndDeletedAtIsNull(String code, String channel);

    Optional<SysMessageTemplate> findByIdAndDeletedAtIsNull(UUID id);
}
