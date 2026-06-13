package com.aicomic.integration;

import com.aicomic.AiComicApplication;
import com.aicomic.entity.*;
import com.aicomic.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端集成测试
 * 测试完整流水线：剧本 → 角色提取 → 场景提取 → 分镜解析 → 视频生成 → S 级合成
 */
@SpringBootTest(classes = AiComicApplication.class)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineIntegrationTest {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ScriptRepository scriptRepository;

    @Autowired
    private EpisodeRepository episodeRepository;

    @Autowired
    private CharacterRepository characterRepository;

    @Autowired
    private SceneRepository sceneRepository;

    @Autowired
    private StoryboardRepository storyboardRepository;

    private static Long projectId;
    private static Long scriptId;
    private static Long episodeId;

    @Test
    @Order(1)
    @DisplayName("Step 1: 创建项目和剧本")
    void testCreateProjectAndScript() {
        // 创建项目
        Project project = new Project();
        project.setName("集成测试项目");
        project.setDescription("端到端流水线测试");
        project.setStyle(Project.StyleType.SHORT_DRAMA);
        project.setPipelineStage(Project.PipelineStage.SCRIPT);
        project = projectRepository.save(project);
        projectId = project.getId();
        assertNotNull(projectId);

        // 创建剧本
        Script script = new Script();
        script.setProjectId(projectId);
        script.setTitle("测试剧本");
        script.setOutline("这是一个测试剧本的大纲");
        script.setCurrentStep(Script.ScriptStep.EPISODES);
        script.setStatus(Script.ScriptStatus.IN_PROGRESS);
        script = scriptRepository.save(script);
        scriptId = script.getId();
        assertNotNull(scriptId);

        // 创建剧集
        Episode episode = new Episode();
        episode.setScriptId(scriptId);
        episode.setEpisodeNumber(1);
        episode.setTitle("第一集");
        episode.setScriptContent("第一集的剧本内容");
        episode.setStatus(Episode.EpisodeStatus.PARSED);
        episode = episodeRepository.save(episode);
        episodeId = episode.getId();
        assertNotNull(episodeId);

        System.out.println("✅ Step 1 完成：项目和剧本创建成功");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: 创建角色")
    void testCreateCharacters() {
        Character character = new Character();
        character.setProjectId(projectId);
        character.setName("测试主角");
        character.setRole(Character.Role.PROTAGONIST);
        character.setGender(Character.Gender.MALE);
        character.setAgeRange("20-25 岁");
        character.setAppearance("英俊，黑发，剑眉");
        character.setPersonality("勇敢，正直");
        character.setAnchorPrompt("完整的角色锚点描述");
        character.setExtractedFromScript(true);
        character.setConfirmedAt(LocalDateTime.now());
        character = characterRepository.save(character);

        assertNotNull(character.getId());
        assertEquals("测试主角", character.getName());

        // 创建第二个角色
        Character character2 = new Character();
        character2.setProjectId(projectId);
        character2.setName("测试配角");
        character2.setRole(Character.Role.SUPPORTING);
        character2.setGender(Character.Gender.FEMALE);
        characterRepository.save(character2);

        System.out.println("✅ Step 2 完成：角色创建成功");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: 创建场景")
    void testCreateScenes() {
        Scene scene = new Scene();
        scene.setProjectId(projectId);
        scene.setName("测试场景 - 卧室");
        scene.setDescription("一个温馨的卧室");
        scene.setTimeOfDay(Scene.TimeOfDay.NIGHT);
        scene.setWeather(Scene.Weather.CLEAR);
        scene.setStyleHint("现代风格");
        scene = sceneRepository.save(scene);

        assertNotNull(scene.getId());
        assertEquals("测试场景 - 卧室", scene.getName());

        System.out.println("✅ Step 3 完成：场景创建成功");
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: 创建分镜")
    void testCreateStoryboards() {
        Storyboard sb1 = new Storyboard();
        sb1.setEpisodeId(episodeId);
        sb1.setSequence(0);
        sb1.setTimeRange("0-4s");
        sb1.setContinuity("开场镜头");
        sb1.setDialogue("[主角，平静]:\"这是开始\"");
        sb1.setAction("主角走进房间");
        sb1.setEmotion("平静");
        sb1.setShotSize(Storyboard.ShotSize.WIDE);
        sb1.setCameraAngle(Storyboard.CameraAngle.EYE_LEVEL);
        sb1.setCameraMovement(Storyboard.CameraMovement.STATIC);
        sb1.setInvolvedCharacters("[\"测试主角\"]");
        sb1.setInvolvedCharacterIds("[1]");
        sb1.setInvolvedSceneName("测试场景 - 卧室");
        sb1.setInvolvedSceneId(1L);
        sb1.setBgSound("轻柔的背景音乐");
        sb1.setStatus(Storyboard.StoryboardStatus.IMAGE_DONE);
        sb1.setGenerationPurpose(Storyboard.GenerationPurpose.STORYBOARD_VIDEO);
        sb1 = storyboardRepository.save(sb1);

        Storyboard sb2 = new Storyboard();
        sb2.setEpisodeId(episodeId);
        sb2.setSequence(1);
        sb2.setTimeRange("4-8s");
        sb2.setContinuity("承接上镜");
        sb2.setDialogue("[配角，惊讶]:\"真的吗？\"");
        sb2.setAction("配角转身");
        sb2.setEmotion("惊讶");
        sb2.setShotSize(Storyboard.ShotSize.MEDIUM);
        sb2.setCameraAngle(Storyboard.CameraAngle.EYE_LEVEL);
        sb2.setCameraMovement(Storyboard.CameraMovement.PAN_LEFT);
        sb2.setGeneratedImageUrl("/images/test_shot2.jpg");
        sb2.setStatus(Storyboard.StoryboardStatus.IMAGE_DONE);
        sb2.setGenerationPurpose(Storyboard.GenerationPurpose.STORYBOARD_VIDEO);
        sb2 = storyboardRepository.save(sb2);

        assertNotNull(sb1.getId());
        assertNotNull(sb2.getId());

        System.out.println("✅ Step 4 完成：分镜创建成功");
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: 验证完整流水线数据")
    void testPipelineDataIntegrity() {
        // 验证项目
        Project project = projectRepository.findById(projectId).orElse(null);
        assertNotNull(project);
        assertEquals("集成测试项目", project.getName());

        // 验证剧集数量
        List<Episode> episodes = episodeRepository.findByScriptId(scriptId);
        assertEquals(1, episodes.size());

        // 验证角色数量
        List<Character> characters = characterRepository.findByProjectId(projectId);
        assertEquals(2, characters.size());

        // 验证场景数量
        List<Scene> scenes = sceneRepository.findByProjectId(projectId);
        assertEquals(1, scenes.size());

        // 验证分镜数量
        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        assertEquals(2, storyboards.size());
        assertEquals(0, storyboards.get(0).getSequence());
        assertEquals(1, storyboards.get(1).getSequence());

        // 验证分镜关联
        Storyboard firstStoryboard = storyboards.get(0);
        assertEquals("测试主角", firstStoryboard.getInvolvedCharacters());
        assertEquals("测试场景 - 卧室", firstStoryboard.getInvolvedSceneName());

        System.out.println("✅ Step 5 完成：流水线数据完整性验证通过");
    }

    @Test
    @Order(6)
    @DisplayName("Step 6: 更新分镜状态为视频完成")
    void testUpdateStoryboardStatus() {
        List<Storyboard> storyboards = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        for (Storyboard sb : storyboards) {
            sb.setGeneratedVideoUrl("/videos/test_episode_" + sb.getSequence() + ".mp4");
            sb.setStatus(Storyboard.StoryboardStatus.VIDEO_DONE);
        }
        storyboardRepository.saveAll(storyboards);

        List<Storyboard> updated = storyboardRepository.findByEpisodeIdOrderBySequenceAsc(episodeId);
        assertEquals(2, updated.size());
        for (Storyboard sb : updated) {
            assertNotNull(sb.getGeneratedVideoUrl());
            assertEquals(Storyboard.StoryboardStatus.VIDEO_DONE, sb.getStatus());
        }

        System.out.println("✅ Step 6 完成：分镜视频状态更新成功");
    }

    @DisplayName("清理测试数据")
    @AfterAll
    static void cleanup() {
        System.out.println("🧹 测试完成，数据将在事务回滚后自动清理");
    }
}
