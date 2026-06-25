package org.example.interview.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactResponse {
    private String version;
    private Long sizeBytes;
    private String sha256;
    private String hashAlgorithm;   // "SHA256" или "SHA1"
}
