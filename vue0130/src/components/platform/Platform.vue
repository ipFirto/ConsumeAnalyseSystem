<template>
  <section class="platform-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>
            {{ text.currentPlatform }}
            {{ selectedPlatformName }}
            <span v-if="records.length">| {{ text.totalRecords }} {{ records.length }}</span>
          </p>
        </div>
        <div class="header-actions">
          <el-select
            v-model="selectedPlatformId"
            style="width: 190px"
            :placeholder="text.selectPlatform"
            @change="onPlatformChange"
          >
            <el-option v-for="item in platformList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </div>
      </header>

      <div v-if="loading && !records.length" class="state">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loading }}</span>
      </div>

      <div v-else-if="error && !records.length" class="state error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <template v-else-if="records.length">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ text.dayCount }}</div>
            <div class="value">{{ stats.dayCount }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.totalOrders }}</div>
            <div class="value">{{ stats.totalOrders }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.totalAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.totalAmount) }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.categoryCount }}</div>
            <div class="value">{{ stats.categoryCount }}</div>
          </div>
        </div>

        <div class="chart-card">
          <div class="chart-title">{{ text.trendTitle }}</div>
          <div v-if="loading" class="chart-loading-mask">
            <el-icon class="loading-icon"><Loading /></el-icon>
            <span>{{ text.loading }}</span>
          </div>
          <div ref="trendRef" class="trend-chart"></div>
        </div>

        <div class="filter-bar">
          <el-input v-model.trim="filters.date" :placeholder="text.filterDate" clearable />
          <el-input v-model.trim="filters.keyword" placeholder="搜索订单号 / 用户 / 邮箱 / 商品" clearable />
          <el-input v-model.trim="filters.category" :placeholder="text.filterCategory" clearable />
          <el-input-number
            v-model="filters.minAmount"
            :min="0"
            :precision="2"
            :placeholder="text.filterMinAmount"
            controls-position="right"
          />
          <el-input-number
            v-model="filters.maxAmount"
            :min="0"
            :precision="2"
            :placeholder="text.filterMaxAmount"
            controls-position="right"
          />
          <el-button @click="resetFilters">{{ text.resetFilter }}</el-button>
        </div>

        <el-table
          :data="pagedRows"
          border
          stripe
          height="520"
          :default-sort="{ prop: 'co_created_at', order: 'descending' }"
          :row-class-name="tableRowClassName"
          @row-click="onTableRowClick"
        >
          <el-table-column prop="co_created_at" label="下单时间" min-width="170" sortable />
          <el-table-column prop="co_order_no" label="订单号" min-width="190" />
          <el-table-column prop="product_name" label="商品" min-width="170" />
          <el-table-column prop="p_brand" label="品牌/分类" min-width="130" />
          <el-table-column label="金额" min-width="130" align="right" sortable>
            <template #default="{ row }">CNY {{ formatAmount(Number(row.amount || 0)) }}</template>
          </el-table-column>
          <el-table-column prop="user_name" label="用户" min-width="120" />
          <el-table-column prop="user_email" label="邮箱" min-width="190" />
          <el-table-column prop="pr_name" label="省份" min-width="110" />
          <el-table-column prop="c_name" label="城市" min-width="110" />
        </el-table>

        <div class="pager-wrap">
          <span class="pager-info">筛选后记录：{{ filteredRows.length }}</span>
          <el-pagination
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :page-sizes="[20, 50, 100, 200]"
            layout="total, sizes, prev, pager, next"
            :total="filteredRows.length"
          />
        </div>
      </template>

      <div v-else class="state">
        <el-empty :description="text.empty" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  title: '平台消费数据',
  selectPlatform: '选择平台',
  currentPlatform: '当前平台：',
  totalRecords: '共记录：',
  refresh: '刷新',
  loading: '正在加载平台数据...',
  dayCount: '统计天数',
  totalOrders: '订单总数',
  totalAmount: '总消费金额',
  categoryCount: '分类数',
  trendTitle: '按日期趋势（按日期升序）',
  date: '日期',
  category: '分类(品牌)',
  orderCount: '订单量',
  totalAmountCol: '分类总金额',
  avgAmount: '分类均单额',
  empty: '暂无平台数据',
  fetchFailed: '获取平台数据失败',
  uncategorized: '未分类',
  amountLine: '每日消费金额',
  orderLine: '每日订单量',
  filterDate: '按日期筛选（如 2026-02）',
  filterCategory: '按分类筛选',
  filterMinOrder: '最小订单量',
  filterMaxOrder: '最大订单量',
  filterMinAmount: '最小总金额',
  filterMaxAmount: '最大总金额',
  filterMinAvg: '最小均单额',
  filterMaxAvg: '最大均单额',
  resetFilter: '重置筛选',
};

