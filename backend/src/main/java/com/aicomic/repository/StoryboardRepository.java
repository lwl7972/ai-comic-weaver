package com.aicomic.repository;

import com.aicomic.entity.Storyboard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryboardRepository extends JpaRepository<Storyboard, Long> {

    List<Storyboard> findByEpisodeIdOrderBySequenceAsc(Long episodeId);

    Optional<Storyboard> findByEpisodeIdAndSequence(Long episodeId, Integer sequence);

    long countByEpisodeIdAndStatus(Long episodeId, Storyboard.StoryboardStatus status);
}
