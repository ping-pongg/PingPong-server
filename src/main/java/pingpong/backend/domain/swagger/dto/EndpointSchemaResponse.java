package pingpong.backend.domain.swagger.dto;

import java.util.List;

public record EndpointSchemaResponse(
        Long id,
        String path,
        String method,
        String summary,
        String description,
        List<ParameterInfo> parameters,
        RequestBodyInfo requestBody,
        List<ResponseInfo> responses
) {
    public record ParameterInfo(
            String name,
            String inType,
            Boolean required,
            String description,
            String schemaJson
    ) {}

    public record RequestBodyInfo(
            String mediaType,
            String schemaJson
    ) {}

    public record ResponseInfo(
            String statusCode,
            String mediaType,
            String schemaJson
    ) {}
}
