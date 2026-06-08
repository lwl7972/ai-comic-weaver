package com.aicomic.repository;

import com.aicomic.entity.Scene;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findByProjectIdOrderByNameAsc(Long projectId);

    List<Scene> findByProjectIdAndConfirmedAtIsNotNull(Long projectId);
}