const props = defineProps<{
  platformId: number | null;
  visible?: boolean;
}>();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const selectedPlatformId = ref<number | null>(props.platformId ?? null);
const records = ref<PlatformRecord[]>([]);
const loading = ref(false);
const error = ref('');
const createDefaultFilters = () => ({
  date: '',
  keyword: '',
  category: '',
  minAmount: undefined as number | undefined,
  maxAmount: undefined as number | undefined,
});
const filters = ref(createDefaultFilters());
const appliedFilters = ref(createDefaultFilters());
const page = ref({
  current: 1,
  size: 20,
});
const PLATFORM_VIEW_STATE_KEY = 'platform:view-state';

const trendRef = ref<HTMLDivElement | null>(null);
let trendChart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let requestSerial = 0;
let filterTimer: number | null = null;
const platformMounted = ref(false);

const selectedPlatformName = computed(
  () => platformList.value.find((x) => x.id === selectedPlatformId.value)?.name || '-'
);

interface PlatformViewState {
  selectedPlatformId: number | null;
  filters: ReturnType<typeof createDefaultFilters>;
  appliedFilters: ReturnType<typeof createDefaultFilters>;
  page: { current: number; size: number };
}

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

function getCategory(row: PlatformRecord) {
  if (row.p_brand?.trim()) return row.p_brand.trim();
  if (Number.isFinite(Number(row.pr_type))) return `类型-${row.pr_type}`;
  return text.uncategorized;
}

const dailyRowsMap = computed(() => {
  const map = new Map<string, { orderCount: number; totalAmount: number }>();
  for (const row of records.value) {
    const date = toDateKey(row.co_created_at);
    const old = map.get(date) || { orderCount: 0, totalAmount: 0 };
    old.orderCount += 1;
    old.totalAmount += Number(row.amount || 0);
    map.set(date, old);
  }
  return map;
});

const sortedDates = computed(() => Array.from(dailyRowsMap.value.keys()).sort((a, b) => a.localeCompare(b)));

const filteredRows = computed(() => {
  const f = appliedFilters.value;
  const keyword = String(f.keyword || '').trim().toLowerCase();
  const categoryKeyword = String(f.category || '').trim().toLowerCase();

  return records.value
    .filter((row) => {
      const dateKey = toDateKey(row.co_created_at);
      if (f.date && !dateKey.includes(f.date)) return false;

      const category = getCategory(row).toLowerCase();
      if (categoryKeyword && !category.includes(categoryKeyword)) return false;

      if (keyword) {
        const haystack = [
          row.co_order_no,
          row.product_name,
          row.user_name,
          row.user_email,
          row.p_brand,
          row.pr_name,
          row.c_name,
        ]
          .map((x) => String(x || '').toLowerCase())
          .join(' ');
        if (!haystack.includes(keyword)) return false;
      }

      const amount = Number(row.amount || 0);
      if (f.minAmount !== undefined && amount < f.minAmount) return false;
      if (f.maxAmount !== undefined && amount > f.maxAmount) return false;
      return true;
    })
    .sort((a, b) => toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at));
});

const pagedRows = computed(() => {
  const start = (page.value.current - 1) * page.value.size;
  return filteredRows.value.slice(start, start + page.value.size);
});

const chartRowsMap = computed(() => {
  const map = new Map<string, { orderCount: number; totalAmount: number }>();
  for (const row of filteredRows.value) {
    const date = toDateKey(row.co_created_at);
    const old = map.get(date) || { orderCount: 0, totalAmount: 0 };
    old.orderCount += 1;
    old.totalAmount += Number(row.amount || 0);
    map.set(date, old);
  }
  return map;
});

const chartDates = computed(() => Array.from(chartRowsMap.value.keys()).sort((a, b) => a.localeCompare(b)));

const stats = computed(() => {
  const totalOrders = records.value.length;
  const totalAmount = records.value.reduce((sum, item) => sum + Number(item.amount || 0), 0);
  const dayCount = sortedDates.value.length;
  const categoryCount = new Set(records.value.map(getCategory)).size;
  return { totalOrders, totalAmount, dayCount, categoryCount };
});

