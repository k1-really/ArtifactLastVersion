package org.example.interview.repository;

import org.example.interview.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {
    Optional<ArtifactEntity> findByGroupIdAndArtifactId(String groupId, String artifactId);
}
