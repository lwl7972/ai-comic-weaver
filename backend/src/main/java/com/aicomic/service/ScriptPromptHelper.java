package com.aicomic.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 剧本 prompt 构建与解析公共工具
 * <p>
 * 统一 ScriptService 和 NovelService 中重复的：
 * - buildOutlinePrompt：大纲生成提示词
 * - parseOutlineToEpisodes：大纲→剧集列表解析
 * - buildEpisodeScriptPrompt：剧集剧本生成提示词
 */
public final class ScriptPromptHelper {

    private ScriptPromptHelper() {}

    /**
     * 构建大纲生成提示词
     *
     * @param summaryText 小说摘要文本（章节摘要或完整摘要）
     * @param title       剧本/小说标题
     * @return 完整 prompt
     */
    public static String buildOutlinePrompt(String summaryText, String title) {
        return "You are a professional screenwriter. Generate a complete comic drama outline based on the following novel summaries.\n\n"
                + "Title: " + title + "\n\n"
                + "Requirements:\n"
                + "1. Adapt into a comic/short drama outline, each episode about 3-5 minutes\n"
                + "2. Each episode should have clear structure: setup, development, climax, resolution\n"
                + "3. Separate each episode with \"---EPISODE---\"\n"
                + "4. Each episode format: Episode X: Title\\nPlot summary\\nCore conflict\\nCharacters\\nScenes\n\n"
                + "=== Novel Summaries ===\n" + summaryText + "\n\n"
                + "=== Please output the episode outline ===";
    }

    /**
     * 解析 AI 大纲输出为剧集列表
     * <p>
     * 以 "---EPISODE---" 为分隔符，空段跳过。
     * 如果解析结果为空，回退到整段文本作为一个剧集（更健壮的处理）。
     *
     * @param outlineText AI 生成的大纲文本
     * @return 剧集概要列表
     */
    public static List<String> parseOutlineToEpisodes(String outlineText) {
        String[] parts = outlineText.split("---EPISODE---");
        List<String> episodes = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                episodes.add(trimmed);
            }
        }
        // 健壮性回退：如果没有任何剧集，将整段文本作为唯一剧集
        if (episodes.isEmpty()) {
            episodes.add(outlineText);
        }
        return episodes;
    }

    /**
     * 构建剧集剧本生成提示词（带前文上下文）
     *
     * @param outline          整体大纲
     * @param previousEpisodes 前序剧集摘要
     * @param episodeNum       当前集号
     * @param episodeTitle     当前集标题
     * @return 完整 prompt
     */
    public static String buildEpisodeScriptPrompt(String outline, String previousEpisodes,
                                                   int episodeNum, String episodeTitle) {
        return "You are a professional comic drama screenwriter. Write the complete script for the following episode.\n\n"
                + "Requirements:\n"
                + "1. Script format: scene descriptions, character actions, dialogue\n"
                + "2. Scene markers: [Scene: xxx]\n"
                + "3. Dialogue format: Character Name: dialogue content\n"
                + "4. Action descriptions in (parentheses)\n"
                + "5. Suitable for 3-5 minute comic drama\n"
                + "6. Maintain continuity with previous episodes\n\n"
                + "=== Outline ===\n" + outline + "\n\n"
                + "=== Previous Episodes Summary ===\n" + previousEpisodes + "\n\n"
                + "Please write the complete script for Episode " + episodeNum + " \"" + episodeTitle + "\":";
    }

    /**
     * 构建剧集剧本生成提示词（NovelService 使用的简化版）
     *
     * @param allSummaries    小说全部章节摘要
     * @param episodeOutline  当前剧集概要
     * @param episodeNum      当前集号
     * @return 完整 prompt
     */
    public static String buildEpisodeScriptPromptFromSummary(String allSummaries, String episodeOutline, int episodeNum) {
        return "You are a professional comic drama screenwriter. Write the complete script for episode "
                + episodeNum + " based on the following information.\n\n"
                + "Requirements:\n"
                + "1. Script format with scene descriptions, character actions, and dialogue\n"
                + "2. Mark scenes with [Scene: xxx]\n"
                + "3. Dialogue format: Character Name: dialogue content\n"
                + "4. Action descriptions in (parentheses)\n"
                + "5. Suitable for 3-5 minute comic drama\n"
                + "6. Vivid language, tight pacing\n\n"
                + "=== Full Novel Summary ===\n" + allSummaries + "\n\n"
                + "=== Episode " + episodeNum + " Outline ===\n" + episodeOutline + "\n\n"
                + "=== Please output the complete script ===";
    }
}
