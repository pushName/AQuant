<template>
  <div class="analysis-page">
    <a-card :bordered="false" title="智能分析作业">
      <a-alert type="info" show-icon message="作业将依次完成数据准备、投研分析、Kronos 预测、结果融合和投委会审议。"
        description="创建后使用启动时固定的提示词快照；后续编辑提示词不会影响已在运行的作业。" style="margin-bottom: 16px" />
      <template #extra>
        <a-space>
          <a-button @click="openBatchModal">按自选分组分析</a-button>
          <a-button type="primary" @click="showCreate = true">创建作业</a-button>
        </a-space>
      </template>
      <a-table :data-source="jobs" :loading="loading" :pagination="pagination" row-key="id" @change="changePage">
        <a-table-column title="作业" data-index="id" ellipsis />
        <a-table-column title="日期" data-index="date" />
        <a-table-column title="股票数" data-index="total" />
        <a-table-column title="当前阶段" key="stage"><template #default="{ record }">{{ stageLabel(record.stage) }}</template></a-table-column>
        <a-table-column title="进度" key="progress">
          <template #default="{ record }"><a-progress :percent="record.progress" size="small" /></template>
        </a-table-column>
        <a-table-column title="状态" key="status">
          <template #default="{ record }"><a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag></template>
        </a-table-column>
        <a-table-column title="操作" key="action">
          <template #default="{ record }">
            <a-space>
              <a-button type="link" @click="router.push(`/analysis/jobs/${record.id}`)">详情</a-button>
              <a-button v-if="!terminal(record.status)" type="link" danger @click="cancel(record.id)">取消</a-button>
              <a-button v-if="record.status === 'FAILED' || record.status === 'CANCELLED'" type="link" @click="retry(record.id)">重试</a-button>
              <a-popconfirm
                v-if="terminal(record.status)"
                title="确认删除这个终态作业？删除后不可恢复。"
                ok-text="删除"
                cancel-text="取消"
                @confirm="remove(record.id)"
              >
                <a-button type="link" danger :loading="deletingId === record.id">删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table-column>
      </a-table>
    </a-card>

    <a-modal v-model:open="showCreate" title="创建智能分析作业" :confirm-loading="creating" @ok="create">
      <a-form layout="vertical">
        <a-form-item label="分析日期" required extra="选择用于获取行情、财务和新闻数据的交易日；非交易日会由数据源按其规则处理。"><a-date-picker v-model:value="form.date" value-format="YYYY-MM-DD" style="width:100%" /></a-form-item>
        <a-form-item label="股票代码" required extra="支持每行一个或使用逗号分隔，例如 sh.600990、sz.000001；单次最多 500 只。"><a-textarea v-model:value="form.tickers" placeholder="例如：sh.600990&#10;sz.000001" :rows="4" /></a-form-item>
        <a-form-item label="预测与输出选项" extra="Kronos 用历史 K 线生成价格趋势预测；流式模式会保留流式数据处理路径，但不会改变页面的作业进度展示。">
          <a-space direction="vertical"><a-checkbox v-model:checked="form.skipKronos">跳过 Kronos 价格趋势预测</a-checkbox><a-checkbox v-model:checked="form.streaming">启用流式数据处理模式</a-checkbox></a-space>
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal v-model:open="showBatch" title="按自选分组批量分析" :width="760" :confirm-loading="batchCreating" @ok="createFromGroup">
      <a-form layout="vertical">
        <a-form-item label="分析日期" required>
          <a-date-picker v-model:value="batchForm.date" value-format="YYYY-MM-DD" style="width:100%" />
        </a-form-item>
        <a-form-item label="股票自选分组" required>
          <a-select
            v-model:value="batchForm.groupId"
            :loading="groupsLoading"
            placeholder="请选择股票自选分组"
            style="width:100%"
            @change="loadBatchStocks"
          >
            <a-select-option v-for="group in stockGroups" :key="group.id" :value="group.id">
              {{ group.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-alert
          v-if="batchForm.groupId"
          type="info"
          show-icon
          :message="`当前分组包含 ${batchStocks.length} 只股票`"
          description="只会提交股票自选分组中的股票，不会分析基金分组；单次最多 500 只。"
        />
        <div v-if="batchForm.groupId" class="batch-stock-list">
          <a-spin v-if="batchStocksLoading" tip="正在加载分组股票..." class="batch-stock-loading" />
          <a-table
            v-else-if="batchStocks.length"
            :data-source="batchStocks"
            :pagination="false"
            :scroll="{ y: 360 }"
            row-key="stockCode"
            size="small"
            bordered
          >
            <a-table-column title="序号" key="index" width="72" align="center">
              <template #default="{ index }">{{ index + 1 }}</template>
            </a-table-column>
            <a-table-column title="股票代码" data-index="stockCode" key="stockCode" width="220" />
            <a-table-column title="股票名称" key="stockName">
              <template #default="{ record }">{{ record.stockName || '--' }}</template>
            </a-table-column>
          </a-table>
          <a-empty v-else description="该分组暂无股票" />
        </div>
        <a-form-item label="预测与输出选项" style="margin-top: 16px">
          <a-space direction="vertical">
            <a-checkbox v-model:checked="batchForm.skipKronos">跳过 Kronos 价格趋势预测</a-checkbox>
            <a-checkbox v-model:checked="batchForm.streaming">启用流式数据处理模式</a-checkbox>
          </a-space>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { cancelAnalysisJob, createAnalysisJob, deleteAnalysisJob, listAnalysisJobs, retryAnalysisJob, type AnalysisJob } from '@/api/analysis';
import { getWatchlistGroups, getWatchlistStocks, type WatchlistGroupVO, type WatchlistStockVO } from '@/api/watchlist';
import { analysisStageLabel, analysisStatusLabel } from '@/constants/analysis';

const router = useRouter();
const jobs = ref<AnalysisJob[]>([]);
const loading = ref(false);
const creating = ref(false);
const showCreate = ref(false);
const showBatch = ref(false);
const batchCreating = ref(false);
const groupsLoading = ref(false);
const batchStocksLoading = ref(false);
const deletingId = ref('');
const stockGroups = ref<WatchlistGroupVO[]>([]);
const batchStocks = ref<WatchlistStockVO[]>([]);
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });
const form = reactive({ date: '', tickers: '', skipKronos: false, streaming: false });
const batchForm = reactive<{ date: string; groupId?: number; skipKronos: boolean; streaming: boolean }>({ date: '', skipKronos: false, streaming: false });
const batchTickerCodes = computed(() => Array.from(new Set(batchStocks.value.map((stock) => stock.stockCode).filter(Boolean))));

