package org.example.interview.repository;

import org.example.interview.entity.ArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Репозиторий для работы с ArtifactEntity
 * JpaRepository предоставляет базовые CRUD методы
 * Также добавляем свои методы для проверки актуальности записи
 */
public interface ArtifactRepository extends JpaRepository<ArtifactEntity, Long> {

    /**
     * Найти артефакт по groupId и artifactId
     * Используем Optional для graceful обработки случая "не найдено"
     */
    Optional<ArtifactEntity> findByGroupIdAndArtifactId(String groupId, String artifactId);
}
