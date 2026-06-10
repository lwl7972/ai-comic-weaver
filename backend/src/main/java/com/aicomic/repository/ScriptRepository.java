package com.aicomic.repository;

import com.aicomic.entity.Script;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ScriptRepository extends JpaRepository<Script, Long> {

    List<Script> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    Optional<Script> findTopByProjectIdOrderByCreatedAtDesc(Long projectId);

    void deleteByProjectId(Long projectId);
}
