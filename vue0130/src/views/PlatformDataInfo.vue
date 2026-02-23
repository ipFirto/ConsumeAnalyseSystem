<template>
  <section class="platform-info-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>{{ text.platform }}{{ platformName || '-' }} | {{ text.date }}{{ dateParam || '-' }}</p>
        </div>
        <div class="panel-actions">
          <el-button @click="goBack">{{ text.backPlatform }}</el-button>
        </div>
      </header>

      <div v-if="loading" class="state">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loading }}</span>
      </div>

      <div v-else-if="error" class="state error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <template v-else>
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ text.orderCount }}</div>
            <div class="value">{{ stats.orderCount }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.totalAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.totalAmount) }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.maxAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.maxAmount) }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.avgAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.avgAmount) }}</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-title">{{ text.chartTitle }}</div>
          <div ref="chartRef" class="chart"></div>
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  title: '平台日期明细',
  platform: '平台：',
  date: '日期：',
  backPlatform: '返回平台消费数据',
  loading: '正在加载明细数据...',
  missingParams: '缺少日期或平台参数',
  fetchFailed: '获取平台明细失败',
  empty: '当前日期无数据',
  chartTitle: '金额趋势（按时间排序）',
  orderNo: '订单号',
  amount: '金额',
  orderCount: '订单数',
  totalAmount: '总金额',
  maxAmount: '最高金额',
  avgAmount: '平均金额',
};

const route = useRoute();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const sourceRows = ref<PlatformRecord[]>([]);
const loading = ref(false);
const error = ref('');
const chartRef = ref<HTMLDivElement | null>(null);

let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let requestSerial = 0;
const MAX_VISIBLE_POINTS = 500;

function queryString(raw: unknown) {
  if (Array.isArray(raw)) return String(raw[0] ?? '').trim();
  if (raw == null) return '';
  return String(raw).trim();
}

const platformId = computed(() => {
  const raw = queryString(route.query.platformId ?? route.query.platform);
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
});

const dateParam = computed(() => queryString(route.query.date));

const platformName = computed(
  () => platformList.value.find((item) => item.id === platformId.value)?.name || ''
);

function toDateKey(raw: string) {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return String(raw || '-');
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function toTimestamp(raw: string) {
  const value = new Date(raw).getTime();
  return Number.isFinite(value) ? value : 0;
}

function toTimeLabel(raw: string) {
  const d = new Date(raw);
  if (Number.isNaN(d.getTime())) return raw;
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  const ss = String(d.getSeconds()).padStart(2, '0');
  return `${hh}:${mm}:${ss}`;
}

const dayRows = computed(() => {
  if (!dateParam.value) return [];
  return sourceRows.value.filter((row) => toDateKey(row.co_created_at) === dateParam.value);
});

const chartRows = computed(() => {
  return [...dayRows.value].sort((a, b) => toTimestamp(a.co_created_at) - toTimestamp(b.co_created_at));
});

const stats = computed(() => {
  const orderCount = dayRows.value.length;
  const totalAmount = dayRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0);
  const maxAmount = dayRows.value.reduce((max, row) => Math.max(max, Number(row.amount || 0)), 0);
  const avgAmount = orderCount ? totalAmount / orderCount : 0;
  return { orderCount, totalAmount, maxAmount, avgAmount };
});

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function ensureChart() {
  if (!chartRef.value) return false;
  if (chartRef.value.clientWidth === 0 || chartRef.value.clientHeight === 0) return false;
  if (chart && chart.getDom() !== chartRef.value) {
    chart.dispose();
    chart = null;
  }
  if (!chart) {
    chart = echarts.init(chartRef.value);
  }
  return true;
}

function bindChartClick() {
  if (!chart) return;
  chart.off('click');
  chart.on('click', (params: any) => {
    const axisValue = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    let index = Number(params?.dataIndex);
    if (!Number.isFinite(index) || index < 0) {
      index = chartRows.value.findIndex((row) => toTimeLabel(row.co_created_at) === axisValue);
    }
    if (!Number.isFinite(index) || index < 0) return;
    chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex: index });
    if (axisValue) {
      chart?.dispatchAction({ type: 'updateAxisPointer', xAxisIndex: 0, value: axisValue });
    }
    const row = chartRows.value[index];
    const orderId = Number(row?.co_id);
    if (!Number.isFinite(orderId) || orderId <= 0) return;

    const query: Record<string, string> = {
      orderId: String(orderId),
      backTo: 'platformDate',
    };
    if (platformId.value) query.platformId = String(platformId.value);
    if (dateParam.value) query.date = dateParam.value;

    void router.push({
      name: 'HomeAllOrderDetail',
      query,
    });
  });
}

