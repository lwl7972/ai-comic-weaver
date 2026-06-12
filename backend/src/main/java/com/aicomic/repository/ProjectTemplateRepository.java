package com.aicomic.repository;

import com.aicomic.entity.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, Long> {

    List<ProjectTemplate> findByStyleOrderByUseCountDesc(ProjectTemplate.StyleType style);

    List<ProjectTemplate> findAllByOrderByIsBuiltinDescUseCountDesc();

    boolean existsByName(String name);
}
