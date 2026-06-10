package com.aicomic.repository;

import com.aicomic.entity.Novel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NovelRepository extends JpaRepository<Novel, Long> {

    List<Novel> findByProjectIdOrderByImportedAtDesc(Long projectId);

    Optional<Novel> findTopByProjectIdOrderByImportedAtDesc(Long projectId);

    void deleteByProjectId(Long projectId);
}
