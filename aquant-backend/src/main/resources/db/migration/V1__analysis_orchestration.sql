-- AQuant 智能分析编排与提示词管理首版迁移。
-- 生产环境请在备份后显式执行；开发环境仍可由 Hibernate ddl-auto=update 自动建表。
CREATE TABLE IF NOT EXISTS analysis_job (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  status VARCHAR(20) NOT NULL,
  stage VARCHAR(30) NOT NULL,
  analysis_date DATE NOT NULL,
  tickers_json LONGTEXT NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  completed_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  progress INT NOT NULL DEFAULT 0,
  python_job_id VARCHAR(64),
  prompt_release_id BIGINT,
  prompt_snapshot_hash VARCHAR(128),
  config_json LONGTEXT,
  result_json LONGTEXT,
  error_message VARCHAR(2000),
  created_by BIGINT,
  created_username VARCHAR(100),
  cancel_requested BIT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  started_at DATETIME,
  finished_at DATETIME,
  KEY idx_analysis_job_status (status),
  KEY idx_analysis_job_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analysis_job_event (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  sequence_no BIGINT NOT NULL,
  source_seq BIGINT,
  type VARCHAR(30) NOT NULL,
  stage VARCHAR(30),
  role VARCHAR(80),
  ticker VARCHAR(80),
  status VARCHAR(30),
  completed INT,
  total INT,
  message VARCHAR(1000),
  payload_json LONGTEXT,
  event_at DATETIME NOT NULL,
  UNIQUE KEY uk_analysis_job_event_seq (job_id, sequence_no),
  KEY idx_analysis_job_event_job (job_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS llm_prompt_template (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_key VARCHAR(80) NOT NULL,
  template_type VARCHAR(30) NOT NULL,
  description VARCHAR(200),
  published_version INT,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  UNIQUE KEY uk_llm_prompt_template_key_type (role_key, template_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS llm_prompt_version (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  template_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  content LONGTEXT NOT NULL,
  variables_json TEXT,
  content_hash VARCHAR(128) NOT NULL,
  status VARCHAR(20) NOT NULL,
  created_by BIGINT,
  created_at DATETIME NOT NULL,
  published_at DATETIME,
  UNIQUE KEY uk_llm_prompt_version (template_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analysis_job_prompt_snapshot (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  role_key VARCHAR(80) NOT NULL,
  template_type VARCHAR(30) NOT NULL,
  version_id BIGINT,
  version_no INT,
  content LONGTEXT NOT NULL,
  content_hash VARCHAR(128) NOT NULL,
  UNIQUE KEY uk_analysis_prompt_snapshot (job_id, role_key, template_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analysis_job_result (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  job_id VARCHAR(64) NOT NULL,
  ticker VARCHAR(80),
  ta_result_json LONGTEXT,
  kronos_result_json LONGTEXT,
  merged_result_json LONGTEXT,
  decision_json LONGTEXT,
  error_message VARCHAR(2000),
  UNIQUE KEY uk_analysis_job_result (job_id, ticker)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
