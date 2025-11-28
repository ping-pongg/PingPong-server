package pingpong.backend.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
@Slf4j
public class SwaggerConfig {

    @Value("${swagger.servers:}")
    private List<String> swaggerServers;

    @Bean
    public OpenAPI openAPI() {
        final String schemeName = "bearerAuth";
        log.info("Swagger server URL: {}", swaggerServers);

        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("졸업프로젝트 2팀 API")
                        .description("졸업프로젝트 2팀 API 문서")
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(schemeName, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(schemeName));

        if (swaggerServers != null && !swaggerServers.isEmpty()) {
            openAPI.setServers(swaggerServers.stream().map(u -> new Server().url(u)).toList());
        }
        return openAPI;
    }
}

