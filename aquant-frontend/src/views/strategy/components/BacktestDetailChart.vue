<template>
  <div class="backtest-detail">
    <a-spin :spinning="loading">
      <div v-if="detail" class="backtest-summary">
        <div class="backtest-summary__metrics">
          <div>
            <span class="summary-label">初始资金</span>
            <strong>{{ formatMoney(detail.initialCapital) }}</strong>
          </div>
          <div>
            <span class="summary-label">最终资金</span>
            <strong :class="returnClass">{{ formatMoney(detail.finalCapital) }}</strong>
          </div>
          <div>
            <span class="summary-label">累计收益</span>
            <strong :class="returnClass">{{ formatPercent(totalReturn) }}</strong>
          </div>
          <div>
            <span class="summary-label">交易信号</span>
            <strong>{{ detail.tradePoints.length }} 次</strong>
          </div>
        </div>
        <div class="backtest-range-field">
          <span class="summary-label">回测时间</span>
          <a-range-picker
            v-model:value="selectedRange"
            class="backtest-range-picker"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            :allow-clear="false"
            @change="loadDetail"
          />
        </div>
      </div>
      <div v-show="detail" ref="chartRef" class="backtest-chart" />
      <a-empty v-if="!loading && !detail" description="暂无可回测的历史行情" />
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue';
import { message } from 'ant-design-vue';
import * as echarts from 'echarts';
import dayjs from 'dayjs';
import {
  getStockStrategyBacktestDetail,
  type StockStrategyBacktestDetailReqVO,
  type StockStrategyBacktestDetailVO,
} from '@/api/stock';

const props = defineProps<{
  request: StockStrategyBacktestDetailReqVO;
}>();

const chartRef = ref<HTMLElement>();
const detail = shallowRef<StockStrategyBacktestDetailVO>();
const loading = ref(false);
const createDefaultRange = (years: number): [string, string] => [
  dayjs().subtract(years, 'year').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
];
const selectedRange = ref<[string, string]>(createDefaultRange(props.request.recentYears));
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let loadVersion = 0;

const totalReturn = computed(() => detail.value
  ? detail.value.finalCapital / detail.value.initialCapital - 1
  : 0);
const returnClass = computed(() => totalReturn.value >= 0 ? 'value-up' : 'value-down');

const formatMoney = (value: number) => `¥${value.toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`;
const formatPercent = (value: number) => `${value >= 0 ? '+' : ''}${(value * 100).toFixed(2)}%`;

const signalData = (signal: 'BUY' | 'SELL', valueKey: 'price' | 'capital', divisor = 1) => detail.value!.tradePoints
  .filter(point => point.signal === signal)
  .map(point => ({
    name: `${signal === 'BUY' ? '买' : '卖'}${point.sequence}`,
    value: [point.tradeDate, point[valueKey] / divisor],
    label: { formatter: `${signal === 'BUY' ? '买' : '卖'}${point.sequence}` },
  }));

