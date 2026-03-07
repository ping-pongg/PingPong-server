package pingpong.backend.domain.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.task.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, String> {

    List<Task> findAllByTeamId(Long teamId);

    List<Task> findAllByTeamIdAndFlowMappingCompleted(Long teamId, Boolean flowMappingCompleted);

    List<Task> findAllByTeamIdAndStatus(Long teamId, String status);

    List<Task> findAllByTeamIdAndFlowMappingCompletedAndStatus(Long teamId, Boolean flowMappingCompleted, String status);
}
