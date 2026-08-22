-- 将历史自动建表环境中的短文本列扩展为 LONGTEXT。
-- 真实 TradingAgents 角色提示词可超过 VARCHAR/TEXT 的历史限制；该变更不删除既有数据。
ALTER TABLE llm_prompt_version
    MODIFY COLUMN content LONGTEXT NOT NULL;

ALTER TABLE analysis_job_prompt_snapshot
    MODIFY COLUMN content LONGTEXT NOT NULL;
