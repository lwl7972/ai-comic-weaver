package com.aicomic.repository;

import com.aicomic.entity.AssetItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetItemRepository extends JpaRepository<AssetItem, Long> {

    /** 查找角色的定妆图 (refCharacterId 关联) */
    Optional<AssetItem> findByRefCharacterIdAndType(Long refCharacterId, AssetItem.AssetType type);

    /** 查找场景的视图图 (refSceneId 关联) */
    Optional<AssetItem> findByRefSceneIdAndType(Long refSceneId, AssetItem.AssetType type);

    /** 批量查询角色的定妆图 */
    List<AssetItem> findByRefCharacterIdInAndType(List<Long> refCharacterIds, AssetItem.AssetType type);

    /** 查找项目下所有素材 */
    List<AssetItem> findByProjectIdOrderByNameAsc(Long projectId);

    void deleteByProjectId(Long projectId);
}