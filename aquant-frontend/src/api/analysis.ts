import request from '@/utils/request';

/** 智能分析作业请求参数。 */
export interface AnalysisJobCreateRequest {
  date: string;
  tickers: string[];
  skipKronos?: boolean;
  streaming?: boolean;
  promptReleaseId?: number;
  modelConfig?: Record<string, unknown>;
}

export interface AnalysisJob {
  id: string;
  status: string;
  stage: string;
  date: string;
  tickers: string[];
  total: number;
  completed: number;
  failed: number;
  progress: number;
  pythonJobId?: string;
  errorMessage?: string;
  createdAt: string;
  updatedAt: string;
}

export interface AnalysisEvent {
  seq: number;
  type: string;
  stage?: string;
  role?: string;
  ticker?: string;
  status: string;
  completed?: number;
  total?: number;
  message?: string;
  timestamp: string;
}

export interface AnalysisRoleConclusion {
  status: string;
  conclusion: string;
  error?: string | null;
}

export type AnalysisOutcomeStatus = 'SELECTED' | 'NO_SELECTION' | 'PARTIAL';
export interface AnalysisSelection {
  selected: boolean;
  stage?: string;
  reasonCode?: string | null;
  reason?: string;
}

export interface AnalysisCommitteeDecision {
  recommendation?: string | null;
  confidence?: number | null;
  reasoning?: string;
  bullCase?: string;
  bearCase?: string;
  agentConsensus?: Record<string, unknown>;
  kronosDirection?: string | null;
  kronosUp?: boolean;
  kronosChangePct?: number | null;
  advisoryOnly?: boolean;
}

export interface AnalysisTickerConclusion {
  ticker: string;
  date?: string;
  roles: Record<string, AnalysisRoleConclusion>;
  committee?: AnalysisCommitteeDecision | null;
  selection?: AnalysisSelection | null;
  decision?: {
    signal?: string | null;
    confidence?: number | null;
    reasoning?: string;
  };
}

export interface AnalysisResultSummary {
  outcomeStatus?: AnalysisOutcomeStatus;
  requestedCount?: number;
  selectedCount?: number;
  analyzedCount?: number;
  rejectedCount?: number;
  cachePolicy?: 'REFRESH' | 'CACHE_ALLOWED';
  resultCount?: number;
  taCount?: number;
  kronosCount?: number;
  message?: string;
}

export interface AnalysisResult {
  results: unknown[];
  roleConclusions: AnalysisTickerConclusion[];
  summary?: AnalysisResultSummary;
}

export interface PromptTemplate {
  id: number;
  roleKey: string;
  templateType: string;
  description?: string;
  publishedVersion?: number;
}

export interface PromptVersion {
  id: number;
  roleKey: string;
  templateType: string;
  version: number;
  content: string;
  variables: string[];
  status: string;
  contentHash: string;
}

export function createAnalysisJob(data: AnalysisJobCreateRequest) {
  return request.post('/analysis/jobs', data);
}

export function listAnalysisJobs(page = 0, size = 20) {
  return request.get('/analysis/jobs', { params: { page, size } });
}

export function getAnalysisJob(jobId: string) {
  return request.get(`/analysis/jobs/${encodeURIComponent(jobId)}`);
}

export async function deleteAnalysisJob(jobId: string) {
  const response = await request.delete(`/analysis/jobs/${encodeURIComponent(jobId)}`);
  const payload = response.data as { success?: boolean; code?: number; message?: string } | undefined;
  if (payload?.success !== true) {
    const error = new Error(payload?.message || '分析作业删除失败，请稍后重试') as Error & {
      alreadyNotified?: boolean;
    };
    // 全局响应拦截器已经展示了标准业务错误，交互层只负责保留列表，
    // 避免同一失败重复弹出两次；非标准响应仍由页面兜底提示。
    error.alreadyNotified = payload?.success === false
      && payload.code !== undefined
      && payload.code !== 0
      && payload.code !== 200;
    throw error;
  }
  return response;
}

export function getAnalysisEvents(jobId: string, afterSeq = 0) {
  return request.get(`/analysis/jobs/${encodeURIComponent(jobId)}/events`, { params: { afterSeq } });
}

export function getAnalysisResults(jobId: string) {
  return request.get(`/analysis/jobs/${encodeURIComponent(jobId)}/results`);
}

export function cancelAnalysisJob(jobId: string) {
  return request.post(`/analysis/jobs/${encodeURIComponent(jobId)}/cancel`);
}

export function retryAnalysisJob(jobId: string) {
  return request.post(`/analysis/jobs/${encodeURIComponent(jobId)}/retry`);
}

export function listPromptTemplates() {
  return request.get('/analysis/prompts');
}

/** 从已运行的 Python 分析服务同步 TradingAgents 源码默认提示词。 */
export function importSourcePromptDefaults() {
  return request.post('/analysis/prompts/import-source-defaults');
}

export function listPromptVersions(roleKey: string, templateType = 'ROLE') {
  return request.get(`/analysis/prompts/${encodeURIComponent(roleKey)}/versions`, { params: { templateType } });
}

export function savePromptDraft(roleKey: string, content: string, variables: string[]) {
  return request.post(`/analysis/prompts/${encodeURIComponent(roleKey)}/draft`, {
    content,
    variables,
  });
}

export function publishPromptVersion(roleKey: string, version: number) {
  return request.post(`/analysis/prompts/${encodeURIComponent(roleKey)}/versions/${version}/publish`);
}

/** 使用 fetch 读取带 JWT 的 SSE，原生 EventSource 无法安全携带 Authorization。 */
export async function streamAnalysisEvents(
  jobId: string,
  afterSeq: number,
  onEvent: (event: AnalysisEvent) => void,
  signal: AbortSignal,
) {
  const token = localStorage.getItem('token');
  const response = await fetch(`/api/analysis/jobs/${encodeURIComponent(jobId)}/events/stream?afterSeq=${afterSeq}`, {
    headers: {
      Accept: 'text/event-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    signal,
  });
  if (!response.ok || !response.body) throw new Error(`进度连接失败（HTTP ${response.status}）`);
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split('\n\n');
    buffer = frames.pop() || '';
    for (const frame of frames) {
      const data = frame.split('\n').find((line) => line.startsWith('data:'))?.slice(5).trim();
      if (!data) continue;
      try { onEvent(JSON.parse(data) as AnalysisEvent); } catch { /* 忽略心跳和损坏帧 */ }
    }
  }
}
