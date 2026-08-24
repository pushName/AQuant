<template>
  <div class="analysis-page">
    <a-card :bordered="false" :loading="loading">
      <template #title>分析作业 {{ job?.id }}</template>
      <template #extra><a-button @click="router.back()">返回</a-button></template>
      <a-row :gutter="[16, 16]" align="top">
        <a-col :xs="24" :sm="12" :lg="6"><a-statistic title="当前阶段" :value="stageLabel(job?.stage)" /></a-col>
        <a-col :xs="24" :sm="12" :lg="6"><a-statistic title="完成进度" :value="job?.progress || 0" suffix="%" /></a-col>
        <a-col :xs="24" :sm="12" :lg="6"><a-statistic title="作业状态" :value="statusLabel(job?.status)" /></a-col>
        <a-col :xs="24" :sm="12" :lg="6">
          <div class="committee-panel">
            <div class="committee-stamp" :class="stampClass(selectedCommittee?.recommendation)">
              <span class="stamp-title">{{ selectedCommittee?.advisoryOnly ? '委员会参考' : '委员会审议' }}</span>
              <strong>{{ committeeRecommendationLabel }}</strong>
              <span v-if="selectedCommittee?.confidence != null" class="stamp-confidence">
                置信度 {{ formatConfidence(selectedCommittee.confidence) }}
              </span>
            </div>
            <a-tag v-if="selectedCommittee?.advisoryOnly" color="orange">未入选 · 参考结论</a-tag>
            <a-tag v-else-if="selectedConclusion?.selection?.selected" color="green">已入选 · 最终结论</a-tag>
            <a-tag v-if="selectedCommittee?.kronosUp" color="green">Kronos 预测上涨</a-tag>
            <a-button type="primary" :disabled="!finalResultItems.length" @click="reportOpen = true">
              <FileSearchOutlined />
              查看报告详情
            </a-button>
          </div>
        </a-col>
      </a-row>
      <a-alert
        v-if="result?.summary?.outcomeStatus"
        :type="outcomeAlertType"
        show-icon
        :message="outcomeTitle"
        :description="result.summary.message"
        style="margin-top: 16px"
      />
      <a-descriptions v-if="job" title="作业参数说明" :column="2" size="small" style="margin-top: 20px">
        <a-descriptions-item label="分析日期">{{ job.date }}（用于确定行情与新闻数据的时间点）</a-descriptions-item>
        <a-descriptions-item label="股票范围">{{ job.tickers.join('、') }}（共 {{ job.total }} 只）</a-descriptions-item>
        <a-descriptions-item label="成功/失败数量">{{ job.completed }} / {{ job.failed }}</a-descriptions-item>
        <a-descriptions-item label="Python 服务作业 ID">{{ job.pythonJobId || '等待创建' }}</a-descriptions-item>
      </a-descriptions>
      <a-progress :percent="job?.progress || 0" style="margin: 24px 0" />
      <a-descriptions v-if="result?.summary" title="筛选结果" :column="4" size="small" bordered>
        <a-descriptions-item label="请求股票">{{ result.summary.requestedCount ?? job?.total ?? '--' }} 只</a-descriptions-item>
        <a-descriptions-item label="完成分析">{{ result.summary.analyzedCount ?? result.summary.taCount ?? '--' }} 只</a-descriptions-item>
        <a-descriptions-item label="最终入选">{{ result.summary.selectedCount ?? result.summary.resultCount ?? 0 }} 只</a-descriptions-item>
        <a-descriptions-item label="未入选/异常">{{ result.summary.rejectedCount ?? '--' }} 只</a-descriptions-item>
      </a-descriptions>
      <a-alert
        v-if="selectedConclusion?.selection && !selectedConclusion.selection.selected"
        type="warning"
        show-icon
        :message="`当前股票未入选：${selectionReasonLabel(selectedConclusion.selection.reasonCode)}`"
        :description="selectedConclusion.selection.reason || '未提供具体原因'"
        style="margin-top: 16px"
      />
      <a-divider>角色进度说明</a-divider>
      <a-typography-paragraph type="secondary">角色按投研依赖关系执行；状态用于显示当前进展，不展示提示词内容、模型正文或任何凭据。</a-typography-paragraph>
      <a-row :gutter="[12, 12]">
        <a-col v-for="role in roles" :key="role" :xs="12" :sm="8" :md="6">
          <a-card size="small" class="role-card" @click="openRoleConclusion(role)">
            <a-space direction="vertical" size="small">
              <span>{{ roleLabel(role) }}</span>
              <a-typography-text type="secondary">{{ role }}</a-typography-text>
              <a-tag :color="roleStatus(role) === 'SUCCEEDED' ? 'green' : roleStatus(role) === 'RUNNING' ? 'blue' : roleStatus(role) === 'FAILED' ? 'red' : 'default'">{{ statusLabel(roleStatus(role)) }}</a-tag>
              <a-typography-text type="secondary">点击查看结论</a-typography-text>
            </a-space>
          </a-card>
        </a-col>
      </a-row>
      <a-select v-if="roleConclusions.length > 1" v-model:value="selectedTicker" :options="tickerOptions" style="width: 240px; margin-top: 16px" />
      <a-divider>执行事件</a-divider>
      <a-timeline>
        <a-timeline-item v-for="event in events" :key="event.seq">
          <span class="event-time">{{ formatTime(event.timestamp) }}</span>
          <strong>{{ event.type === 'ROLE_STATUS' ? '角色状态' : event.type === 'STAGE_STATUS' ? '阶段状态' : '作业状态' }}</strong>
          <span v-if="event.role"> · {{ roleLabel(event.role) }}</span>
          <span> · {{ event.message }}</span>
        </a-timeline-item>
      </a-timeline>
      <a-alert v-if="job?.errorMessage" type="error" :message="job.errorMessage" show-icon />
    </a-card>
    <a-modal v-model:open="conclusionOpen" :title="selectedRole ? `${roleLabel(selectedRole)}结论` : '分析师结论'" :footer="null" width="720px">
      <a-descriptions v-if="selectedConclusion" :column="2" size="small" bordered>
        <a-descriptions-item label="股票代码">{{ selectedConclusion.ticker }}</a-descriptions-item>
        <a-descriptions-item label="分析日期">{{ selectedConclusion.date || job?.date || '--' }}</a-descriptions-item>
        <a-descriptions-item label="角色状态"><a-tag :color="selectedRoleConclusion?.status === 'SUCCEEDED' ? 'green' : selectedRoleConclusion?.status === 'FAILED' ? 'red' : 'default'">{{ statusLabel(selectedRoleConclusion?.status) }}</a-tag></a-descriptions-item>
        <a-descriptions-item label="委员会建议">{{ signalLabel(selectedCommittee?.recommendation) }}</a-descriptions-item>
      </a-descriptions>
      <a-alert v-if="selectedRoleConclusion?.error" type="error" show-icon :message="selectedRoleConclusion.error" style="margin-top: 16px" />
      <MarkdownContent
        class="conclusion-content"
        :content="selectedRoleConclusion?.conclusion || '该角色尚未产生可展示的结论。'"
      />
    </a-modal>
    <a-modal v-model:open="reportOpen" title="委员会审议报告" :footer="null" width="960px">
      <template v-if="selectedConclusion">
        <div class="report-toolbar">
          <a-select
            v-if="roleConclusions.length > 1"
            v-model:value="selectedTicker"
            :options="tickerOptions"
            style="width: 240px"
          />
          <a-space wrap>
            <a-tag :color="signalColor(selectedCommittee?.recommendation)">
              {{ selectedCommittee?.advisoryOnly ? '委员会参考建议' : '委员会最终建议' }}：{{ signalLabel(selectedCommittee?.recommendation) }}
            </a-tag>
            <a-tag v-if="selectedCommittee?.kronosDirection" :color="selectedCommittee.kronosUp ? 'green' : 'default'">
              Kronos：{{ kronosDirectionLabel(selectedCommittee.kronosDirection) }}
            </a-tag>
          </a-space>
        </div>
        <a-descriptions :column="3" bordered size="small">
          <a-descriptions-item label="股票代码">{{ selectedConclusion.ticker }}</a-descriptions-item>
          <a-descriptions-item label="分析日期">{{ selectedConclusion.date || job?.date || '--' }}</a-descriptions-item>
          <a-descriptions-item label="委员会置信度">
            {{ selectedCommittee?.confidence != null ? formatConfidence(selectedCommittee.confidence) : '--' }}
          </a-descriptions-item>
          <a-descriptions-item label="通过最终筛选">
            {{ selectedConclusion.selection?.selected ? '是' : '否' }}
          </a-descriptions-item>
          <a-descriptions-item label="TA 完成">{{ result?.summary?.taCount ?? '--' }} 只</a-descriptions-item>
          <a-descriptions-item label="Kronos 完成">{{ result?.summary?.kronosCount ?? '--' }} 只</a-descriptions-item>
        </a-descriptions>
        <a-alert
          v-if="selectedConclusion.selection && !selectedConclusion.selection.selected"
          class="report-section"
          type="warning"
          show-icon
          :message="`该委员会结论仅供参考：${selectionReasonLabel(selectedConclusion.selection.reasonCode)}`"
          :description="selectedConclusion.selection.reason"
        />
        <a-alert
          v-if="!selectedCommittee"
          class="report-section"
          type="warning"
          show-icon
          message="当前作业结果未包含委员会审议数据；请使用更新后的 Python 分析服务重新运行作业。"
        />
        <template v-else>
          <a-divider>委员会审议理由</a-divider>
          <MarkdownContent :content="selectedCommittee.reasoning || '暂无委员会审议理由。'" />
          <a-row :gutter="16" class="report-section">
            <a-col :xs="24" :md="12">
              <section class="argument-section argument-bull">
                <h4>看多论点</h4>
                <MarkdownContent :content="selectedCommittee.bullCase || '无明显看多论点。'" />
              </section>
            </a-col>
            <a-col :xs="24" :md="12">
              <section class="argument-section argument-bear">
                <h4>看空论点</h4>
                <MarkdownContent :content="selectedCommittee.bearCase || '无明显看空论点。'" />
              </section>
            </a-col>
          </a-row>
        </template>
        <a-divider>各分析师报告</a-divider>
        <a-collapse>
          <a-collapse-panel v-for="entry in analystReasons(selectedConclusion)" :key="entry.role">
            <template #header>
              <a-space>
                <span>{{ roleLabel(entry.role) }}</span>
                <a-tag :color="entry.conclusion.status === 'SUCCEEDED' ? 'green' : 'red'">
                  {{ statusLabel(entry.conclusion.status) }}
                </a-tag>
              </a-space>
            </template>
            <a-alert v-if="entry.conclusion.error" type="error" show-icon :message="entry.conclusion.error" />
            <MarkdownContent
              v-if="entry.conclusion.conclusion"
              class="analyst-reason-content"
              :content="entry.conclusion.conclusion"
            />
          </a-collapse-panel>
        </a-collapse>
      </template>
      <a-empty v-else description="暂无可展示的报告" />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getAnalysisEvents, getAnalysisJob, getAnalysisResults, streamAnalysisEvents, type AnalysisEvent, type AnalysisJob, type AnalysisResult, type AnalysisRoleConclusion, type AnalysisTickerConclusion } from '@/api/analysis';
