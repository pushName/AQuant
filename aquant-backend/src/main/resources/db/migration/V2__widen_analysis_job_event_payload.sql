-- 分析事件可能携带 Python 事件摘要，不能使用 VARCHAR(255) 保存。
-- 已存在的数据库必须显式执行；新建表由 V1 或 Hibernate 直接使用 LONGTEXT。
ALTER TABLE analysis_job_event
  MODIFY COLUMN payload_json LONGTEXT NULL;
