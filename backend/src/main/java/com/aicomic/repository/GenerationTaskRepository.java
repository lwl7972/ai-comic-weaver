package com.aicomic.repository;

import com.aicomic.entity.GenerationTask;
import com.aicomic.entity.Storyboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {

    List<GenerationTask> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    List<GenerationTask> findByStatusIn(List<GenerationTask.TaskStatus> statuses);

    List<GenerationTask> findByPurposeOrderByCreatedAtDesc(Storyboard.GenerationPurpose purpose);
}