function formatAmount(v: number) {
  return Number(v || 0).toFixed(2);
}

function saveViewState() {
  try {
    const payload: PlatformViewState = {
      selectedPlatformId: selectedPlatformId.value,
      filters: { ...filters.value },
      appliedFilters: { ...appliedFilters.value },
      page: { ...page.value },
    };
    sessionStorage.setItem(PLATFORM_VIEW_STATE_KEY, JSON.stringify(payload));
  } catch {
    // ignore storage errors
  }
}

function restoreViewState() {
  try {
    const raw = sessionStorage.getItem(PLATFORM_VIEW_STATE_KEY);
    if (!raw) return false;
    sessionStorage.removeItem(PLATFORM_VIEW_STATE_KEY);
    const parsed = JSON.parse(raw) as Partial<PlatformViewState>;
    const base = createDefaultFilters();
    if (typeof parsed.selectedPlatformId === 'number' && parsed.selectedPlatformId > 0) {
      selectedPlatformId.value = parsed.selectedPlatformId;
    }
    filters.value = { ...base, ...(parsed.filters || {}) };
    appliedFilters.value = { ...base, ...(parsed.appliedFilters || parsed.filters || {}) };
    page.value.current = Math.max(1, Number(parsed.page?.current || 1));
    page.value.size = Math.max(1, Number(parsed.page?.size || 20));
    return true;
  } catch {
    sessionStorage.removeItem(PLATFORM_VIEW_STATE_KEY);
    return false;
  }
}

function ensureChart() {
  if (!trendRef.value) return false;
  if (trendRef.value.clientWidth === 0 || trendRef.value.clientHeight === 0) return false;
  if (trendChart && trendChart.getDom() !== trendRef.value) {
    trendChart.dispose();
    trendChart = null;
  }
  if (!trendChart) {
    trendChart = echarts.init(trendRef.value);
  }
  return true;
}

function bindTrendChartClick() {
  if (!trendChart) return;
  trendChart.off('click');
  trendChart.on('click', (params: any) => {
    const date = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    if (!date || !selectedPlatformId.value) return;
    const dataIndex = Number(params?.dataIndex);
    if (Number.isFinite(dataIndex) && dataIndex >= 0) {
      trendChart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex });
      trendChart?.dispatchAction({ type: 'updateAxisPointer', xAxisIndex: 0, value: date });
    }
    saveViewState();
    void router.push({
      name: 'HomePlatformDate',
      query: {
        date,
        platformId: String(selectedPlatformId.value),
      },
    });
  });
}

function renderTrendChart() {
  if (!ensureChart() || !trendChart) return;

  const dates = chartDates.value;
  const dailyAmount = dates.map((date) => Number(chartRowsMap.value.get(date)?.totalAmount || 0));
  const dailyOrders = dates.map((date) => Number(chartRowsMap.value.get(date)?.orderCount || 0));

  if (!dates.length) {
    trendChart.setOption(
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

  trendChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'line', snap: true } },
      legend: { top: 4, data: [text.amountLine, text.orderLine] },
      grid: { left: 66, right: 66, top: 42, bottom: 44 },
      xAxis: {
        type: 'category',
        data: dates,
        axisLabel: { rotate: 30, hideOverlap: true },
      },
      yAxis: [{ type: 'value', name: 'CNY' }, { type: 'value', name: text.orderCount }],
      series: [
        {
          name: text.amountLine,
          type: 'line',
          smooth: true,
          cursor: 'pointer',
          data: dailyAmount,
          areaStyle: { opacity: 0.13 },
        },
        {
          name: text.orderLine,
          type: 'bar',
          cursor: 'pointer',
          yAxisIndex: 1,
          barMaxWidth: 32,
          data: dailyOrders,
        },
      ],
    },
    true
  );

  bindTrendChartClick();
  requestAnimationFrame(() => trendChart?.resize());
}

async function loadData() {
  const reqId = ++requestSerial;
  if (!selectedPlatformId.value) {
    if (reqId !== requestSerial) return;
    records.value = [];
    error.value = '';
    await nextTick();
    renderTrendChart();
    return;
  }

  loading.value = true;
  error.value = '';
  try {
    const res = await getPlatformData(selectedPlatformId.value);
    if (reqId !== requestSerial) return;
    if (res.code !== 200) {
      throw new Error(res.msg || text.fetchFailed);
    }
    records.value = res.data || [];
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    error.value = err?.message || text.fetchFailed;
    ElMessage.error(error.value);
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
    await nextTick();
    renderTrendChart();
  }
}

