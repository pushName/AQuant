-- analysis_job 的 JSON 列在 Hibernate 6 自动建表的库上是 TINYTEXT(255 字节)。
-- 真实分析结果（result_json）远超 255 字节，写入时报
-- "could not execute statement; SQL [n/a]"（Data too long for column），
-- 导致 Python 侧已成功的分析被后端标为 FAILED。
-- V1 新建表使用 LONGTEXT；已存在的数据库必须显式执行本脚本（同 V2）。
ALTER TABLE analysis_job
  MODIFY COLUMN tickers_json LONGTEXT NOT NULL,
  MODIFY COLUMN config_json LONGTEXT NULL,
  MODIFY COLUMN result_json LONGTEXT NULL;
