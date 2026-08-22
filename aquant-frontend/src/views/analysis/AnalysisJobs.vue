<template>
  <div class="analysis-page">
    <a-card :bordered="false" title="智能分析作业">
      <a-alert type="info" show-icon message="作业将依次完成数据准备、投研分析、Kronos 预测、结果融合和投委会审议。"
        description="创建后使用启动时固定的提示词快照；后续编辑提示词不会影响已在运行的作业。" style="margin-bottom: 16px" />
      <template #extra><a-button type="primary" @click="showCreate = true">创建作业</a-button></template>
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { cancelAnalysisJob, createAnalysisJob, listAnalysisJobs, retryAnalysisJob, type AnalysisJob } from '@/api/analysis';
import { analysisStageLabel, analysisStatusLabel } from '@/constants/analysis';

const router = useRouter();
const jobs = ref<AnalysisJob[]>([]);
const loading = ref(false);
const creating = ref(false);
const showCreate = ref(false);
const pagination = reactive({ current: 1, pageSize: 20, total: 0 });
const form = reactive({ date: '', tickers: '', skipKronos: false, streaming: false });

const load = async () => {
  loading.value = true;
  try {
    const response = await listAnalysisJobs(pagination.current - 1, pagination.pageSize);
    const page = response.data.data;
    jobs.value = page.content || [];
    pagination.total = page.totalElements || 0;
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
const terminal = (status: string) => ['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status);
const statusColor = (status: string) => status === 'SUCCEEDED' ? 'green' : status === 'FAILED' ? 'red' : status === 'CANCELLED' ? 'default' : 'blue';
const statusLabel = analysisStatusLabel;
const stageLabel = analysisStageLabel;
onMounted(load);
</script>

<style scoped>
.analysis-page { padding: 8px; }
</style>
