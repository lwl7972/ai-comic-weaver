package com.aicomic.repository;

import com.aicomic.entity.AudioTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 音频轨道数据访问层
 */
@Repository
public interface AudioTrackRepository extends JpaRepository<AudioTrack, Long> {

    /**
     * 按剧集 ID 查询所有音频轨道
     */
    List<AudioTrack> findByEpisodeIdOrderByCreatedAtAsc(Long episodeId);

    /**
     * 按剧集 ID 和类型查询音频轨道
     */
    List<AudioTrack> findByEpisodeIdAndTypeOrderByCreatedAtAsc(Long episodeId, AudioTrack.AudioTrackType type);
}
