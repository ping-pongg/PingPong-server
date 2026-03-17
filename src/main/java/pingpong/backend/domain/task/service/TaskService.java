package pingpong.backend.domain.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.flow.Flow;
import pingpong.backend.domain.flow.repository.FlowRepository;
import pingpong.backend.domain.flow.repository.RequestEndpointRepository;
import pingpong.backend.domain.notion.service.NotionFacade;
import pingpong.backend.domain.swagger.Endpoint;
import java.util.List;
import pingpong.backend.domain.task.FlowTask;
import pingpong.backend.domain.task.Task;
import pingpong.backend.domain.task.TaskErrorCode;
import pingpong.backend.domain.task.dto.TaskDetailResponse;
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
    private final RequestEndpointRepository requestEndpointRepository;
    private final NotionFacade notionFacade;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByTeamId(Long teamId, Boolean flowMappingCompleted, String status) {
        List<Task> tasks;
        if (flowMappingCompleted != null && status != null) {
            tasks = taskRepository.findAllByTeamIdAndFlowMappingCompletedAndStatus(teamId, flowMappingCompleted, status);
        } else if (flowMappingCompleted != null) {
            tasks = taskRepository.findAllByTeamIdAndFlowMappingCompleted(teamId, flowMappingCompleted);
        } else if (status != null) {
            tasks = taskRepository.findAllByTeamIdAndStatus(teamId, status);
        } else {
            tasks = taskRepository.findAllByTeamId(teamId);
        }

        return tasks.stream().map(task -> {
            List<Long> flowIds = flowTaskRepository.findAllByTaskId(task.getId())
                    .stream().map(FlowTask::getFlowId).toList();
            List<TaskResponse.FlowInfo> flows = flowRepository.findAllById(flowIds)
                    .stream().map(TaskResponse.FlowInfo::from).toList();
            return TaskResponse.from(task, flows);
        }).toList();
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetails(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(TaskErrorCode.TASK_NOT_FOUND));

        List<Long> flowIds = flowTaskRepository.findAllByTaskId(taskId)
                .stream().map(FlowTask::getFlowId).toList();

        List<TaskDetailResponse.FlowDetail> flowDetails = flowRepository.findAllById(flowIds).stream()
                .map(flow -> {
                    List<Endpoint> endpoints = requestEndpointRepository
                            .findDistinctEndpointsByFlowIds(List.of(flow.getId()));
                    List<TaskDetailResponse.EndpointInfo> endpointInfos = endpoints.stream()
                            .map(ep -> new TaskDetailResponse.EndpointInfo(
                                    ep.getId(),
                                    ep.getPath(),
                                    ep.getMethod() != null ? ep.getMethod().name() : null,
                                    ep.getSummary(),
                                    ep.getTag()
                            ))
                            .toList();
                    return new TaskDetailResponse.FlowDetail(flow.getId(), flow.getTitle(), flow.getDescription(), endpointInfos);
                })
                .toList();

        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getDateStart(),
                task.getDateEnd(),
                task.getCompletedDateStart(),
                task.getCompletedDateEnd(),
                task.getPageContent(),
                flowDetails
        );
    }

    @Transactional
    public void updateFlowMappingCompleted(String taskId, TaskMappedUpdateRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new CustomException(TaskErrorCode.TASK_NOT_FOUND));

        if (Boolean.TRUE.equals(request.flowMappingCompleted())) {
            boolean hasMappedFlow = flowTaskRepository.existsByTaskId(taskId);
            if (!hasMappedFlow) {
                throw new CustomException(TaskErrorCode.NO_FLOW_MAPPED);
            }

            List<Long> flowIds = flowTaskRepository.findAllByTaskId(taskId)
                    .stream().map(FlowTask::getFlowId).toList();
            List<Endpoint> endpoints = requestEndpointRepository.findDistinctEndpointsByFlowIds(flowIds);

            String newDatabaseId = notionFacade.setupTaskDatabase(
                    task.getTeamId(), taskId, task.getChildDatabaseId(), endpoints);
            task.updateChildDatabaseId(newDatabaseId);
        }

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
