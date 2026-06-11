package com.aicomic.repository;

import com.aicomic.entity.ExtractedAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExtractedAssetRepository extends JpaRepository<ExtractedAsset, Long> {

    List<ExtractedAsset> findByProjectIdAndType(Long projectId, ExtractedAsset.ExtractedAssetType type);

    List<ExtractedAsset> findByProjectIdAndTypeAndStatus(Long projectId,
                                                         ExtractedAsset.ExtractedAssetType type,
                                                         ExtractedAsset.ExtractedStatus status);

    List<ExtractedAsset> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
