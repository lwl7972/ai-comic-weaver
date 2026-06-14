package com.aicomic.repository;

import com.aicomic.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findAllByOrderByCreatedAtDesc();

    List<Project> findAllByOrderByUpdatedAtDesc();

    Optional<Project> findByName(String name);
}
