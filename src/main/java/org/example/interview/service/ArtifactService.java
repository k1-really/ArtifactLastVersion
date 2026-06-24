package org.example.interview.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.interview.dto.ArtifactResponse;
import org.example.interview.entity.ArtifactEntity;
import org.example.interview.repository.ArtifactRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Сервис для работы с артефактами
 * Реализует основную бизнес-логику: получение из кэша или удаленного репозитория
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    @Value("${maven.repository.url:https://repo1.maven.org/maven2}")
    private String repositoryUrl;

    @Value("${cache.refresh-hours:24}")
    private Integer cacheRefreshHours;

    private final ArtifactRepository artifactRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Основной метод получения информации об артефакте
     * Реализует кэширование с проверкой актуальности
     */
    public ArtifactResponse getArtifactInfo(String groupId, String artifactId) {
        log.info("Getting artifact info for groupId={}, artifactId={}", groupId, artifactId);

        // 1. Пытаемся найти запись в БД
        Optional<ArtifactEntity> cached = artifactRepository
                .findByGroupIdAndArtifactId(groupId, artifactId);

        // 2. Если запись существует и актуальна, возвращаем из кэша
        if (cached.isPresent() && isCacheValid(cached.get())) {
            log.info("Returning cached artifact: {}", cached.get().getVersion());
            return mapToResponse(cached.get());
        }

        // 3. Иначе загружаем из удаленного репозитория
        log.info("Cache expired or not found, fetching from remote repository");
        ArtifactEntity freshEntity = fetchFromRemoteRepository(groupId, artifactId);

        // 4. Сохраняем или обновляем в БД
        ArtifactEntity savedEntity = saveOrUpdate(freshEntity);

        return mapToResponse(savedEntity);
    }

    /**
     * Проверяет, актуален ли кэш (был ли обновлен в текущие сутки)
     */
    private boolean isCacheValid(ArtifactEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastUpdated = entity.getLastUpdated();

        // Проверяем, что прошло меньше cacheRefreshHours часов с последнего обновления
        java.time.Duration duration = java.time.Duration.between(lastUpdated, now);
        boolean isValid = duration.toHours() < cacheRefreshHours;

        log.debug("Cache validity: {}, hours since update: {}", isValid, duration.toHours());
        return isValid;
    }

    /**
     * Загружает данные из удаленного Maven-репозитория
     * Использует maven-metadata.xml для получения информации
     */
    private ArtifactEntity fetchFromRemoteRepository(String groupId, String artifactId) {
        try {
            // Формируем URL для maven-metadata.xml
            String path = groupId.replace('.', '/') + "/" + artifactId + "/";
            String metadataUrl = repositoryUrl + "/" + path + "maven-metadata.xml";

            log.info("Fetching metadata from: {}", metadataUrl);

            // Загружаем XML
            String xmlContent = restTemplate.getForObject(metadataUrl, String.class);
            if (xmlContent == null) {
                throw new RuntimeException("Empty response from repository");
            }

            // Парсим XML для получения последней версии
            String latestVersion = parseLatestVersion(xmlContent);
            log.info("Latest version: {}", latestVersion);

            // Формируем URL для конкретного JAR файла
            String jarUrl = repositoryUrl + "/" + path + latestVersion + "/" +
                    artifactId + "-" + latestVersion + ".jar";

            log.info("Fetching JAR from: {}", jarUrl);

            // Скачиваем JAR для получения размера и SHA256
            byte[] jarContent = restTemplate.getForObject(jarUrl, byte[].class);
            if (jarContent == null) {
                throw new RuntimeException("Empty JAR content");
            }

            // Вычисляем SHA256
            String sha256 = calculateSHA256(jarContent);
            long sizeBytes = jarContent.length;

            // Создаем сущность
            return ArtifactEntity.builder()
                    .groupId(groupId)
                    .artifactId(artifactId)
                    .version(latestVersion)
                    .sizeBytes(sizeBytes)
                    .sha256(sha256)
                    .lastUpdated(LocalDateTime.now())
                    .build();

        } catch (HttpClientErrorException.NotFound e) {
            log.error("Artifact not found: {}", e.getMessage());
            throw new RuntimeException("Artifact not found: " + groupId + ":" + artifactId, e);
        } catch (ResourceAccessException e) {
            log.error("Timeout or network error: {}", e.getMessage());
            throw new RuntimeException("Repository timeout or unavailable", e);
        } catch (Exception e) {
            log.error("Error fetching artifact: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to fetch artifact info", e);
        }
    }

    /**
     * Парсит XML для получения последней версии артефакта
     * Использует XPath для навигации по XML
     */
    private String parseLatestVersion(String xmlContent) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(
                    new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8))));

            XPath xPath = XPathFactory.newInstance().newXPath();

            // XPath выражение для получения последней версии
            String expression = "/metadata/versioning/latest/text()";
            NodeList nodeList = (NodeList) xPath.compile(expression)
                    .evaluate(document, XPathConstants.NODESET);

            if (nodeList.getLength() == 0) {
                // Fallback: берем последнюю версию из списка versions
                expression = "/metadata/versioning/versions/version[last()]/text()";
                nodeList = (NodeList) xPath.compile(expression)
                        .evaluate(document, XPathConstants.NODESET);
            }

            if (nodeList.getLength() == 0) {
                throw new RuntimeException("No version found in metadata");
            }

            return nodeList.item(0).getTextContent();

        } catch (Exception e) {
            log.error("Error parsing XML: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to parse maven-metadata.xml", e);
        }
    }

    /**
     * Вычисляет SHA256 хэш для массива байт
     */
    private String calculateSHA256(byte[] content) {
        try {
            java.security.MessageDigest digest =
                    java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);

            // Конвертируем байты в hex строку
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Сохраняет или обновляет запись в БД
     * Если запись существует - обновляем, иначе создаем новую
     */
    private ArtifactEntity saveOrUpdate(ArtifactEntity entity) {
        Optional<ArtifactEntity> existing = artifactRepository
                .findByGroupIdAndArtifactId(entity.getGroupId(), entity.getArtifactId());

        if (existing.isPresent()) {
            // Обновляем существующую запись
            ArtifactEntity existingEntity = existing.get();
            existingEntity.setVersion(entity.getVersion());
            existingEntity.setSizeBytes(entity.getSizeBytes());
            existingEntity.setSha256(entity.getSha256());
            existingEntity.setLastUpdated(entity.getLastUpdated());
            return artifactRepository.save(existingEntity);
        } else {
            // Создаем новую запись
            return artifactRepository.save(entity);
        }
    }

    /**
     * Маппинг сущности в DTO для ответа API
     */
    private ArtifactResponse mapToResponse(ArtifactEntity entity) {
        return ArtifactResponse.builder()
                .version(entity.getVersion())
                .sizeBytes(entity.getSizeBytes())
                .sha256(entity.getSha256())
                .build();
    }
}