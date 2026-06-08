package com.aicomic.repository;

import com.aicomic.entity.Character;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CharacterRepository extends JpaRepository<Character, Long> {

    List<Character> findByProjectIdOrderByNameAsc(Long projectId);

    List<Character> findByProjectIdAndConfirmedAtIsNotNull(Long projectId);
}
