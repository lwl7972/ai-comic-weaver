package com.aicomic.repository;

import com.aicomic.entity.PipelineState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PipelineStateRepository extends JpaRepository<PipelineState, Long> {

    Optional<PipelineState> findByProjectId(Long projectId);
}