import { analysisRoleLabel, analysisStageLabel, analysisStatusLabel } from '@/constants/analysis';
import MarkdownContent from '@/components/MarkdownContent.vue';
import { FileSearchOutlined } from '@ant-design/icons-vue';

const route = useRoute();
const router = useRouter();
const jobId = String(route.params.jobId);
const job = ref<AnalysisJob | null>(null);
const result = ref<AnalysisResult | null>(null);
const events = ref<AnalysisEvent[]>([]);
const loading = ref(true);
const abortController = new AbortController();
const selectedTicker = ref('');
const selectedRole = ref('');
const conclusionOpen = ref(false);
const reportOpen = ref(false);
const roles = ['market', 'social', 'news', 'fundamentals', 'policy', 'hot_money', 'lockup', 'quality_gate', 'bull', 'bear', 'research_manager', 'trader', 'risk_aggressive', 'risk_neutral', 'risk_conservative', 'portfolio_manager'];
const roleLabel = analysisRoleLabel;
const stageLabel = analysisStageLabel;
const statusLabel = analysisStatusLabel;
const terminalStatuses = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED']);
const normalizeResult = (payload: unknown): AnalysisResult | null => {
  if (Array.isArray(payload)) return { results: payload, roleConclusions: [], summary: { resultCount: payload.length } };
  if (!payload || typeof payload !== 'object') return null;
  const value = payload as Partial<AnalysisResult>;
  if (!('results' in value) && !('roleConclusions' in value) && !('summary' in value)) return null;
  return {
    results: Array.isArray(value.results) ? value.results : [],
    roleConclusions: Array.isArray(value.roleConclusions) ? value.roleConclusions : [],
    summary: value.summary,
  };
};
const roleConclusions = computed(() => result.value?.roleConclusions || []);
const tickerOptions = computed(() => roleConclusions.value.map((item) => ({ value: item.ticker, label: item.ticker })));
const selectedConclusion = computed<AnalysisTickerConclusion | undefined>(() => roleConclusions.value.find((item) => item.ticker === selectedTicker.value));
const selectedRoleConclusion = computed<AnalysisRoleConclusion | undefined>(() => selectedRole.value ? selectedConclusion.value?.roles[selectedRole.value] : undefined);
const finalResultItems = computed(() => roleConclusions.value);
const selectedCommittee = computed(() => selectedConclusion.value?.committee || undefined);
const outcomeTitle = computed(() => {
  const status = result.value?.summary?.outcomeStatus;
  return status === 'NO_SELECTION' ? '分析完成，但没有股票入选'
    : status === 'PARTIAL' ? '分析完成，但部分股票未完成'
      : status === 'SELECTED' ? '分析完成，已有股票入选' : '分析结果';
});
const outcomeAlertType = computed(() => result.value?.summary?.outcomeStatus === 'SELECTED' ? 'success' : 'warning');
const selectionReasonLabel = (code?: string | null) => ({
  TA_ERROR: 'TA 分析失败',
  SIGNAL_NOT_ALLOWED: '信号不在允许范围',
  CONFIDENCE_BELOW_THRESHOLD: '置信度低于阈值',
  DATA_INSUFFICIENT: '数据不足',
  DELISTED: '已退市',
  ABNORMAL_STOCK: '异常股票',
  METADATA_FILTER: '未通过元数据筛选',
  PIPELINE_ERROR: '流水线未生成结果',
}[String(code || '')] || code || '未通过最终筛选');
const signalLabels: Record<string, string> = {
  BUY: '买入',
  STRONG_BUY: '买入',
  SELL: '卖出',
  STRONG_SELL: '卖出',
  HOLD: '持有',
  WAIT: '持有',
  KRONOS_UP: 'Kronos上涨',
};
const signalLabel = (value?: string | null) => {
  const normalized = String(value || '').trim().toUpperCase();
  if (signalLabels[normalized]) return signalLabels[normalized];
  if (value && /买入|看多/.test(value)) return '买入';
  if (value && /卖出|看空/.test(value)) return '卖出';
  if (value && /持有|保持|观望|等待/.test(value)) return '持有';
  return value || '暂无建议';
};
const signalColor = (value?: string | null) => {
  const label = signalLabel(value);
  return label === '买入' ? 'green' : label === '卖出' ? 'red' : label === '持有' ? 'orange' : label === 'Kronos上涨' ? 'cyan' : 'default';
};
const committeeRecommendationLabel = computed(() => selectedCommittee.value
  ? signalLabel(selectedCommittee.value.recommendation)
  : '待审议');
