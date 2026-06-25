package org.example.interview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;


@Entity // Указывает, что это JPA сущность
@Table(name = "artifacts", // Имя таблицы в БД
        uniqueConstraints = { // Уникальное ограничение - одна запись на пару groupId:artifactId
                @UniqueConstraint(columnNames = {"group_id", "artifact_id"})
        })
@Data
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, length = 255)
    private String groupId;

    @Column(name = "artifact_id", nullable = false, length = 255)
    private String artifactId;

    @Column(nullable = false, length = 100)
    private String version;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
