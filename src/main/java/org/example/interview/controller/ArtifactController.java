package org.example.interview.controller;
/*
Цель.
===============
За 1 час ожидается рабочий прототип с корректной архитектурой и основным потоком. Обработка всех edge-cases и тесты — опциональны.
        Главное — показать, как вы прорабатываете требования, проектируете, используете инструменты разработки и тестируете за собой.
Можно задавать любые вопросы по задаче и пользоваться любыми инструментами, которыми вы будете использовать в реальной работе.

Задача.
===============
Разработать REST API сервис, возвращающий информацию о последней (по дате) доступной release версии JAR-артефакта в удалённом
Maven-репозитории (по умолчанию Maven Central).

Входные данные: groupId и artifactId.
Формат запроса: GET /api/artifact/{groupId}/{artifactId}

Ответ: JSON с полями:
        - version (строка)
  - sizeBytes (число, размер файла)
  - sha256 (строка, хэш-сумма артефакта)

Логика работы:
        1. При первом запросе данные загружаются из удалённого репозитория и сохраняются в локальную SQLite.
2. При повторных запросах проверяется дата последнего обновления записи.
Если запись была создана или обновлена в текущие сутки, данные возвращаются из БД.
Иначе выполняется повторный запрос к репозиторию, данные обновляются в БД и возвращаются клиенту.
        3. URL репозитория, путь к SQLite-файлу и параметры кэша должны выноситься во внешнюю конфигурацию (application.yml / env vars)
без пересборки приложения.

Нефункциональные требования.
        ===============
        - Java 11+.
        - Любая IDE на выбор.
        - Любой фреймворк на выбор.
        - Можно использовать maven илм gradle.
- Приложение должно запускаться на Linux и Windows.
- Предусмотрите Dockerfile и краткую инструкцию по запуску.
- Обеспечьте базовую обработку ошибок: невалидные координаты, отсутствие артефакта, таймауты репозитория.
        - Для демонстрации допускается использование SQLite или H2, но архитектура должна позволять лёгкий переход на любую другую реляционную БД.
- Должна быть возможность перезагрузки приложения без потери данных.*/

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.interview.dto.ArtifactResponse;
import org.example.interview.service.ArtifactService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifact")
@RequiredArgsConstructor
@Slf4j
public class ArtifactController {

    private final ArtifactService artifactService;

    /**
     * GET эндпоинт для получения информации об артефакте
     * Пример запроса: GET /api/artifact/org.springframework.boot/spring-boot-starter-web
     */
    @GetMapping("/{groupId}/{artifactId}")
    public ResponseEntity<ArtifactResponse> getArtifactInfo(
            @PathVariable @NotBlank String groupId,
            @PathVariable @NotBlank String artifactId) {

        log.info("Received request for groupId={}, artifactId={}", groupId, artifactId);

        try {
            ArtifactResponse response = artifactService.getArtifactInfo(groupId, artifactId);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Error processing request: {}", e.getMessage());
            throw e;
        }
    }
}