function renderChart() {
  if (!ensureChart() || !chart) return;

  const rows = chartRows.value;
  if (!rows.length) {
    chart.off('click');
    chart.setOption(
      {
        title: { text: text.empty, left: 'center', top: 'middle' },
        xAxis: { show: false },
        yAxis: { show: false },
        series: [],
      },
      true
    );
    return;
  }

  chart.setOption(
    {
      grid: { left: 48, right: 24, top: 40, bottom: 56 },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'line', snap: true },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const index = Number(p?.dataIndex ?? 0);
          const row = rows[index];
          if (!row) return '';
          return `${row.co_created_at}<br/>${text.orderNo}: ${row.co_order_no}<br/>${text.amount}: CNY ${formatAmount(
            Number(row.amount || 0)
          )}`;
        },
      },
      xAxis: {
        type: 'category',
        name: '时间',
        data: rows.map((row) => toTimeLabel(row.co_created_at)),
        axisLabel: { hideOverlap: true, rotate: 28 },
      },
      yAxis: {
        type: 'value',
        name: '金额(CNY)',
      },
      series: [
        {
          name: text.amount,
          type: 'line',
          smooth: true,
          cursor: 'pointer',
          showSymbol: false,
          data: rows.map((row) => Number(row.amount || 0)),
          areaStyle: { opacity: 0.12 },
          lineStyle: { width: 2 },
        },
      ],
      dataZoom:
        rows.length > MAX_VISIBLE_POINTS
          ? [
              {
                type: 'inside',
                xAxisIndex: 0,
                startValue: rows.length - MAX_VISIBLE_POINTS,
                endValue: rows.length - 1,
                maxValueSpan: MAX_VISIBLE_POINTS,
              },
              {
                type: 'slider',
                xAxisIndex: 0,
                startValue: rows.length - MAX_VISIBLE_POINTS,
                endValue: rows.length - 1,
                maxValueSpan: MAX_VISIBLE_POINTS,
                height: 14,
                bottom: 10,
              },
            ]
          : [],
    },
    true
  );
  bindChartClick();
}

async function loadData() {
  const reqId = ++requestSerial;
  const pId = platformId.value;
  const date = dateParam.value;

  if (!pId || !date) {
    error.value = text.missingParams;
    sourceRows.value = [];
    await nextTick();
    renderChart();
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    const res = await getPlatformData(pId);
    if (reqId !== requestSerial) return;
    if (res.code !== 200) {
      throw new Error(res.msg || text.fetchFailed);
    }
    sourceRows.value = res.data || [];
    if (!dayRows.value.length) {
      error.value = text.empty;
    }
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    sourceRows.value = [];
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
    await nextTick();
    renderChart();
  }
}

function goBack() {
  const query: Record<string, string> = {
    restorePlatform: '1',
  };
  if (platformId.value) {
    query.platformId = String(platformId.value);
  }
  void router.push({
    name: 'HomePlatform',
    query,
  });
}

function handleResize() {
  chart?.resize();
}

watch(
  () => [platformId.value, dateParam.value],
  () => {
    void loadData();
  },
  { immediate: true }
);

watch(
  chartRows,
  async () => {
    await nextTick();
    renderChart();
  },
  { deep: true }
);

onMounted(async () => {
  platformList.value = await loadPlatformList();
  window.addEventListener('resize', handleResize);
  await nextTick();
  renderChart();
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      handleResize();
    });
    resizeObserver.observe(chartRef.value);
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.off('click');
  chart?.dispose();
  chart = null;
});
</script>

<style scoped>
.platform-info-page {
  padding: 24px;
  min-height: 100dvh;
  background:
    radial-gradient(85rem 56rem at -15% -20%, rgba(74, 150, 255, 0.23), rgba(74, 150, 255, 0)),
    radial-gradient(78rem 52rem at 110% 120%, rgba(14, 184, 166, 0.15), rgba(14, 184, 166, 0)),
    linear-gradient(145deg, #eff6ff 0%, #dbeafe 100%);
}

.panel {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 18px;
  padding: 18px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  color: #1e3a5f;
}

.panel-header p {
  margin: 8px 0 0;
  color: #567299;
  font-size: 14px;
}

.state {
  min-height: 220px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #4d6a91;
}

.state.error {
  color: #d9534f;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 10px 0 16px;
}

.stat-card {
  border-radius: 12px;
  padding: 12px;
  border: 1px solid rgba(37, 99, 235, 0.15);
  background: rgba(255, 255, 255, 0.8);
}

.stat-card .label {
  color: #5b6f8d;
  font-size: 13px;
}

.stat-card .value {
  margin-top: 6px;
  color: #1f3f67;
  font-size: 22px;
  font-weight: 700;
}

.chart-card {
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px 6px;
  margin: 4px 0 14px;
}

.chart-title {
  font-size: 14px;
  color: #35577f;
  margin: 2px 0 8px;
}

.chart {
  width: 100%;
  height: 360px;
}

@keyframes spin {
  from {
    transform: rotate(0);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .platform-info-page {
    padding: 12px;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .chart {
    height: 300px;
  }
}
</style>