function onPlatformChange() {
  resetFilters();
  void loadData();
}

function tableRowClassName() {
  return 'clickable-row';
}

function onTableRowClick(row: PlatformRecord) {
  const orderId = Number(row?.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) {
    ElMessage.warning('当前行缺少可用订单');
    return;
  }
  saveViewState();
  const query: Record<string, string> = {
    orderId: String(orderId),
  };
  if (selectedPlatformId.value) {
    query.platformId = String(selectedPlatformId.value);
  }
  void router.push({
    name: 'HomeAllOrderDetail',
    query,
  });
}

function clearFilterTimer() {
  if (filterTimer !== null) {
    clearTimeout(filterTimer);
    filterTimer = null;
  }
}

function applyFiltersNow() {
  clearFilterTimer();
  appliedFilters.value = { ...filters.value };
  page.value.current = 1;
}

function scheduleApplyFilters() {
  clearFilterTimer();
  filterTimer = window.setTimeout(() => {
    appliedFilters.value = { ...filters.value };
    page.value.current = 1;
  }, 220);
}

function resetFilters() {
  filters.value = createDefaultFilters();
  applyFiltersNow();
}

function handleResize() {
  trendChart?.resize();
}

watch(
  () => props.platformId,
  (val) => {
    const nextPlatformId = val ?? selectedPlatformId.value;
    const changed = nextPlatformId !== selectedPlatformId.value;
    selectedPlatformId.value = nextPlatformId;
    if (!platformMounted.value) return;
    if (!changed) return;
    resetFilters();
    void loadData();
  },
  { immediate: true }
);

watch(
  () => props.visible,
  async (val) => {
    if (val === false) return;
    await nextTick();
    renderTrendChart();
    requestAnimationFrame(() => trendChart?.resize());
  }
);

watch(
  filters,
  () => {
    scheduleApplyFilters();
  },
  { deep: true }
);

watch(
  () => [filteredRows.value.length, page.value.size],
  () => {
    const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / page.value.size));
    if (page.value.current > maxPage) {
      page.value.current = maxPage;
    }
  }
);

watch(
  filteredRows,
  async () => {
    await nextTick();
    renderTrendChart();
  }
);

onMounted(async () => {
  const hasRestored = restoreViewState();
  platformList.value = await loadPlatformList();
  if (!selectedPlatformId.value) {
    selectedPlatformId.value = platformList.value[0]?.id ?? null;
  }
  platformMounted.value = true;
  await loadData();
  if (hasRestored) {
    await nextTick();
    renderTrendChart();
  }
  resizeObserver = new ResizeObserver(() => {
    if (!trendRef.value) return;
    if (trendRef.value.clientWidth === 0 || trendRef.value.clientHeight === 0) return;
    if (!trendChart) {
      renderTrendChart();
    } else {
      trendChart.resize();
    }
  });
  if (trendRef.value) resizeObserver.observe(trendRef.value);
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  clearFilterTimer();
  resizeObserver?.disconnect();
  resizeObserver = null;
  window.removeEventListener('resize', handleResize);
  trendChart?.dispose();
  trendChart = null;
});

defineExpose({
  refresh: loadData,
});
</script>

<style scoped>
.platform-page {
  padding: 24px;
}

.panel {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(59, 130, 246, 0.18);
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
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
  position: relative;
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px 6px;
  margin: 4px 0 14px;
}

.chart-loading-mask {
  position: absolute;
  inset: 42px 12px 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #3f5f8a;
  z-index: 3;
  pointer-events: none;
  user-select: none;
}

.chart-title {
  font-size: 14px;
  color: #35577f;
  margin: 2px 0 8px;
}

.trend-chart {
  width: 100%;
  height: 360px;
}

.filter-bar {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
  margin: 0 0 14px;
}

.pager-wrap {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pager-info {
  color: #48668f;
  font-size: 13px;
}

:deep(.el-table .clickable-row) {
  cursor: pointer;
}

:deep(.el-table .clickable-row:hover > td.el-table__cell) {
  background: rgba(70, 145, 229, 0.1) !important;
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

  .filter-bar {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .platform-page {
    padding: 12px;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .filter-bar {
    grid-template-columns: 1fr;
  }

  .trend-chart {
    height: 300px;
  }

  .pager-wrap {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
