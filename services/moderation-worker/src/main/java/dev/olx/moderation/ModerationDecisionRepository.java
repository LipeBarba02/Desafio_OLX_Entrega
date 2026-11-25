package dev.olx.moderation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModerationDecisionRepository extends JpaRepository<ModerationDecisionEntity,UUID>{ }
