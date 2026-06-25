package org.example.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.MetadataRequest;
import org.eclipse.aether.resolution.MetadataResult;
import org.example.interview.dto.ArtifactResponse;
import org.example.interview.exception.ArtifactNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;


@Component
@RequiredArgsConstructor
@Slf4j
public class AetherMetadataFetcher {

    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession session;

    @Value("${maven.repository.url:https://repo1.maven.org/maven2}")
    private String repoUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final Pattern SHA1_PATTERN = Pattern.compile("^[a-fA-F0-9]{40}$");

    public ArtifactResponse fetchMetadata(String groupId, String artifactId) {
        log.info("Fetching metadata for {}:{}", groupId, artifactId);

        String releaseVersion = getReleaseVersionUsingAether(groupId, artifactId);
        if (releaseVersion == null || releaseVersion.isEmpty()) {
            throw new ArtifactNotFoundException("No release version found for " + groupId + ":" + artifactId);
        }
        releaseVersion = releaseVersion.trim();
        log.info("Resolved release version: {}", releaseVersion);

        String jarUrl = buildArtifactUrl(groupId, artifactId, releaseVersion, "jar");
        String sha256Url = jarUrl + ".sha256";

        log.info("JAR URL: {}", jarUrl);
        log.info("SHA256 URL: {}", sha256Url);

        long size = getContentLength(jarUrl);
        Map.Entry<String, String> hashResult = getHash(sha256Url);
        String hash = hashResult.getKey();
        String algorithm = hashResult.getValue();

        log.info("Successfully fetched metadata for {}:{} -> version={}, size={}, hash={}, algorithm={}",
                groupId, artifactId, releaseVersion, size, hash, algorithm);

        return ArtifactResponse.builder()
                .version(releaseVersion)
                .sizeBytes(size)
                .sha256(hash)
                .hashAlgorithm(algorithm)
                .build();
    }

    private String getReleaseVersionUsingAether(String groupId, String artifactId) {
        try {
            Metadata metadata = new DefaultMetadata(groupId, artifactId, "maven-metadata.xml", Metadata.Nature.RELEASE);
            RemoteRepository remoteRepo = new RemoteRepository.Builder("central", "default", repoUrl).build();

            MetadataRequest request = new MetadataRequest();
            request.setMetadata(metadata);
            request.setRepository(remoteRepo);
            request.setDeleteLocalCopyIfMissing(true);

            MetadataResult result = repositorySystem.resolveMetadata(session, Collections.singletonList(request)).get(0);

            if (!result.isResolved()) {
                log.warn("Aether could not resolve metadata for {}:{}", groupId, artifactId);
                return null;
            }

            try (InputStream is = result.getMetadata().getFile().toPath().toUri().toURL().openStream()) {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                XMLStreamReader reader = factory.createXMLStreamReader(is);
                String version = null;
                boolean insideRelease = false;

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamConstants.START_ELEMENT) {
                        String localName = reader.getLocalName();
                        if ("release".equals(localName)) {
                            insideRelease = true;
                        }
                    } else if (event == XMLStreamConstants.CHARACTERS && insideRelease) {
                        version = reader.getText().trim();
                        break;
                    } else if (event == XMLStreamConstants.END_ELEMENT) {
                        String localName = reader.getLocalName();
                        if ("release".equals(localName)) {
                            insideRelease = false;
                        }
                    }
                }
                reader.close();
                log.debug("Parsed release version: {}", version);
                return version;
            }
        } catch (Exception e) {
            log.error("Error while fetching metadata via Aether for {}:{}", groupId, artifactId, e);
            throw new RuntimeException("Failed to retrieve release version using Aether: " + e.getMessage(), e);
        }
    }

    private String buildArtifactUrl(String groupId, String artifactId, String version, String filename) {
        String path = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "." + filename;
        return repoUrl + "/" + path;
    }

    private long getContentLength(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

            int status = response.statusCode();
            log.debug("HEAD request to {} returned {}", url, status);

            if (status == 404) {
                throw new ArtifactNotFoundException("Artifact JAR not found at " + url);
            }
            if (status != 200) {
                throw new RuntimeException("HEAD request returned " + status + " for " + url);
            }

            String lengthHeader = response.headers().firstValue("Content-Length").orElse(null);
            if (lengthHeader == null) {
                throw new RuntimeException("Missing Content-Length header for " + url);
            }
            return Long.parseLong(lengthHeader);
        } catch (Exception e) {
            log.error("Error getting size from {}", url, e);
            throw new RuntimeException("Failed to retrieve file size from " + url + ": " + e.getMessage(), e);
        }
    }

    private Map.Entry<String, String> getHash(String sha256Url) {
        // Пробуем .sha256
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sha256Url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                String sha = response.body().trim();
                if (SHA256_PATTERN.matcher(sha).matches()) {
                    log.debug("Got SHA256 for {}", sha256Url);
                    return Map.entry(sha, "SHA256");
                }
                log.warn("Invalid SHA256 format for {}, trying SHA1", sha256Url);
            } else if (response.statusCode() != HttpStatus.SC_NOT_FOUND) {
                throw new RuntimeException("GET for SHA256 returned " + response.statusCode() + " for " + sha256Url);
            }
        } catch (Exception e) {
            log.error("Error getting SHA256 from {}, trying SHA1", sha256Url, e);
        }

        // Fallback на .sha1
        String sha1Url = sha256Url.replace(".sha256", ".sha1");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sha1Url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == HttpStatus.SC_OK) {
                String sha1 = response.body().trim();
                if (SHA1_PATTERN.matcher(sha1).matches()) {
                    log.info("Using SHA1 fallback for {}", sha1Url);
                    return Map.entry(sha1, "SHA1");
                } else {
                    throw new RuntimeException("Invalid SHA1 format: " + sha1);
                }
            } else if (response.statusCode() == HttpStatus.SC_NOT_FOUND) {
                throw new ArtifactNotFoundException("Neither SHA256 nor SHA1 file found for " + sha256Url);
            } else {
                throw new RuntimeException("GET for SHA1 returned " + response.statusCode() + " for " + sha1Url);
            }
        } catch (Exception e) {
            log.error("Error getting SHA1 from {}", sha1Url, e);
            throw new RuntimeException("Failed to retrieve checksum from " + sha256Url + " or " + sha1Url, e);
        }
    }
}