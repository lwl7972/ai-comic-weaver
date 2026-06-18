package com.aicomic.repository;

import com.aicomic.entity.ProjectTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectTemplateRepository extends JpaRepository<ProjectTemplate, Long> {

    List<ProjectTemplate> findByStyleOrderByUseCountDesc(ProjectTemplate.StyleType style);

    List<ProjectTemplate> findAllByOrderByIsBuiltinDescUseCountDesc();

    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END FROM project_template WHERE name = :name", nativeQuery = true)
    int countByName(@Param("name") String name);

    default boolean existsByName(String name) {
        return countByName(name) > 0;
    }
}
