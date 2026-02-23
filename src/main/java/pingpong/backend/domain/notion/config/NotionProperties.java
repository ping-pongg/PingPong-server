package pingpong.backend.domain.notion.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "notion")
public class NotionProperties {
    @NotBlank
    private String clientId;
    @NotBlank
    private String clientSecret;
    @NotBlank
    private String apiBaseUrl;
    @NotBlank
    private String oauthTokenPath;
    @NotBlank
    private String notionVersion;
    @NotBlank
    private String redirectUri;
}
