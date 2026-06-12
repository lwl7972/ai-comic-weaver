package com.aicomic.service;

import com.aicomic.common.exception.ResourceNotFoundException;
import com.aicomic.entity.AssetItem;
import com.aicomic.repository.AssetItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 素材库服务 - 上传/查询/删除/角色场景关联
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final AssetItemRepository assetItemRepository;

    @Value("${app.storage.path:./data}")
    private String storageBasePath;

    // ==================== 查询 ====================

    /**
     * 按项目+类型+标签筛选素材
     */
    @Transactional(readOnly = true)
    public List<AssetItem> getAssetsByProject(Long projectId, String type, String tags) {
        List<AssetItem> assets = assetItemRepository.findByProjectIdOrderByNameAsc(projectId);

        if (type != null && !type.isBlank()) {
            AssetItem.AssetType assetType = AssetItem.AssetType.valueOf(type.toUpperCase());
            assets = assets.stream()
                    .filter(a -> a.getType() == assetType)
                    .collect(Collectors.toList());
        }

        if (tags != null && !tags.isBlank()) {
            String tagLower = tags.toLowerCase();
            assets = assets.stream()
                    .filter(a -> a.getTags() != null && a.getTags().toLowerCase().contains(tagLower))
                    .collect(Collectors.toList());
        }

        return assets;
    }

    /**
     * 获取单个素材
     */
    @Transactional(readOnly = true)
    public AssetItem getAsset(Long id) {
        return assetItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset", id));
    }

    // ==================== 上传 ====================

    /**
     * 上传文件到本地存储
     */
    @Transactional
    public AssetItem uploadAsset(Long projectId, MultipartFile file, String tags) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String mimeType = file.getContentType();
        AssetItem.AssetType type = determineType(mimeType, originalFilename);

        // 构建存储路径: storageBasePath/projects/{projectId}/assets/{type.lower}/
        String timestamp = String.valueOf(System.currentTimeMillis());
        String fileName = timestamp + "_" + originalFilename;
        Path dirPath = Paths.get(storageBasePath, "projects", String.valueOf(projectId),
                "assets", type.name().toLowerCase());
        Path filePath = dirPath.resolve(fileName);

        try {
            Files.createDirectories(dirPath);
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("文件保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("文件保存失败: " + e.getMessage(), e);
        }

        // 相对路径存储
        String relativePath = Paths.get("projects", String.valueOf(projectId),
                "assets", type.name().toLowerCase(), fileName).toString();

        AssetItem asset = new AssetItem();
        asset.setProjectId(projectId);
        asset.setName(originalFilename);
        asset.setType(type);
        asset.setFilePath(relativePath);
        asset.setFileSize(file.getSize());
        asset.setMimeType(mimeType);
        asset.setTags(tags);
        asset.setSource(AssetItem.AssetSource.UPLOAD);
        asset.setCreatedAt(LocalDateTime.now());

        AssetItem saved = assetItemRepository.save(asset);
        log.info("素材上传成功: id={}, name={}, type={}, size={}", saved.getId(), saved.getName(),
                saved.getType(), saved.getFileSize());
        return saved;
    }

    // ==================== 删除 ====================

    /**
     * 删除素材（同时删除本地文件）
     */
    @Transactional
    public void deleteAsset(Long id) {
        AssetItem asset = getAsset(id);

        // 删除本地文件
        Path filePath = Paths.get(storageBasePath, asset.getFilePath());
        try {
            Files.deleteIfExists(filePath);
            log.info("本地文件已删除: {}", filePath);
        } catch (IOException e) {
            log.warn("本地文件删除失败: {} - {}", filePath, e.getMessage());
        }

        assetItemRepository.deleteById(id);
        log.info("素材已删除: id={}", id);
    }

    // ==================== 关联 ====================

    /**
     * 关联角色
     */
    @Transactional
    public AssetItem linkToCharacter(Long assetId, Long characterId) {
        AssetItem asset = getAsset(assetId);
        asset.setRefCharacterId(characterId);
        return assetItemRepository.save(asset);
    }

    /**
     * 关联场景
     */
    @Transactional
    public AssetItem linkToScene(Long assetId, Long sceneId) {
        AssetItem asset = getAsset(assetId);
        asset.setRefSceneId(sceneId);
        return assetItemRepository.save(asset);
    }

    // ==================== 辅助方法 ====================

    private AssetItem.AssetType determineType(String mimeType, String filename) {
        if (mimeType != null) {
            if (mimeType.startsWith("image/")) return AssetItem.AssetType.IMAGE;
            if (mimeType.startsWith("video/")) return AssetItem.AssetType.VIDEO;
            if (mimeType.startsWith("audio/")) return AssetItem.AssetType.AUDIO;
        }
        // fallback: 按文件扩展名判断
        if (filename != null) {
            String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
            switch (ext) {
                case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp": case "svg":
                    return AssetItem.AssetType.IMAGE;
                case "mp4": case "avi": case "mov": case "mkv": case "webm": case "flv":
                    return AssetItem.AssetType.VIDEO;
                case "mp3": case "wav": case "ogg": case "flac": case "aac": case "m4a":
                    return AssetItem.AssetType.AUDIO;
                default:
                    return AssetItem.AssetType.DOCUMENT;
            }
        }
        return AssetItem.AssetType.DOCUMENT;
    }
}
