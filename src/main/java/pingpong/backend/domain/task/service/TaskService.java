package pingpong.backend.domain.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pingpong.backend.domain.task.dto.TaskResponse;
import pingpong.backend.domain.task.repository.TaskRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByTeamId(Long teamId) {
        return taskRepository.findAllByTeamId(teamId).stream()
                .map(TaskResponse::from)
                .toList();
    }
}