const load = async () => {
  loading.value = true;
  try {
    const response = await listAnalysisJobs(pagination.current - 1, pagination.pageSize);
    const page = response.data.data;
    jobs.value = page.content || [];
    pagination.total = page.totalElements || 0;
    if (!jobs.value.length && pagination.current > 1 && pagination.total > 0) {
      pagination.current -= 1;
      await load();
    }
  } finally { loading.value = false; }
};
const changePage = (page: { current?: number; pageSize?: number }) => { pagination.current = page.current || 1; pagination.pageSize = page.pageSize || 20; load(); };
const create = async () => {
  const tickers = form.tickers.split(/[,\s]+/).map((item) => item.trim()).filter(Boolean);
  if (!form.date || !tickers.length) { message.warning('请输入日期和股票代码'); return; }
  creating.value = true;
  try { await createAnalysisJob({ date: form.date, tickers, skipKronos: form.skipKronos, streaming: form.streaming }); showCreate.value = false; message.success('作业已创建'); await load(); }
  finally { creating.value = false; }
};
const cancel = async (id: string) => { await cancelAnalysisJob(id); message.success('已请求取消'); await load(); };
const retry = async (id: string) => { await retryAnalysisJob(id); message.success('已创建重试作业'); await load(); };
const remove = async (id: string) => {
  deletingId.value = id;
  try {
    await deleteAnalysisJob(id);
    message.success('作业已删除');
    await load();
  } catch (error: any) {
    // HTTP 错误和标准业务错误已由全局拦截器提示；这里只处理 API
    // 返回非标准结构等本地校验错误，避免重复 toast。
    if (!error?.response && !error?.isAuthFailure && !error?.alreadyNotified) {
      message.error(error?.message || '作业删除失败，请稍后重试');
    }
  }
  finally { deletingId.value = ''; }
};
const openBatchModal = async () => {
  showBatch.value = true;
  batchForm.groupId = undefined;
  batchStocks.value = [];
  groupsLoading.value = true;
  try {
    const response = await getWatchlistGroups('STOCK');
    stockGroups.value = response.data.data || [];
  } finally { groupsLoading.value = false; }
};
const loadBatchStocks = async (groupId: number) => {
  if (!groupId) {
    batchStocks.value = [];
    return;
  }
  batchStocksLoading.value = true;
  batchStocks.value = [];
  try {
    const response = await getWatchlistStocks(groupId);
    batchStocks.value = (response.data.data || []).filter((stock) => stock.targetType !== 'FUND');
  } finally { batchStocksLoading.value = false; }
};
const createFromGroup = async () => {
  if (!batchForm.date || !batchForm.groupId) { message.warning('请选择分析日期和股票自选分组'); return; }
  if (batchStocksLoading.value) { message.info('正在加载分组股票，请稍候'); return; }
  if (!batchTickerCodes.value.length) { message.warning('当前分组暂无可分析的股票'); return; }
  if (batchTickerCodes.value.length > 500) { message.warning('单次最多分析 500 只股票'); return; }
  batchCreating.value = true;
  try {
    await createAnalysisJob({ date: batchForm.date, tickers: batchTickerCodes.value, skipKronos: batchForm.skipKronos, streaming: batchForm.streaming });
    showBatch.value = false;
    message.success(`已创建 ${batchTickerCodes.value.length} 只股票的批量分析作业`);
    await load();
  } finally { batchCreating.value = false; }
};
const terminal = (status: string) => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status);
const statusColor = (status: string) => status === 'SUCCEEDED' ? 'green' : status === 'FAILED' ? 'red' : status === 'CANCELLED' ? 'default' : 'blue';
const statusLabel = analysisStatusLabel;
const stageLabel = analysisStageLabel;
onMounted(load);
</script>

<style scoped>
.analysis-page { padding: 8px; }
.batch-stock-list { margin-top: 16px; }
.batch-stock-loading { display: block; padding: 48px 0; text-align: center; }
</style>
