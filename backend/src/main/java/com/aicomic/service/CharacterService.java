package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.Character;
import com.aicomic.entity.CharacterAnchor;
import com.aicomic.repository.CharacterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 🎭 角色模块服务
 * 负责：AI 资产提取（ADR-4）、6 层身份锚点（ADR-10）、定妆图管理、角色圣经
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CharacterService {

    private final CharacterRepository characterRepository;

    /**
     * 获取项目的所有角色
     */
    @Transactional(readOnly = true)
    public List<Character> getCharactersByProject(Long projectId) {
        return characterRepository.findByProjectIdOrderByNameAsc(projectId);
    }

    /**
     * 创建或更新角色
     */
    @Transactional
    public Character saveCharacter(Character character) {
        return characterRepository.save(character);
    }

    /**
     * 删除角色
     */
    @Transactional
    public void deleteCharacter(Long characterId) {
        if (!characterRepository.existsById(characterId)) {
            throw new ResourceNotFoundException("角色", characterId);
        }
        characterRepository.deleteById(characterId);
    }

    /**
     * AI 自动提取角色资产（异步执行）
     * 剧本完成后自动触发 LLM 提取角色列表
     */
    @Async("taskExecutor")
    public void extractCharactersAsync(Long projectId, Long scriptId) {
        log.info("开始 AI 提取角色: projectId={}, scriptId={}", projectId, scriptId);
        // TODO: 调用 LLM 提取角色
        // 1. 获取剧本全文
        // 2. 组装资产提取提示词（PromptTemplate STORYBOARD/A.6 类）
        // 3. 调用 LLM API 提取角色列表
        // 4. 生成 ExtractedAsset 待确认记录
        // 5. 通过 SSE 推送进度
        log.info("角色提取完成: projectId={}", projectId);
    }

    /**
     * 生成角色定妆图（异步执行）
     * 使用 6 层身份锚点拼装提示词，调用图像模型生成
     */
    @Async("taskExecutor")
    public void generateMakeupImageAsync(Long characterId) {
        log.info("开始生成角色定妆图: characterId={}", characterId);
        // TODO: 调用图像模型生成定妆图
        // 1. 获取角色信息及 6 层锚点
        // 2. 拼装完整提示词（PromptTemplate CHARACTER/A.3 类）
        // 3. 调用图像模型 API
        // 4. 保存定妆图 URL
        // 5. 通过 SSE 推送进度
        log.info("角色定妆图生成完成: characterId={}", characterId);
    }
}
