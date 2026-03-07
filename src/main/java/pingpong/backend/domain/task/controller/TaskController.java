package pingpong.backend.domain.task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pingpong.backend.domain.task.dto.TaskDetailResponse;
import pingpong.backend.domain.task.dto.TaskFlowMappingRequest;
import pingpong.backend.domain.task.dto.TaskFlowMappingResponse;
import pingpong.backend.domain.task.dto.TaskMappedUpdateRequest;
import pingpong.backend.domain.task.dto.TaskResponse;
import pingpong.backend.domain.task.service.TaskService;
import pingpong.backend.global.response.result.SuccessResponse;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/tasks")
@Tag(name = "Task API", description = "Notion primary database와 동기화된 Task를 조회하는 API입니다.")
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/teams/{teamId}")
    @Operation(
            summary = "팀별 Task 목록 조회",
            description = "팀의 Notion primary database와 동기화된 Task 목록을 반환합니다. flowMappingCompleted, status 파라미터로 필터링 가능합니다."
    )
    public SuccessResponse<List<TaskResponse>> getTasksByTeamId(
            @PathVariable Long teamId,
            @RequestParam(required = false) Boolean flowMappingCompleted,
            @RequestParam(required = false) String status
    ) {
        return SuccessResponse.ok(taskService.getTasksByTeamId(teamId, flowMappingCompleted, status));
    }

    @GetMapping("/{taskId}/details")
    @Operation(hidden = true)
    public SuccessResponse<TaskDetailResponse> getTaskDetails(
            @PathVariable String taskId
    ) {
        return SuccessResponse.ok(taskService.getTaskDetails(taskId));
    }

    @PatchMapping("/{taskId}/mapped")
    @Operation(
            summary = "Task flow 매핑 완성 여부 수정",
            description = "Task의 flowMappingCompleted 필드를 수정합니다."
    )
    public SuccessResponse<Void> updateFlowMappingCompleted(
            @PathVariable String taskId,
            @RequestBody TaskMappedUpdateRequest request
    ) {
        taskService.updateFlowMappingCompleted(taskId, request);
        return SuccessResponse.ok();
    }

    @PostMapping("/{taskId}/flows")
    @Operation(
            summary = "Task에 Flow 매핑 추가",
            description = "Task에 Flow ID 목록을 Append 방식으로 매핑합니다. 이미 매핑된 Flow는 중복 추가하지 않습니다."
    )
    public SuccessResponse<TaskFlowMappingResponse> addFlowsToTask(
            @PathVariable String taskId,
            @RequestBody TaskFlowMappingRequest request
    ) {
        return SuccessResponse.ok(taskService.addFlowsToTask(taskId, request));
    }
}
