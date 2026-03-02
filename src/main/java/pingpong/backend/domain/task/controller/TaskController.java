package pingpong.backend.domain.task.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
            description = "팀의 Notion primary database와 동기화된 Task 목록을 반환합니다."
    )
    public SuccessResponse<List<TaskResponse>> getTasksByTeamId(@PathVariable Long teamId) {
        return SuccessResponse.ok(taskService.getTasksByTeamId(teamId));
    }
}
