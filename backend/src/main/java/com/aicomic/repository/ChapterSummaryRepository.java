package com.aicomic.repository;

import com.aicomic.entity.ChapterSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChapterSummaryRepository extends JpaRepository<ChapterSummary, Long> {

    List<ChapterSummary> findByNovelIdOrderByChapterIndexAsc(Long novelId);

    List<ChapterSummary> findByNovelIdAndStatus(Long novelId, ChapterSummary.SummaryStatus status);
}
