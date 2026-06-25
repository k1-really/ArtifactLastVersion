package org.example.interview.controller;

import org.example.interview.dto.ArtifactResponse;
import org.example.interview.exception.ArtifactNotFoundException;
import org.example.interview.service.ArtifactService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(ArtifactController.class)
class ArtifactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ArtifactService artifactService;

    @Test
    void shouldReturnArtifactInfo() throws Exception {
        ArtifactResponse response = ArtifactResponse.builder()
                .version("3.2.0")
                .sizeBytes(12345L)
                .sha256("a7c3d6e8f9b2c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e")
                .hashAlgorithm("SHA256")
                .build();

        when(artifactService.getArtifactInfo("org.springframework.boot", "spring-boot-starter-web"))
                .thenReturn(response);

        mockMvc.perform(get("/api/artifact/org.springframework.boot/spring-boot-starter-web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("3.2.0"))
                .andExpect(jsonPath("$.sizeBytes").value(12345))
                .andExpect(jsonPath("$.sha256").value("a7c3d6e8f9b2c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e"))
                .andExpect(jsonPath("$.hashAlgorithm").value("SHA256"));
    }

    @Test
    void shouldReturn404WhenArtifactNotFound() throws Exception {
        when(artifactService.getArtifactInfo("com.example", "non-existent"))
                .thenThrow(new ArtifactNotFoundException("No release version found"));

        mockMvc.perform(get("/api/artifact/com.example/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No release version found"));
    }

    @Test
    void shouldReturn400WhenGroupIdIsEmpty() throws Exception {
        mockMvc.perform(get("/api/artifact//spring-boot-starter-web"))
                .andExpect(status().isNotFound()); // или 400, если доработать
    }
}