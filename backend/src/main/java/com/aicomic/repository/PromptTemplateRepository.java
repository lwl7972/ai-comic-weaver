package com.aicomic.repository;

import com.aicomic.entity.PromptTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    List<PromptTemplate> findByCategory(PromptTemplate.TemplateCategory category);

    Optional<PromptTemplate> findByNameAndVersion(String name, Integer version);

    List<PromptTemplate> findByIsDefaultTrue();
}
