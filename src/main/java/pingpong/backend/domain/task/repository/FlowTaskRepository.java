package pingpong.backend.domain.task.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pingpong.backend.domain.task.FlowTask;

import java.util.List;

public interface FlowTaskRepository extends JpaRepository<FlowTask, Long> {

    void deleteAllByTaskId(String taskId);

    List<FlowTask> findAllByFlowId(Long flowId);

    List<FlowTask> findAllByTaskId(String taskId);

    boolean existsByFlowIdAndTaskId(Long flowId, String taskId);
}
