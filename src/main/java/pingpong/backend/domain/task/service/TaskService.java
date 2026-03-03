package pingpong.backend.domain.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.task.FlowTask;
import pingpong.backend.domain.task.Task;
import pingpong.backend.domain.task.TaskErrorCode;
import pingpong.backend.domain.task.dto.TaskFlowMappingRequest;
import pingpong.backend.domain.task.dto.TaskFlowMappingResponse;
import pingpong.backend.domain.task.dto.TaskMappedUpdateRequest;
import pingpong.backend.domain.task.dto.TaskResponse;
import pingpong.backend.domain.task.repository.FlowTaskRepository;
import pingpong.backend.domain.task.repository.TaskRepository;
import pingpong.backend.global.exception.CustomException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final FlowTaskRepository flowTaskRepository;
    private final FlowRepository flowRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByTeamId(Long teamId, Boolean flowMappingCompleted) {
        List<Task> tasks = flowMappingCompleted != null
                ? taskRepository.findAllByTeamIdAndFlowMappingCompleted(teamId, flowMappingCompleted)
                : taskRepository.findAllByTeamId(teamId);
        return tasks.stream().map(TaskResponse::from).toList();
    }

    @Transactional
    public void updateFlowMappingCompleted(String taskId, TaskMappedUpdateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(TaskErrorCode.TASK_NOT_FOUND));
        task.updateFlowMappingCompleted(request.flowMappingCompleted());
    }

    @Transactional
    public TaskFlowMappingResponse addFlowsToTask(String taskId, TaskFlowMappingRequest request) {
        taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(TaskErrorCode.TASK_NOT_FOUND));

        List<Long> flowIds = request.flowIds();
        List<Flow> flows = flowRepository.findAllById(flowIds);
        if (flows.size() != flowIds.size()) {
            throw new CustomException(TaskErrorCode.FLOW_NOT_FOUND_IN_MAPPING);
        }

        for (Long flowId : flowIds) {
            if (!flowTaskRepository.existsByFlowIdAndTaskId(flowId, taskId)) {
                flowTaskRepository.save(FlowTask.of(flowId, taskId));
            }
        }

        List<Long> allFlowIds = flowTaskRepository.findAllByTaskId(taskId)
                .stream().map(FlowTask::getFlowId).toList();
        return new TaskFlowMappingResponse(taskId, allFlowIds);
    }
}
