package com.aicomic.repository;

import com.aicomic.entity.Episode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findByScriptIdOrderByEpisodeNumberAsc(Long scriptId);

    List<Episode> findByScriptIdAndStatus(Long scriptId, Episode.EpisodeStatus status);
}
