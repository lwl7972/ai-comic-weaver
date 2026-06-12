package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.PipelineState;
import com.aicomic.entity.Project;
import com.aicomic.entity.ProjectTemplate;
import com.aicomic.repository.PipelineStateRepository;
import com.aicomic.repository.ProjectRepository;
import com.aicomic.repository.ProjectTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目模板服务 - 模板CRUD、从模板创建项目、内置模板初始化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ProjectTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;
    private final PipelineStateRepository pipelineStateRepository;

    /**
     * 按类型查询模板列表，不传type则返回全部
     */
    public List<ProjectTemplate> getTemplates(String type) {
        if (type != null && !type.isBlank()) {
            try {
                ProjectTemplate.StyleType styleType = ProjectTemplate.StyleType.valueOf(type);
                return templateRepository.findByStyleOrderByUseCountDesc(styleType);
            } catch (IllegalArgumentException e) {
                log.warn("无效的模板类型: {}", type);
                return List.of();
            }
        }
        return templateRepository.findAllByOrderByIsBuiltinDescUseCountDesc();
    }

    /**
     * 获取单个模板
     */
    public ProjectTemplate getTemplate(Long id) {
        return templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("模板", id));
    }

    /**
     * 创建/更新模板
     */
    @Transactional
    public ProjectTemplate saveTemplate(ProjectTemplate template) {
        template.setIsBuiltin(false);
        return templateRepository.save(template);
    }

    /**
     * 删除模板（内置模板不可删除）
     */
    @Transactional
    public void deleteTemplate(Long id) {
        ProjectTemplate template = getTemplate(id);
        if (Boolean.TRUE.equals(template.getIsBuiltin())) {
            throw new IllegalStateException("内置模板不可删除");
        }
        templateRepository.delete(template);
        log.info("删除自定义模板: id={}, name={}", id, template.getName());
    }

    /**
     * 从模板创建项目
     * - 读取模板数据
     * - 创建新Project，设置名称、描述、风格、默认参数
     * - 初始化PipelineState
     * - 模板useCount++
     */
    @Transactional
    public Project createProjectFromTemplate(Long templateId, String projectName, String description) {
        ProjectTemplate template = getTemplate(templateId);

        // 创建项目
        Project project = new Project();
        project.setName(projectName);
        project.setDescription(description != null ? description : template.getDescription());
        project.setStyle(mapStyle(template.getStyle()));
        project.setPipelineStage(Project.PipelineStage.SCRIPT);
        Project saved = projectRepository.save(project);

        // 初始化流水线状态
        PipelineState state = new PipelineState();
        state.setProjectId(saved.getId());
        state.setCurrentStage(Project.PipelineStage.SCRIPT);
        pipelineStateRepository.save(state);

        // 模板使用次数+1
        template.setUseCount(template.getUseCount() != null ? template.getUseCount() + 1 : 1);
        templateRepository.save(template);

        log.info("从模板创建项目: templateId={}, projectId={}, templateName={}",
                templateId, saved.getId(), template.getName());
        return saved;
    }

    /**
     * 初始化内置模板数据（应用启动时调用）
     */
    @Transactional
    public void initBuiltinTemplates() {
        // 短剧模板
        createBuiltinIfAbsent("短剧模板", "适合3-5分钟短剧创作",
                ProjectTemplate.StyleType.SHORT_DRAMA,
                "{\"defaultFps\":24,\"aspectRatio\":\"16:9\",\"visualStyle\":\"写实\"}");

        // 漫剧模板
        createBuiltinIfAbsent("漫剧模板", "适合漫画风格剧情",
                ProjectTemplate.StyleType.COMIC,
                "{\"defaultFps\":12,\"aspectRatio\":\"9:16\",\"visualStyle\":\"2D动漫\"}");

        // 预告片模板
        createBuiltinIfAbsent("预告片模板", "适合电影预告片制作",
                ProjectTemplate.StyleType.TRAILER,
                "{\"defaultFps\":30,\"aspectRatio\":\"16:9\",\"visualStyle\":\"电影质感\"}");

        log.info("内置模板初始化完成");
    }

    /**
     * 如果同名模板不存在则创建内置模板
     */
    private void createBuiltinIfAbsent(String name, String description,
                                       ProjectTemplate.StyleType style, String templateData) {
        if (!templateRepository.existsByName(name)) {
            ProjectTemplate template = new ProjectTemplate();
            template.setName(name);
            template.setDescription(description);
            template.setStyle(style);
            template.setTemplateData(templateData);
            template.setIsBuiltin(true);
            template.setUseCount(0);
            templateRepository.save(template);
            log.info("创建内置模板: {}", name);
        }
    }

    /**
     * 将模板风格映射为项目风格
     * Project.StyleType 只有 SHORT_DRAMA/COMIC/TRAILER，模板多了 CUSTOM
     * CUSTOM 映射为 SHORT_DRAMA 作为默认
     */
    private Project.StyleType mapStyle(ProjectTemplate.StyleType templateStyle) {
        if (templateStyle == null || templateStyle == ProjectTemplate.StyleType.CUSTOM) {
            return Project.StyleType.SHORT_DRAMA;
        }
        return Project.StyleType.valueOf(templateStyle.name());
    }
}