const renderChart = () => {
  if (!chartRef.value || !detail.value) return;
  chart?.dispose();
  chart = echarts.init(chartRef.value);
  const dates = detail.value.tradeDates;
  const stockChartLineColors = ['#3B82F6', '#F59E0B', '#EC4899', '#10B981'];
  const indicatorSeries = detail.value.indicatorSeries.map((series, index) => {
    const lineColor = series.name === 'DIF'
      ? '#F59E0B'
      : series.name === 'DEA'
        ? '#3B82F6'
        : stockChartLineColors[index % stockChartLineColors.length];
    return {
      name: series.name,
      type: series.type,
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: series.values,
      symbol: 'none',
      smooth: false,
      lineStyle: { width: 1.2, color: lineColor },
      itemStyle: series.type === 'bar'
        ? { color: (params: any) => Number(params.value) >= 0 ? '#EF4444' : '#10B981' }
        : { color: lineColor },
      barMaxWidth: 8,
      z: series.type === 'bar' ? 1 : 3 + index,
    };
  });
  const scatterBase = {
    type: 'scatter',
    symbolSize: 12,
    label: { show: true, distance: 5, fontSize: 10 },
    tooltip: { valueFormatter: (value: number) => Number(value).toFixed(2) },
    z: 10,
  };

  chart.setOption({
    animation: false,
    color: stockChartLineColors,
    legend: [
      { top: 2, right: 28, data: ['收盘价', '买入点', '卖出点'] },
      { top: '40%', right: 28, data: detail.value.indicatorSeries.map(item => item.name) },
      { top: '70%', right: 28, data: ['资金曲线', '初始资金'] },
    ],
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'cross',
        lineStyle: { type: 'dashed' },
        label: {
          backgroundColor: '#ffffff',
          borderColor: '#e2e8f0',
          borderWidth: 1,
          borderRadius: 4,
          color: '#64748b',
          padding: [3, 6],
          shadowBlur: 0,
        },
      },
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      { left: 68, right: 28, top: 42, height: '27%' },
      { left: 68, right: 28, top: '44%', height: '20%' },
      { left: 68, right: 28, top: '74%', bottom: 52 },
    ],
    xAxis: [0, 1, 2].map(index => ({
      type: 'category',
      gridIndex: index,
      data: dates,
      boundaryGap: false,
      axisLabel: { show: index === 2, color: '#94a3b8', hideOverlap: true },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
    })),
    yAxis: [
      { type: 'value', gridIndex: 0, scale: true, name: '股价', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7', type: 'dashed' } } },
      { type: 'value', gridIndex: 1, scale: true, name: '策略', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7', type: 'dashed' } } },
      { type: 'value', gridIndex: 2, scale: true, name: '资金（万元）', axisLabel: { color: '#94a3b8' }, splitLine: { lineStyle: { color: '#eef2f7', type: 'dashed' } } },
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1, 2], start: 0, end: 100 },
      {
        type: 'slider',
        xAxisIndex: [0, 1, 2],
        show: true,
        left: 68,
        right: 28,
        bottom: 8,
        height: 8,
        start: 0,
        end: 100,
        borderColor: 'transparent',
        backgroundColor: '#f1f5f9',
        fillerColor: 'rgba(148, 163, 184, 0.45)',
        handleSize: '100%',
        handleStyle: {
          color: '#94a3b8',
          borderColor: '#cbd5e1',
        },
        moveHandleSize: 0,
        showDetail: false,
        showDataShadow: false,
        zoomLock: false,
      },
    ],
    series: [
      { name: '收盘价', type: 'line', xAxisIndex: 0, yAxisIndex: 0, data: detail.value.closePrices, symbol: 'none', itemStyle: { color: '#3B82F6' }, lineStyle: { width: 1.8, color: '#3B82F6' } },
      { ...scatterBase, name: '买入点', xAxisIndex: 0, yAxisIndex: 0, symbol: 'triangle', itemStyle: { color: '#EF4444' }, label: { ...scatterBase.label, position: 'top', color: '#DC2626' }, data: signalData('BUY', 'price') },
      { ...scatterBase, name: '卖出点', xAxisIndex: 0, yAxisIndex: 0, symbol: 'triangle', symbolRotate: 180, itemStyle: { color: '#10B981' }, label: { ...scatterBase.label, position: 'bottom', color: '#059669' }, data: signalData('SELL', 'price') },
      ...indicatorSeries,
      { name: '资金曲线', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: detail.value.capitalValues.map(value => value / 10000), symbol: 'none', itemStyle: { color: '#3B82F6' }, lineStyle: { width: 1.8, color: '#3B82F6' }, areaStyle: { color: 'rgba(59, 130, 246, 0.10)' } },
      { name: '初始资金', type: 'line', xAxisIndex: 2, yAxisIndex: 2, data: dates.map(() => detail.value!.initialCapital / 10000), symbol: 'none', itemStyle: { color: '#94a3b8' }, lineStyle: { width: 1, color: '#94a3b8', type: 'dashed' } },
      { ...scatterBase, name: '资金买点', xAxisIndex: 2, yAxisIndex: 2, symbol: 'triangle', itemStyle: { color: '#EF4444' }, label: { show: false }, data: signalData('BUY', 'capital', 10000) },
      { ...scatterBase, name: '资金卖点', xAxisIndex: 2, yAxisIndex: 2, symbol: 'triangle', symbolRotate: 180, itemStyle: { color: '#10B981' }, label: { show: false }, data: signalData('SELL', 'capital', 10000) },
    ],
  });

  resizeObserver?.disconnect();
  resizeObserver = new ResizeObserver(() => chart?.resize());
  resizeObserver.observe(chartRef.value);
};

const loadDetail = async () => {
  const currentVersion = ++loadVersion;
  loading.value = true;
  detail.value = undefined;
  try {
    const response = await getStockStrategyBacktestDetail({
      ...props.request,
      startDate: selectedRange.value[0],
      endDate: selectedRange.value[1],
    });
    if (currentVersion !== loadVersion) return;
    if (response.data.success || response.data.code === 0) {
      detail.value = response.data.data;
      await nextTick();
      renderChart();
    }
  } catch (error: any) {
    if (currentVersion === loadVersion) {
      message.error(error?.response?.data?.message || '加载回测详情失败');
    }
  } finally {
    if (currentVersion === loadVersion) {
      loading.value = false;
    }
  }
};

watch(() => props.request, request => {
  selectedRange.value = createDefaultRange(request.recentYears);
  loadDetail();
}, { immediate: true, deep: true });

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
});
</script>

<style scoped>
.backtest-detail { height: 100%; min-height: 0; }
:deep(.ant-spin-nested-loading),
:deep(.ant-spin-container) { height: 100%; }
.backtest-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 36px;
  min-height: 56px;
  padding: 10px 18px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #f8fafc;
}
.backtest-summary__metrics { display: flex; gap: 36px; }
.backtest-summary__metrics > div,
.backtest-range-field { display: flex; flex-direction: column; gap: 3px; }
.backtest-range-picker { width: 248px; }
.summary-label { color: #64748b; font-size: 12px; }
.backtest-summary strong { color: #1e293b; font-variant-numeric: tabular-nums; }
.backtest-chart { width: 100%; height: calc(100% - 66px); min-height: 0; margin-top: 10px; }
.value-up { color: #dc2626 !important; }
.value-down { color: #059669 !important; }
@media (max-width: 768px) {
  .backtest-summary { align-items: flex-start; gap: 16px; overflow-x: auto; }
  .backtest-summary__metrics { gap: 16px; }
  .backtest-summary__metrics > div { min-width: 100px; }
  .backtest-range-field { min-width: 248px; }
  .backtest-chart { height: calc(100% - 66px); min-height: 0; }
}
</style>
