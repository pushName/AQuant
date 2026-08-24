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

分析作业页面支持两种创建方式：手工输入股票代码，或选择股票类型的自选分组批量提交。批量入口读取现有 `/stockWatchlist/group/list?type=STOCK` 和 `/stockWatchlist/stock/list`，去重后复用同一个创建接口，单次仍最多 500 只股票；基金分组不会提交给股票分析服务。

终态作业可通过 `DELETE /analysis/jobs/{jobId}` 删除。删除会先幂等清理 Python 对应的 `service_jobs/service_events`，成功后再清理 Java 侧作业事件、提示词快照和作业记录；TA/Kronos/行情缓存保留。Python 服务不可用时 Java 记录不会删除，运行中的作业必须先取消，且接口会校验当前用户与作业创建者一致。

作业完成后，`GET /analysis/jobs/{jobId}/results` 返回的结果包含 `results`、`roleConclusions` 和 `summary`。`results` 是通过最终筛选的股票；即使它为空，`roleConclusions` 仍保留每只股票、每个分析角色的可展示结论。前端在收到终态 SSE 后必须重新拉取该接口，避免首次进入详情页时缓存到未完成的空结果。

## 提示词源码同步与变量

打开提示词管理页面时，前端会调用 Python 的 `GET /v1/analysis/prompts/catalog`，将 TradingAgents 当前源码中的 16 个角色提示词同步到 MySQL；也可点击“同步 Python 默认提示词”手动重试。Python 服务不可用时页面继续展示数据库已有版本并给出警告。

- 同步会替换通用占位模板，也会在 Python 源码变化时更新此前的源码同步版本；人工编辑或发布的模板不会被覆盖。
- 源码内容未变化时不会重复创建版本；变化时创建新发布版本并归档旧源码版本。
- 作业创建后会固定提示词快照，因此后续同步、编辑或发布不会影响运行中的作业。
- 页面编辑的是角色提示词正文；Python 运行时还会拼接固定的多角色协作外壳、工具列表、日期和证券代码上下文，并把消息历史作为独立消息传给模型。
- 可在页面使用 `ticker`、`date`、`current_date`、`reports`、`messages`、`tool_names` 和 `debate_round`。其中 `debate_round` 只能用于投研或风险辩论节点；缺少运行时值时 Python 会拒绝发送未替换的提示词。

日志只允许记录作业状态、阶段、角色和脱敏错误摘要，禁止输出上游 Key、Bearer Token、完整提示词及模型正文。
