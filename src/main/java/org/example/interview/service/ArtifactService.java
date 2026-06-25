package org.example.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.interview.dto.ArtifactResponse;
import org.example.interview.entity.ArtifactEntity;
import org.example.interview.repository.ArtifactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;
    private final AetherMetadataFetcher metadataFetcher;

    @Value("${cache.refresh-hours:24}")
    private int refreshHours;

    @Transactional
    public ArtifactResponse getArtifactInfo(String groupId, String artifactId) {
        Optional<ArtifactEntity> cached = artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId);
        LocalDateTime now = LocalDateTime.now();

        if (cached.isPresent()) {
            ArtifactEntity entity = cached.get();
            LocalDateTime lastUpdated = entity.getLastUpdated();
            if (lastUpdated.plusHours(refreshHours).isAfter(now)) {
                log.debug("Cache hit for {}:{}", groupId, artifactId);
                return mapToResponse(entity);
            } else {
                log.debug("Cache expired for {}:{}", groupId, artifactId);
            }
        }

        log.info("Fetching from remote for {}:{}", groupId, artifactId);
        ArtifactResponse remoteData = metadataFetcher.fetchMetadata(groupId, artifactId);

        ArtifactEntity entity = cached.orElse(ArtifactEntity.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .build());
        entity.setVersion(remoteData.getVersion());
        entity.setSizeBytes(remoteData.getSizeBytes());
        entity.setSha256(remoteData.getSha256());
        entity.setLastUpdated(now);
        artifactRepository.save(entity);

        return remoteData;
    }

    private ArtifactResponse mapToResponse(ArtifactEntity entity) {
        String hash = entity.getSha256();
        String algorithm;
        if (hash != null) {
            if (hash.length() == 64) {
                algorithm = "SHA256";
            } else if (hash.length() == 40) {
                algorithm = "SHA1";
            } else {
                algorithm = "UNKNOWN";
            }
        } else {
            algorithm = "UNKNOWN";
        }
        return ArtifactResponse.builder()
                .version(entity.getVersion())
                .sizeBytes(entity.getSizeBytes())
                .sha256(hash)
                .hashAlgorithm(algorithm)
                .build();
    }
}