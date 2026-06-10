package com.aicomic.entity;

import javax.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 5.26 诊断信息表 - 诊断工具模块
 */
@Entity
@Table(name = "diagnostic_info")
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DiagnosticInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    /** 检查项名称 */
    @Column(name = "check_name", nullable = false, length = 100)
    private String checkName;

    /** 检查类别 */
    @Enumerated(EnumType.STRING)
    @Column(name = "check_category", nullable = false)
    private CheckCategory category;

    /** 检查结果状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false)
    private ResultStatus resultStatus;

    /** 结果详情 */
    @Lob
    @Column(name = "detail_message", columnDefinition = "TEXT")
    private String detailMessage;

    /** 建议修复方案 */
    @Lob
    @Column(name = "suggestion", columnDefinition = "TEXT")
    private String suggestion;

    /** 执行耗时(ms) */
    @Column(name = "duration_ms")
    private Long durationMs;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum CheckCategory {
        SYSTEM, DATABASE, API_CONNECTIVITY, FILE_SYSTEM, PERFORMANCE, SECURITY
    }

    public enum ResultStatus { PASS, WARN, FAIL, SKIP }
}
