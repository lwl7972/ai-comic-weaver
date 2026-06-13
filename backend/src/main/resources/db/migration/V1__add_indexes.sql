-- AI Comic Platform Database Indexes
-- 用于优化查询性能

-- Script module indexes
CREATE INDEX IF NOT EXISTS idx_script_project ON script(project_id);
CREATE INDEX IF NOT EXISTS idx_script_status ON script(status);

-- Episode module indexes
CREATE INDEX IF NOT EXISTS idx_episode_script ON episode(script_id);
CREATE INDEX IF NOT EXISTS idx_episode_number ON episode(episode_number);

-- Character module indexes
CREATE INDEX IF NOT EXISTS idx_character_project ON character(project_id);
CREATE INDEX IF NOT EXISTS idx_character_name ON character(name);

-- Scene module indexes
CREATE INDEX IF NOT EXISTS idx_scene_project ON scene(project_id);

-- Storyboard module indexes
CREATE INDEX IF NOT EXISTS idx_storyboard_episode ON storyboard(episode_id);
CREATE INDEX IF NOT EXISTS idx_storyboard_sequence ON storyboard(sequence);
CREATE INDEX IF NOT EXISTS idx_storyboard_status ON storyboard(status);

-- Generation task indexes
CREATE INDEX IF NOT EXISTS idx_task_project ON generation_task(project_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON generation_task(status);
CREATE INDEX IF NOT EXISTS idx_task_target ON generation_task(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_task_created ON generation_task(created_at);

-- Novel module indexes
CREATE INDEX IF NOT EXISTS idx_novel_project ON novel(project_id);
CREATE INDEX IF NOT EXISTS idx_novel_status ON novel(status);

-- Chapter summary indexes
CREATE INDEX IF NOT EXISTS idx_chapter_summary_novel ON chapter_summary(novel_id);
CREATE INDEX IF NOT EXISTS idx_chapter_summary_index ON chapter_summary(chapter_index);

-- Extracted asset indexes
CREATE INDEX IF NOT EXISTS idx_extracted_asset_project ON extracted_asset(project_id);
CREATE INDEX IF NOT EXISTS idx_extracted_asset_type ON extracted_asset(asset_type);
CREATE INDEX IF NOT EXISTS idx_extracted_asset_confirmed ON extracted_asset(is_confirmed);

-- Pipeline state indexes
CREATE INDEX IF NOT EXISTS idx_pipeline_project ON pipeline_state(project_id);

-- Model config indexes
CREATE INDEX IF NOT EXISTS idx_model_config_type ON model_config(type);
CREATE INDEX IF NOT EXISTS idx_model_config_enabled ON model_config(is_enabled);

-- Prompt template indexes
CREATE INDEX IF NOT EXISTS idx_prompt_template_category ON prompt_template(category);
CREATE INDEX IF NOT EXISTS idx_prompt_template_preset ON prompt_template(is_preset);

-- Asset item indexes
CREATE INDEX IF NOT EXISTS idx_asset_project ON asset_item(project_id);
CREATE INDEX IF NOT EXISTS idx_asset_type ON asset_item(type);

-- Version history indexes
CREATE INDEX IF NOT EXISTS idx_version_history_object ON version_history(object_type, object_id);

-- Backup record indexes
CREATE INDEX IF NOT EXISTS idx_backup_project ON backup_record(project_id);

-- Update sqlite statistics
ANALYZE;
