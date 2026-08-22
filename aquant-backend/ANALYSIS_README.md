# AQuant 智能分析编排模块

## 启动顺序

1. 启动 `D:\wolf\LLM\llm-gateway`，确认网关健康检查可用。
2. 在 trade-krono-cli 虚拟环境启动 Python 服务：

   ```powershell
   .venv\Scripts\python.exe -m uvicorn trade_krono_cli.service.app:app --host 127.0.0.1 --port 8000
   ```

3. 配置 AQuant 后端环境变量并启动 Spring Boot：

   - `ANALYSIS_PYTHON_BASE_URL`：Python 服务地址，默认 `http://127.0.0.1:8000`。
   - `ANALYSIS_EXECUTOR_THREADS`：Java 作业线程数，默认 `2`。
   - `AQUANT_DB_URL`、`AQUANT_DB_USERNAME`、`AQUANT_DB_PASSWORD`：MySQL 连接参数。

## 数据库迁移

生产环境先执行 `src/main/resources/db/migration/V1__analysis_orchestration.sql`，再执行 `src/main/resources/db/migration/V2__expand_prompt_content.sql`，然后启动后端。`V2` 会将历史自动建表环境的提示词正文列扩展为 `LONGTEXT`，不会删除已有数据。开发环境也应显式执行 `V2`；`spring.jpa.hibernate.ddl-auto=update` 不保证会自动扩展既有列类型。

## 接口与恢复

Java 对页面提供 `/analysis/jobs` 和 `/analysis/prompts` 接口；页面使用 JWT 调用 SSE。Python 作业事件持久化在 `ANALYSIS_SERVICE_DB` 指定的 SQLite 文件中，Java 重启后会根据 Python 作业 ID 和 `afterSeq` 继续拉取事件。提示词在创建作业时复制到快照表，发布新版本不会改变运行中的作业。

作业完成后，`GET /analysis/jobs/{jobId}/results` 返回的结果包含 `results`、`roleConclusions` 和 `summary`。`results` 是通过最终筛选的股票；即使它为空，`roleConclusions` 仍保留每只股票、每个分析角色的可展示结论。前端在收到终态 SSE 后必须重新拉取该接口，避免首次进入详情页时缓存到未完成的空结果。

## 提示词源码同步与变量

首次使用提示词管理页面时，先启动 Python 分析服务，再在“提示词管理”页面点击“同步 Python 默认提示词”。后端会调用 Python 的 `GET /v1/analysis/prompts/catalog`，将 TradingAgents 当前源码中的 16 个角色提示词导入 MySQL。

- 同步只替换系统首次生成的通用占位模板；人工发布的版本不会被覆盖。
- 每次同步为导入角色创建新版本，并将此前的通用占位版本归档。
- 作业创建后会固定提示词快照，因此后续同步、编辑或发布不会影响运行中的作业。
- 可在页面使用 `ticker`、`date`、`current_date`、`reports`、`messages`、`tool_names` 和 `debate_round`。其中 `debate_round` 只能用于投研或风险辩论节点；缺少运行时值时 Python 会拒绝发送未替换的提示词。

日志只允许记录作业状态、阶段、角色和脱敏错误摘要，禁止输出上游 Key、Bearer Token、完整提示词及模型正文。
