package org.example.interview.service;


import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.example.interview.dto.ArtifactResponse;
import org.example.interview.exception.ArtifactNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AetherMetadataFetcherTest {

    @Autowired
    private AetherMetadataFetcher fetcher;

    private WireMockServer wireMockServer;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("maven.repository.url", () -> "http://localhost:8081/maven2");
    }

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8081);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8081);
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldFetchMetadataWithSha256() {
        String groupId = "org.test";
        String artifactId = "test-artifact";
        String version = "1.0.0";
        String metadataXml = """
                <metadata>
                  <groupId>org.test</groupId>
                  <artifactId>test-artifact</artifactId>
                  <versioning>
                    <release>1.0.0</release>
                  </versioning>
                </metadata>
                """;
        String sha256 = "a".repeat(64);

        stubFor(get(urlEqualTo("/maven2/org/test/test-artifact/maven-metadata.xml"))
                .willReturn(aResponse().withStatus(200).withBody(metadataXml)));
        stubFor(head(urlEqualTo("/maven2/org/test/test-artifact/1.0.0/test-artifact-1.0.0.jar"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Length", "1234")));
        stubFor(get(urlEqualTo("/maven2/org/test/test-artifact/1.0.0/test-artifact-1.0.0.jar.sha256"))
                .willReturn(aResponse().withStatus(200).withBody(sha256)));

        ArtifactResponse response = fetcher.fetchMetadata(groupId, artifactId);
        assertThat(response.getVersion()).isEqualTo(version);
        assertThat(response.getSizeBytes()).isEqualTo(1234L);
        assertThat(response.getSha256()).isEqualTo(sha256);
        assertThat(response.getHashAlgorithm()).isEqualTo("SHA256");
    }

    @Test
    void shouldFallbackToSha1WhenSha256Missing() {
        String groupId = "org.test";
        String artifactId = "test-artifact";
        String version = "1.0.0";
        String metadataXml = """
                <metadata>
                  <groupId>org.test</groupId>
                  <artifactId>test-artifact</artifactId>
                  <versioning>
                    <release>1.0.0</release>
                  </versioning>
                </metadata>
                """;
        String sha1 = "b".repeat(40);

        stubFor(get(urlEqualTo("/maven2/org/test/test-artifact/maven-metadata.xml"))
                .willReturn(aResponse().withStatus(200).withBody(metadataXml)));
        stubFor(head(urlEqualTo("/maven2/org/test/test-artifact/1.0.0/test-artifact-1.0.0.jar"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Length", "1234")));
        stubFor(get(urlEqualTo("/maven2/org/test/test-artifact/1.0.0/test-artifact-1.0.0.jar.sha256"))
                .willReturn(aResponse().withStatus(404)));
        stubFor(get(urlEqualTo("/maven2/org/test/test-artifact/1.0.0/test-artifact-1.0.0.jar.sha1"))
                .willReturn(aResponse().withStatus(200).withBody(sha1)));

        ArtifactResponse response = fetcher.fetchMetadata(groupId, artifactId);
        assertThat(response.getVersion()).isEqualTo(version);
        assertThat(response.getSizeBytes()).isEqualTo(1234L);
        assertThat(response.getSha256()).isEqualTo(sha1);
        assertThat(response.getHashAlgorithm()).isEqualTo("SHA1");
    }

    @Test
    void shouldThrowNotFoundExceptionWhenMetadataMissing() {
        stubFor(get(urlEqualTo("/maven2/com/example/missing/maven-metadata.xml"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> fetcher.fetchMetadata("com.example", "missing"))
                .isInstanceOf(ArtifactNotFoundException.class)
                .hasMessageContaining("No release version found");
    }
}