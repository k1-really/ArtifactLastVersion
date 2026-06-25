package org.example.interview.service;

import org.example.interview.dto.ArtifactResponse;
import org.example.interview.entity.ArtifactEntity;
import org.example.interview.repository.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ArtifactServiceTest {

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private AetherMetadataFetcher metadataFetcher;

    @InjectMocks
    private ArtifactService artifactService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(artifactService, "refreshHours", 24);
    }

    @Test
    void shouldReturnCachedDataWhenNotExpired() {
        String groupId = "org.test";
        String artifactId = "test-artifact";
        ArtifactEntity entity = ArtifactEntity.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version("1.0.0")
                .sizeBytes(100L)
                .sha256("abc123")
                .lastUpdated(LocalDateTime.now())
                .build();

        when(artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId))
                .thenReturn(Optional.of(entity));

        ArtifactResponse response = artifactService.getArtifactInfo(groupId, artifactId);

        assertThat(response.getVersion()).isEqualTo("1.0.0");
        assertThat(response.getSizeBytes()).isEqualTo(100L);
        assertThat(response.getSha256()).isEqualTo("abc123");
        assertThat(response.getHashAlgorithm()).isEqualTo("UNKNOWN");

        verify(metadataFetcher, never()).fetchMetadata(anyString(), anyString());
        verify(artifactRepository, never()).save(any(ArtifactEntity.class));
    }

    @Test
    void shouldRefreshCacheWhenExpired() {
        String groupId = "org.test";
        String artifactId = "test-artifact";
        ArtifactEntity entity = ArtifactEntity.builder()
                .groupId(groupId)
                .artifactId(artifactId)
                .version("1.0.0")
                .sizeBytes(100L)
                .sha256("abc123")
                .lastUpdated(LocalDateTime.now().minusHours(25))
                .build();

        ArtifactResponse remoteResponse = ArtifactResponse.builder()
                .version("2.0.0")
                .sizeBytes(200L)
                .sha256("def4567890123456789012345678901234567890123456789012345678901234") // 64 символа
                .hashAlgorithm("SHA256")
                .build();

        when(artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId))
                .thenReturn(Optional.of(entity));
        when(metadataFetcher.fetchMetadata(groupId, artifactId))
                .thenReturn(remoteResponse);
        when(artifactRepository.save(any(ArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArtifactResponse response = artifactService.getArtifactInfo(groupId, artifactId);

        assertThat(response.getVersion()).isEqualTo("2.0.0");
        assertThat(response.getSizeBytes()).isEqualTo(200L);
        assertThat(response.getSha256()).isEqualTo(remoteResponse.getSha256());
        assertThat(response.getHashAlgorithm()).isEqualTo("SHA256");

        verify(metadataFetcher).fetchMetadata(groupId, artifactId);
        verify(artifactRepository).save(any(ArtifactEntity.class));
    }

    @Test
    void shouldCreateNewRecordWhenNotCached() {
        String groupId = "org.test";
        String artifactId = "new-artifact";

        ArtifactResponse remoteResponse = ArtifactResponse.builder()
                .version("1.0.0")
                .sizeBytes(500L)
                .sha256("a".repeat(64))
                .hashAlgorithm("SHA256")
                .build();

        when(artifactRepository.findByGroupIdAndArtifactId(groupId, artifactId))
                .thenReturn(Optional.empty());
        when(metadataFetcher.fetchMetadata(groupId, artifactId))
                .thenReturn(remoteResponse);
        when(artifactRepository.save(any(ArtifactEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ArtifactResponse response = artifactService.getArtifactInfo(groupId, artifactId);

        assertThat(response.getVersion()).isEqualTo("1.0.0");
        assertThat(response.getSizeBytes()).isEqualTo(500L);
        assertThat(response.getSha256()).isEqualTo(remoteResponse.getSha256());
        assertThat(response.getHashAlgorithm()).isEqualTo("SHA256");

        verify(artifactRepository).save(any(ArtifactEntity.class));
    }
}