const stampClass = (value?: string | null) => {
  const label = signalLabel(value);
  return label === '买入' ? 'stamp-buy' : label === '卖出' ? 'stamp-sell' : label === '持有' ? 'stamp-hold' : label === 'Kronos上涨' ? 'stamp-kronos' : 'stamp-pending';
};
const kronosDirectionLabel = (value?: string | null) => {
  const normalized = String(value || '').trim().toUpperCase();
  return normalized === 'UP' ? '预测上涨' : normalized === 'DOWN' ? '预测下跌' : normalized === 'FLAT' ? '预测横盘' : value || '--';
};
const formatConfidence = (value: number) => `${value <= 1 ? Math.round(value * 100) : Math.round(value)}%`;
const analystReasons = (item: AnalysisTickerConclusion) => roles
  .map((role) => ({ role, conclusion: item.roles?.[role] }))
  .filter((entry): entry is { role: string; conclusion: AnalysisRoleConclusion } => Boolean(entry.conclusion?.conclusion || entry.conclusion?.error));
watch(roleConclusions, (items) => {
  if (!items.some((item) => item.ticker === selectedTicker.value)) selectedTicker.value = items[0]?.ticker || '';
}, { immediate: true });

const load = async () => {
  const [jobResponse, eventResponse, resultResponse] = await Promise.all([getAnalysisJob(jobId), getAnalysisEvents(jobId), getAnalysisResults(jobId)]);
  job.value = jobResponse.data.data;
  events.value = eventResponse.data.data || [];
  result.value = normalizeResult(resultResponse.data.data);
  loading.value = false;
};
const refreshResult = async () => {
  const response = await getAnalysisResults(jobId);
  result.value = normalizeResult(response.data.data);
};
const roleStatus = (role: string) => {
  const conclusionStatus = selectedConclusion.value?.roles[role]?.status;
  if (conclusionStatus) return conclusionStatus;
  const roleEvents = events.value.filter((event) => event.role === role);
  return roleEvents.length ? (roleEvents[roleEvents.length - 1]?.status || 'PENDING') : 'PENDING';
};
const openRoleConclusion = (role: string) => {
  selectedRole.value = role;
  conclusionOpen.value = true;
};
const formatTime = (value: string) => value ? new Date(value).toLocaleTimeString() : '--';
onMounted(async () => {
  await load();
  try {
    await streamAnalysisEvents(jobId, events.value.length ? (events.value[events.value.length - 1]?.seq || 0) : 0, (event) => {
      events.value.push(event);
      void getAnalysisJob(jobId).then((response) => { job.value = response.data.data; });
      if (event.type === 'JOB_STATUS' && terminalStatuses.has(event.status)) void refreshResult();
    }, abortController.signal);
    if (!abortController.signal.aborted) await refreshResult();
  } catch { /* 页面刷新或服务结束时使用已加载的事件 */ }
});
onBeforeUnmount(() => abortController.abort());
</script>

