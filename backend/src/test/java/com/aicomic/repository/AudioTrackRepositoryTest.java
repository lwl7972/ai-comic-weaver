package com.aicomic.repository;

import com.aicomic.entity.AudioTrack;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AudioTrackRepository 集成测试
 */
@DataJpaTest
@ActiveProfiles("test")
class AudioTrackRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private AudioTrackRepository audioTrackRepository;

    @Test
    void testSaveAndFind() {
        AudioTrack track = new AudioTrack();
        track.setEpisodeId(1L);
        track.setName("背景音乐");
        track.setType(AudioTrack.AudioTrackType.BGM);
        track.setFilePath("/audio/bgm.mp3");
        track.setDuration(120.0);
        track.setVolume(0.8);

        AudioTrack saved = audioTrackRepository.save(track);
        entityManager.flush();
        entityManager.clear();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEpisodeId()).isEqualTo(1L);
        assertThat(saved.getName()).isEqualTo("背景音乐");
        assertThat(saved.getType()).isEqualTo(AudioTrack.AudioTrackType.BGM);
    }

    @Test
    void testFindByEpisodeId() {
        AudioTrack track1 = new AudioTrack();
        track1.setEpisodeId(1L);
        track1.setName("BGM");
        track1.setType(AudioTrack.AudioTrackType.BGM);
        track1.setFilePath("/audio/bgm.mp3");

        AudioTrack track2 = new AudioTrack();
        track2.setEpisodeId(1L);
        track2.setName("配音");
        track2.setType(AudioTrack.AudioTrackType.VOICEOVER);
        track2.setFilePath("/audio/voice.mp3");

        audioTrackRepository.save(track1);
        audioTrackRepository.save(track2);
        entityManager.flush();

        List<AudioTrack> tracks = audioTrackRepository.findByEpisodeIdOrderByCreatedAtAsc(1L);
        assertThat(tracks).hasSize(2);
        assertThat(tracks.get(0).getType()).isIn(AudioTrack.AudioTrackType.BGM, AudioTrack.AudioTrackType.VOICEOVER);
    }

    @Test
    void testFindByEpisodeIdAndType() {
        AudioTrack track1 = new AudioTrack();
        track1.setEpisodeId(1L);
        track1.setName("BGM 1");
        track1.setType(AudioTrack.AudioTrackType.BGM);
        track1.setFilePath("/audio/bgm1.mp3");

        AudioTrack track2 = new AudioTrack();
        track2.setEpisodeId(1L);
        track2.setName("BGM 2");
        track2.setType(AudioTrack.AudioTrackType.BGM);
        track2.setFilePath("/audio/bgm2.mp3");

        AudioTrack track3 = new AudioTrack();
        track3.setEpisodeId(1L);
        track3.setName("配音");
        track3.setType(AudioTrack.AudioTrackType.VOICEOVER);
        track3.setFilePath("/audio/voice.mp3");

        audioTrackRepository.saveAll(List.of(track1, track2, track3));
        entityManager.flush();

        List<AudioTrack> bgmTracks = audioTrackRepository.findByEpisodeIdAndTypeOrderByCreatedAtAsc(
                1L, AudioTrack.AudioTrackType.BGM
        );

        assertThat(bgmTracks).hasSize(2);
        assertThat(bgmTracks).extracting("name").containsExactlyInAnyOrder("BGM 1", "BGM 2");
    }

    @Test
    void testDeleteById() {
        AudioTrack track = new AudioTrack();
        track.setEpisodeId(1L);
        track.setName("临时音频");
        track.setType(AudioTrack.AudioTrackType.EFFECT);
        track.setFilePath("/audio/effect.mp3");

        AudioTrack saved = audioTrackRepository.save(track);
        entityManager.flush();

        audioTrackRepository.deleteById(saved.getId());
        entityManager.flush();

        assertThat(audioTrackRepository.findById(saved.getId())).isEmpty();
    }
}
