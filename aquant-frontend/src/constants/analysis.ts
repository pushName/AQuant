/** 智能分析页面使用的中文展示名称，接口仍保留稳定的英文枚举值。 */
export const ANALYSIS_STATUS_LABELS: Record<string, string> = {
  QUEUED: '排队中',
  RUNNING: '执行中',
  CANCELLING: '正在取消',
  CANCELLED: '已取消',
  SUCCEEDED: '已成功',
  FAILED: '失败',
  PENDING: '待执行',
};

export const ANALYSIS_STAGE_LABELS: Record<string, string> = {
  DATA_PREPARING: '数据准备',
  TA_ANALYSTS: '投研分析师',
  KRONOS: 'Kronos 预测',
  MERGING: '结果融合',
  COMMITTEE: '投委会审议',
  PERSISTING: '结果保存',
  COMPLETED: '已完成',
};

export const ANALYSIS_ROLE_LABELS: Record<string, string> = {
  market: '技术面分析师',
  social: '市场情绪分析师',
  news: '新闻事件分析师',
  fundamentals: '基本面分析师',
  policy: '政策分析师',
  hot_money: '资金流分析师',
  lockup: '限售解禁分析师',
  quality_gate: '数据质量门控',
  bull: '多头研究员',
  bear: '空头研究员',
  research_manager: '研究经理',
  trader: '交易员',
  risk_aggressive: '激进风险分析师',
  risk_neutral: '中性风险分析师',
  risk_conservative: '保守风险分析师',
  portfolio_manager: '组合经理',
};

/** 提示词变量的业务含义、适用范围和 Python 服务的实际替换能力。 */
export const PROMPT_VARIABLES = [
  { key: 'ticker', title: '股票代码', description: '本次作业正在分析的标准股票代码，例如 sh.600990。', scope: '全部角色', injected: true },
  { key: 'date', title: '分析日期', description: '创建作业时选择的分析日期，用于确定行情、新闻和财务数据的时间点。', scope: '全部角色', injected: true },
  { key: 'current_date', title: '当前图日期', description: 'TradingAgents 图执行时使用的当前日期；当前与分析日期一致。', scope: '全部角色', injected: true },
  { key: 'reports', title: '分析报告集合', description: '已生成的市场、情绪、新闻、基本面、政策、资金、解禁和质量报告的合并文本。', scope: '质量门控、辩论、交易与决策角色', injected: true },
  { key: 'messages', title: '消息历史', description: '当前图最近 20 条消息或工具调用结果的文本摘要。', scope: '工具型分析师、辩论角色', injected: true },
  { key: 'tool_names', title: '工具名称列表', description: '当前角色可调用的数据工具名；非工具角色显示“当前角色不使用工具”。', scope: '七位工具型分析师为主', injected: true },
  { key: 'debate_round', title: '辩论轮次', description: '当前研究辩论或风险辩论的轮次。非辩论节点不能使用该变量。', scope: '多空研究员、研究经理、风险分析师、组合经理', injected: true },
] as const;

export const analysisStatusLabel = (value?: string) => ANALYSIS_STATUS_LABELS[value || ''] || value || '--';
export const analysisStageLabel = (value?: string) => ANALYSIS_STAGE_LABELS[value || ''] || value || '--';
export const analysisRoleLabel = (value?: string) => ANALYSIS_ROLE_LABELS[value || ''] || value || '--';