<style scoped>
.analysis-page { padding: 8px; }
.event-time { color: #87909c; margin-right: 8px; }
.role-card { cursor: pointer; height: 100%; }
.role-card:hover { border-color: #1677ff; }
.conclusion-content { margin-top: 16px; }
.committee-panel { align-items: center; display: flex; flex-direction: column; gap: 8px; min-height: 150px; }
.committee-stamp { align-items: center; border: 3px solid #8c8c8c; border-radius: 50%; display: flex; flex-direction: column; height: 116px; justify-content: center; line-height: 1.15; transform: rotate(-7deg); width: 116px; }
.committee-stamp .stamp-title { font-size: 12px; }
.committee-stamp strong { font-size: 24px; margin: 4px 0; }
.committee-stamp .stamp-confidence { font-size: 11px; }
.committee-stamp.stamp-buy { border-color: #cf1322; color: #cf1322; }
.committee-stamp.stamp-hold { border-color: #d48806; color: #d48806; }
.committee-stamp.stamp-sell { border-color: #389e0d; color: #389e0d; }
.committee-stamp.stamp-kronos { border-color: #0891b2; color: #0891b2; }
.committee-stamp.stamp-pending { border-color: #8c8c8c; color: #8c8c8c; }
.report-toolbar { align-items: center; display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; margin-bottom: 16px; }
.report-section { margin-top: 16px; }
.argument-section { border-left: 3px solid #d9d9d9; padding: 0 12px; }
.argument-section h4 { margin: 0 0 8px; }
.argument-bull { border-left-color: #389e0d; }
.argument-bear { border-left-color: #cf1322; }
.analyst-reason-content { margin-top: 8px; }
.analyst-reason-header { align-items: center; display: flex; gap: 8px; margin-bottom: 6px; }
.analyst-reason-content { margin-top: 8px; }
</style>
