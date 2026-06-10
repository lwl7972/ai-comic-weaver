package com.aicomic.repository;

import com.aicomic.entity.GenerationTask;
import com.aicomic.entity.Storyboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GenerationTaskRepository extends JpaRepository<GenerationTask, Long> {

    List<GenerationTask> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    List<GenerationTask> findByStatusIn(List<GenerationTask.TaskStatus> statuses);

    List<GenerationTask> findByGenerationPurposeOrderByCreatedAtDesc(Storyboard.GenerationPurpose purpose);

    @Modifying
    @Query("DELETE FROM GenerationTask gt WHERE gt.targetType = :type AND gt.targetId IN :ids")
    void deleteByTargetTypeAndTargetIdIn(@Param("type") String targetType, @Param("ids") List<Long> targetIds);
}
