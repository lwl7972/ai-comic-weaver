package com.aicomic.controller;

import com.aicomic.common.response.ApiResponse;
import com.aicomic.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 备份管理控制器
 */
@RestController
@RequestMapping("/api/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 创建备份
     */
    @PostMapping
    public ResponseEntity<ApiResponse<BackupResponse>> createBackup() {
        String backupFile = backupService.createBackup();
        return ResponseEntity.ok(
            ApiResponse.success(
                new BackupResponse(backupFile, "备份创建成功")
            )
        );
    }

    /**
     * 获取备份列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BackupService.BackupInfo>>> listBackups() {
        List<BackupService.BackupInfo> backups = backupService.listBackups();
        return ResponseEntity.ok(ApiResponse.success(backups));
    }

    /**
     * 恢复备份
     */
    @PostMapping("/restore")
    public ResponseEntity<ApiResponse<Void>> restoreBackup(@RequestParam String file) {
        backupService.restoreBackup(file);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @DeleteMapping("/{filename}")
    public ResponseEntity<ApiResponse<Void>> deleteBackup(@PathVariable String filename) {
        backupService.deleteBackup(filename);
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 删除备份
     */
    @DeleteMapping("/{filename}")
    public ResponseEntity<ApiResponse<Void>> deleteBackup(@PathVariable String filename) {
        backupService.deleteBackup(filename);
        return ResponseEntity.ok(ApiResponse.success(null, "备份删除成功"));
    }

    /**
     * 备份响应 DTO
     */
    public record BackupResponse(String filePath, String message) {
    }
}
