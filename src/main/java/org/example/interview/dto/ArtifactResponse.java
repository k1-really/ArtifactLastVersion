package org.example.interview.dto;

import lombok.*;

/*        - version (строка)
  - sizeBytes (число, размер файла)
  - sha256 (строка, хэш-сумма артефакта)*/
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtifactResponse {
    private String version;
    private Long sizeBytes;
    private String sha256;
}
