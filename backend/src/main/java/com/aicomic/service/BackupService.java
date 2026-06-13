package com.aicomic.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 备份恢复服务
 * 支持数据库导出和资产文件打包
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private final ExportImportService exportImportService;

    @Value("${app.storage.backup-dir:./backups}")
    private String backupDir;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    /**
     * 创建完整备份（数据库 + 资产文件）
     *
     * @return 备份文件路径
     */
    @Transactional(readOnly = true)
    public String createBackup() {
        try {
            // 创建备份目录
            Path backupPath = Paths.get(backupDir);
            if (!Files.exists(backupPath)) {
                Files.createDirectories(backupPath);
                log.info("创建备份目录：{}", backupDir);
            }

            // 生成备份文件名：backup_YYYYMMDD_HHMMSS.zip
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "backup_" + timestamp + ".zip";
            Path backupFile = backupPath.resolve(backupFileName);

            log.info("开始创建备份：{}", backupFileName);

            // 创建临时目录用于存放导出数据
            Path tempDir = Files.createTempDirectory("backup_" + timestamp);
            try {
                // 1. 导出数据库为 SQL
                Path sqlFile = tempDir.resolve("database_export.sql");
                exportImportService.exportDatabase(sqlFile);
                log.info("数据库导出完成：{}", sqlFile);

                // 2. 创建 ZIP 文件
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile.toFile()))) {
                    // 添加 SQL 文件
                    addFileToZip(zos, sqlFile, "database/database_export.sql");

                    // 3. 添加资产文件（如果存在）
                    addAssetsToZip(zos, tempDir);
                }

                log.info("备份创建完成：{}, 大小：{} bytes", backupFileName, Files.size(backupFile));
                return backupFile.toString();
            } finally {
                // 清理临时目录
                deleteRecursive(tempDir);
            }
        } catch (Exception e) {
            log.error("备份创建失败", e);
            throw new RuntimeException("备份创建失败：" + e.getMessage(), e);
        }
    }

    /**
     * 从备份恢复
     *
     * @param backupFilePath 备份文件路径
     */
    @Transactional
    public void restoreBackup(String backupFilePath) {
        try {
            Path backupPath = Paths.get(backupFilePath);
            if (!Files.exists(backupPath)) {
                throw new RuntimeException("备份文件不存在：" + backupFilePath);
            }

            log.info("开始从备份恢复：{}", backupFilePath);

            // 创建临时目录用于解压
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path tempDir = Files.createTempDirectory("restore_" + timestamp);

            try {
                // 1. 解压备份文件
                unzipBackup(backupPath, tempDir);

                // 2. 恢复数据库
                Path sqlFile = tempDir.resolve("database/database_export.sql");
                if (Files.exists(sqlFile)) {
                    exportImportService.importDatabase(sqlFile);
                    log.info("数据库恢复完成");
                }

                // 3. 恢复资产文件（ TODO: 后续实现）
                // restoreAssets(tempDir);

                log.info("备份恢复完成");
            } finally {
                // 清理临时目录
                deleteRecursive(tempDir);
            }
        } catch (Exception e) {
            log.error("备份恢复失败", e);
            throw new RuntimeException("备份恢复失败：" + e.getMessage(), e);
        }
    }

    /**
     * 获取所有备份文件列表
     *
     * @return 备份文件信息列表
     */
    public java.util.List<BackupInfo> listBackups() {
        Path backupPath = Paths.get(backupDir);
        if (!Files.exists(backupPath)) {
            return java.util.Collections.emptyList();
        }

        try {
            return Files.list(backupPath)
                    .filter(p -> p.getFileName().toString().startsWith("backup_") && p.getFileName().toString().endsWith(".zip"))
                    .map(this::toBackupInfo)
                    .sorted((a, b) -> b.createdAt().compareTo(a.createdAt()))
                    .toList();
        } catch (IOException e) {
            log.error("读取备份列表失败", e);
            return java.util.Collections.emptyList();
        }
    }

    /**
     * 删除备份文件
     *
     * @param backupFileName 备份文件名
     */
    public void deleteBackup(String backupFileName) {
        Path backupPath = Paths.get(backupDir).resolve(backupFileName);
        if (!Files.exists(backupPath)) {
            throw new RuntimeException("备份文件不存在：" + backupFileName);
        }

        try {
            Files.delete(backupPath);
            log.info("备份已删除：{}", backupFileName);
        } catch (IOException e) {
            log.error("删除备份失败", e);
            throw new RuntimeException("删除备份失败：" + e.getMessage(), e);
        }
    }

    private void addFileToZip(ZipOutputStream zos, Path file, String entryName) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void addAssetsToZip(ZipOutputStream zos, Path tempDir) throws IOException {
        // TODO: 后续实现资产文件的打包
        // 目前先添加一个空的 assets 目录
        ZipEntry assetsDir = new ZipEntry("assets/");
        zos.putNextEntry(assetsDir);
        zos.closeEntry();
    }

    private void unzipBackup(Path zipFile, Path destDir) throws IOException {
        // 使用 java.util.zip.ZipFile 解压
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                Path entryPath = destDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (var is = zip.getInputStream(entry);
                         var os = new FileOutputStream(entryPath.toFile())) {
                        is.transferTo(os);
                    }
                }
            }
        }
    }

    private BackupInfo toBackupInfo(Path path) {
        try {
            String fileName = path.getFileName().toString();
            String timestamp = fileName.replace("backup_", "").replace(".zip", "");
            LocalDateTime createdAt = LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            long size = Files.size(path);

            return new BackupInfo(fileName, createdAt, size);
        } catch (IOException e) {
            log.warn("读取备份文件信息失败：{}", path.getFileName(), e);
            return new BackupInfo(path.getFileName().toString(), LocalDateTime.MIN, 0);
        }
    }

    private void deleteRecursive(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try {
                        deleteRecursive(p);
                    } catch (IOException e) {
                        log.error("删除文件失败：{}", p, e);
                    }
                });
            }
        }
        Files.deleteIfExists(path);
    }

    /**
     * 备份文件信息
     */
    public record BackupInfo(String fileName, LocalDateTime createdAt, long size) {
    }
}
