package pingpong.backend.domain.swagger.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pingpong.backend.domain.swagger.dto.EndpointSchemaResponse;
import pingpong.backend.domain.swagger.service.EndpointService;
import pingpong.backend.global.response.result.SuccessResponse;

@Hidden
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/endpoints")
@Tag(name = "Endpoint API", description = "엔드포인트 스키마 조회 API입니다.")
public class EndpointController {

    private final EndpointService endpointService;

    @GetMapping("/{endpointId}/schema")
    @Operation(
            summary = "엔드포인트 스키마 조회",
            description = "endpointId에 해당하는 엔드포인트의 parameters, requestBody, responses 스키마를 반환합니다."
    )
    public SuccessResponse<EndpointSchemaResponse> getEndpointSchema(@PathVariable Long endpointId) {
        return SuccessResponse.ok(endpointService.getSchema(endpointId));
    }
}
