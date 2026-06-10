package com.aicomic.repository;

import com.aicomic.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    List<ModelConfig> findByIsActiveTrueOrderByPriorityAsc();

    List<ModelConfig> findByType(ModelConfig.ModelType type);

    Optional<ModelConfig> findByName(String name);
}
