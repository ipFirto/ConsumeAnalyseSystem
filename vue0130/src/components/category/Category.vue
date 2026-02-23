<template>
  <section class="category-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>{{ text.subTitle }}</p>
        </div>
        <div class="controls">
          <el-select v-model="selectedPlatformId" style="width: 170px" @change="onConditionChange">
            <el-option :label="text.allPlatform" :value="null" />
            <el-option v-for="p in platformList" :key="p.id" :label="p.name" :value="p.id" />
          </el-select>

          <el-select v-model="selectedCategoryName" style="width: 190px" @change="onConditionChange">
            <el-option :label="text.allCategory" value="" />
            <el-option v-for="c in categoryOptions" :key="c" :label="c" :value="c" />
          </el-select>
        </div>
      </header>

      <div class="stat-row">
        <div class="stat-card">
          <div class="label">{{ text.statOrders }}</div>
          <div class="value">{{ stats.totalOrders }}</div>
        </div>
        <div class="stat-card">
          <div class="label">{{ text.statAmount }}</div>
          <div class="value">CNY {{ formatAmount(stats.totalAmount) }}</div>
        </div>
        <div class="stat-card">
          <div class="label">{{ text.statProducts }}</div>
          <div class="value">{{ stats.productCount }}</div>
        </div>
      </div>

      <div v-if="loading" class="loading-mask">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loading }}</span>
      </div>

      <div v-if="error" class="error-tip">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <div class="chart-card">
        <div class="chart-title">{{ text.trendTitle }}</div>
        <div ref="lineRef" class="chart top"></div>
      </div>

      <div class="chart-card">
        <div class="chart-title">{{ text.productBarTitle }}</div>
        <div ref="barRef" class="chart bottom"></div>
      </div>

      <div v-if="isStandaloneRoute" class="detail-card">
        <div class="detail-head">
          <div class="chart-title">{{ text.detailTitle }}</div>
          <div class="detail-meta">
            <span>{{ text.currentCategory }}{{ selectedCategoryName || text.allCategory }}</span>
            <span>{{ text.currentTime }}{{ clickedTimeKey || '--' }}</span>
          </div>
        </div>

        <el-alert
          v-if="!clickedTimeKey"
          type="info"
          :closable="false"
          :title="text.clickHint"
          show-icon
          class="detail-alert"
        />

        <el-table
          v-else
          :data="timeWindowOrders"
          border
          stripe
          height="520"
          :default-sort="{ prop: 'co_created_at', order: 'ascending' }"
          :row-class-name="detailRowClassName"
          @row-click="onOrderRowClick"
        >
          <el-table-column prop="co_created_at" :label="text.colTime" min-width="170" sortable />
          <el-table-column prop="co_order_no" :label="text.colOrderNo" min-width="190" />
          <el-table-column prop="product_name" :label="text.colProduct" min-width="160" />
          <el-table-column prop="p_brand" :label="text.colBrand" min-width="120" />
          <el-table-column :label="text.colAmount" min-width="130" align="right" sortable>
            <template #default="{ row }">CNY {{ formatAmount(Number(row.amount || 0)) }}</template>
          </el-table-column>
          <el-table-column prop="user_name" :label="text.colUser" min-width="110" />
          <el-table-column prop="user_email" :label="text.colEmail" min-width="170" />
          <el-table-column prop="pr_name" :label="text.colProvince" min-width="110" />
          <el-table-column prop="c_name" :label="text.colCity" min-width="110" />
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { ElMessage } from 'element-plus';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getCategoryPie } from '@/api/modules/chart';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { getCategoryData, type CategoryOrderRecord } from '@/api/category/category';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  title: '分类消费数据',
  subTitle: '首页点击饼图可定向类别，默认全平台全类别',
  allPlatform: '全平台',
  allCategory: '全类别',
  statOrders: '订单总数',
  statAmount: '销量总额',
  statProducts: '产品数',
  loading: '正在加载分类数据...',
  refreshFailed: '刷新失败，已保留上次图表数据',
  trendTitle: '销量趋势（金额+订单数）',
  productBarTitle: '产品销量排行（当前类别）',
  detailTitle: '订单明细（点击折线图某个时间点，显示1小时内）',
  currentCategory: '当前类别：',
  currentTime: '时间键：',
  clickHint: '请先点击上方趋势图的某个时间点',
  colTime: '下单时间',
  colOrderNo: '订单号',
  colProduct: '商品',
  colBrand: '分类/品牌',
  colAmount: '金额',
  colUser: '用户',
  colEmail: '邮箱',
  colProvince: '省份',
  colCity: '城市',
  xTime: '时间',
  yAmount: '销量金额',
  ySales: '销量',
  yOrders: '订单数',
  empty: '暂无数据',
  fetchFailed: '获取分类数据失败',
};

const props = defineProps<{
  categoryId: number | null;
  categoryName?: string | null;
  platformId?: number | null;
  visible?: boolean;
}>();
const route = useRoute();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const categoryOptions = ref<string[]>([]);
const selectedPlatformId = ref<number | null>(props.platformId ?? null);
const selectedCategoryName = ref<string>(props.categoryName ?? '');
const clickedTimeKey = ref<string>('');
const records = ref<Array<PlatformRecord | CategoryOrderRecord>>([]);
const loading = ref(false);
const error = ref('');

const lineRef = ref<HTMLDivElement | null>(null);
const barRef = ref<HTMLDivElement | null>(null);
let lineChart: echarts.ECharts | null = null;
let barChart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let requestSerial = 0;
let inited = false;
const CATEGORY_TREND_DEFAULT_VISIBLE = 200;
const CATEGORY_TREND_MAX_VISIBLE = 500;

const isStandaloneRoute = computed(
  () => route.path === '/categoryInfo' || route.path === '/home/category-info'
);

const stats = computed(() => {
  const totalOrders = records.value.length;
  const totalAmount = records.value.reduce((sum, item: any) => sum + Number(item.amount || 0), 0);
  const productCount = new Set(records.value.map((x: any) => x.product_name).filter(Boolean)).size;
  return { totalOrders, totalAmount, productCount };
});

function formatAmount(v: number) {
  return Number(v || 0).toFixed(2);
}

function parseQueryString(raw: unknown) {
  if (Array.isArray(raw)) return String(raw[0] ?? '').trim();
  if (raw == null) return '';
  return String(raw).trim();
}

function parseQueryNumber(raw: unknown) {
  const value = Number(parseQueryString(raw));
  return Number.isFinite(value) && value > 0 ? value : null;
}

function parseBucketStart(bucket: string) {
  if (!bucket) return null;
  const normalized = bucket.includes('T') ? bucket : bucket.replace(' ', 'T');
  const full = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(normalized) ? `${normalized}:00` : normalized;
  const d = new Date(full);
  if (Number.isNaN(d.getTime())) return null;
  return d;
}

function toTimestamp(raw: string) {
  const value = new Date(raw).getTime();
  return Number.isFinite(value) ? value : 0;
}

function mapPlatformIdByName(name: string) {
  return platformList.value.find((item) => item.name === name)?.id ?? null;
}

const timeWindowOrders = computed(() => {
  const start = parseBucketStart(clickedTimeKey.value);
  if (!start) return [];
  const end = start.getTime() + 60 * 60 * 1000;

  return records.value
    .filter((row: any) => {
      const ts = toTimestamp(String(row?.co_created_at || ''));
      return ts >= start.getTime() && ts < end;
    })
    .sort((a: any, b: any) => toTimestamp(a.co_created_at) - toTimestamp(b.co_created_at));
});

function toTimeBucket(v: string) {
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return String(v || '-');
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  const h = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${y}-${m}-${day} ${h}:${mm}`;
}

function toSalesValue(row: any) {
  const candidates = ['sales', 'sale_num', 'saleCount', 'count', 'quantity', 'num', 'product_num'];
  for (const key of candidates) {
    const value = Number(row?.[key]);
    if (Number.isFinite(value) && value > 0) return value;
  }
  return 1;
}

function ensureCharts() {
  if (!lineChart && lineRef.value) lineChart = echarts.init(lineRef.value);
  if (!barChart && barRef.value) barChart = echarts.init(barRef.value);
}

function buildCategoryRouteQuery(timeKey: string) {
  const query: Record<string, string> = { time: timeKey };
  if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
  if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;
  return query;
}

function buildProductRouteQuery(productName: string) {
  const query: Record<string, string> = { productName };
  if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
  if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;
  return query;
}

function bindLineChartClick() {
  if (!lineChart) return;
  lineChart.off('click');
  lineChart.on('click', (params: any) => {
    const timeKey = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    if (!timeKey) return;
    const dataIndex = Number(params?.dataIndex);
    if (Number.isFinite(dataIndex) && dataIndex >= 0) {
      lineChart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex });
      lineChart?.dispatchAction({ type: 'updateAxisPointer', xAxisIndex: 0, value: timeKey });
    }
    clickedTimeKey.value = timeKey;
    void router.push({
      name: 'HomeCategoryInfo',
      query: buildCategoryRouteQuery(timeKey),
    });
  });
}

function bindBarChartClick() {
  if (!barChart) return;
  barChart.off('click');
  barChart.on('click', (params: any) => {
    const productName = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    if (!productName || productName === '-') return;
    const dataIndex = Number(params?.dataIndex);
    if (Number.isFinite(dataIndex) && dataIndex >= 0) {
      barChart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex });
      barChart?.dispatchAction({ type: 'updateAxisPointer', xAxisIndex: 0, value: productName });
    }
    void router.push({
      name: 'HomeProductSales',
      query: buildProductRouteQuery(productName),
    });
  });
}

function renderCharts() {
  if (!lineChart || !barChart) return;

  if (!records.value.length) {
    lineChart.setOption({ title: { text: text.empty, left: 'center', top: 'middle' }, series: [] }, true);
    barChart.setOption({ title: { text: text.empty, left: 'center', top: 'middle' }, series: [] }, true);
    return;
  }

  const timelineMap = new Map<string, { amount: number; orders: number }>();
  const productSalesMap = new Map<string, number>();

  for (const row of records.value as any[]) {
    const key = toTimeBucket(row.co_created_at);
    const amount = Number(row.amount || 0);
    const old = timelineMap.get(key) || { amount: 0, orders: 0 };
    old.amount += amount;
    old.orders += 1;
    timelineMap.set(key, old);

    const product = String(row.product_name || '-');
    const sales = toSalesValue(row);
    productSalesMap.set(product, (productSalesMap.get(product) || 0) + sales);
  }

  const timeKeys = Array.from(timelineMap.keys()).sort();
  const amountSeries = timeKeys.map((k) => Number(timelineMap.get(k)?.amount || 0));
  const orderSeries = timeKeys.map((k) => Number(timelineMap.get(k)?.orders || 0));
  const trendStartIndex = Math.max(0, timeKeys.length - CATEGORY_TREND_DEFAULT_VISIBLE);
  const showTrendZoom = timeKeys.length > CATEGORY_TREND_DEFAULT_VISIBLE;

  const topProducts = Array.from(productSalesMap.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 20)
    .reverse();
  const productNames = topProducts.map((x) => x[0]);
  const productSales = topProducts.map((x) => Number(x[1].toFixed(2)));

  lineChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'line', snap: true } },
      legend: { data: [text.yAmount, text.yOrders], top: 6 },
      grid: { left: 60, right: 70, top: 42, bottom: 45 },
      xAxis: { type: 'category', data: timeKeys, name: text.xTime, axisLabel: { hideOverlap: true } },
      yAxis: [{ type: 'value', name: text.yAmount }, { type: 'value', name: text.yOrders }],
      series: [
        {
          name: text.yAmount,
          type: 'line',
          smooth: true,
          cursor: 'pointer',
          data: amountSeries,
          areaStyle: { opacity: 0.12 },
        },
        { name: text.yOrders, type: 'line', smooth: true, cursor: 'pointer', yAxisIndex: 1, data: orderSeries },
      ],
      dataZoom: showTrendZoom
        ? [
            {
              type: 'inside',
              xAxisIndex: 0,
              startValue: trendStartIndex,
              endValue: timeKeys.length - 1,
              maxValueSpan: CATEGORY_TREND_MAX_VISIBLE,
            },
            {
              type: 'slider',
              xAxisIndex: 0,
              startValue: trendStartIndex,
              endValue: timeKeys.length - 1,
              maxValueSpan: CATEGORY_TREND_MAX_VISIBLE,
              height: 14,
              bottom: 10,
            },
          ]
        : [],
    },
    true
  );

  barChart.setOption(
    {
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: 70, right: 20, top: 18, bottom: 80 },
      xAxis: { type: 'category', data: productNames, axisLabel: { rotate: 28, interval: 0, overflow: 'truncate' } },
      yAxis: { type: 'value', name: text.ySales },
      series: [{ type: 'bar', cursor: 'pointer', data: productSales, barMaxWidth: 44 }],
    },
    true
  );

  bindLineChartClick();
  bindBarChartClick();

  requestAnimationFrame(() => {
    lineChart?.resize();
    barChart?.resize();
  });
}

async function fetchCategoryOptions() {
  try {
    if (selectedPlatformId.value) {
      const res = (await getCategoryPie(selectedPlatformId.value)).data;
      if (res.code !== 200) return;
      categoryOptions.value = Array.from(new Set((res.data || []).map((x: any) => String(x.category || ''))))
        .filter(Boolean)
        .sort();
      return;
    }

    const tasks = platformList.value.map((p) => getCategoryPie(p.id));
    const all = await Promise.all(tasks);
    const names = new Set<string>();
    for (const item of all) {
      const body: any = item.data;
      if (body?.code !== 200) continue;
      for (const row of body?.data || []) {
        const n = String(row.category || '');
        if (n) names.add(n);
      }
    }
    categoryOptions.value = Array.from(names).sort();
  } catch {
    categoryOptions.value = [];
  }
}

async function fetchByPlatform(platformId: number): Promise<Array<PlatformRecord | CategoryOrderRecord>> {
  if (selectedCategoryName.value) {
    const res = await getCategoryData(platformId, selectedCategoryName.value);
    if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
    return res.data || [];
  }
  const res = await getPlatformData(platformId);
  if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
  return res.data || [];
}

async function loadData() {
  const reqId = ++requestSerial;
  loading.value = true;
  error.value = '';
  try {
    let result: Array<PlatformRecord | CategoryOrderRecord> = [];

    if (selectedPlatformId.value) {
      result = await fetchByPlatform(selectedPlatformId.value);
    } else {
      const chunks = await Promise.all(platformList.value.map((p) => fetchByPlatform(p.id).catch(() => [])));
      result = chunks.flat();
    }

    if (reqId !== requestSerial) return;
    records.value = result;
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    error.value = err?.message || text.refreshFailed;
    ElMessage.error(error.value);
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
    await nextTick();
    ensureCharts();
    renderCharts();
  }
}

function onConditionChange() {
  if (isStandaloneRoute.value) {
    const query: Record<string, string> = {};
    if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
    if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;
    if (clickedTimeKey.value) query.time = clickedTimeKey.value;
    void router.replace({ name: 'HomeCategoryInfo', query });
  }
  void fetchCategoryOptions();
  void loadData();
}

function applyRouteQueryState() {
  if (!isStandaloneRoute.value) return;
  selectedPlatformId.value = parseQueryNumber(route.query.platformId);
  selectedCategoryName.value = parseQueryString(route.query.categoryName);
  clickedTimeKey.value = parseQueryString(route.query.time);
}

function detailRowClassName() {
  return 'clickable-row';
}

function onOrderRowClick(row: any) {
  const orderId = Number(row?.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) return;

  const query: Record<string, string> = {
    orderId: String(orderId),
    backTo: 'category',
  };

  const pid = selectedPlatformId.value || mapPlatformIdByName(String(row?.pf_name || ''));
  if (pid) query.platformId = String(pid);
  if (clickedTimeKey.value) query.time = clickedTimeKey.value;
  if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;

  void router.push({
    name: 'HomeAllOrderDetail',
    query,
  });
}

function handleResize() {
  lineChart?.resize();
  barChart?.resize();
}

watch(
  () => props.platformId,
  (val) => {
    if (isStandaloneRoute.value) return;
    if (val === undefined) return;
    selectedPlatformId.value = val ?? null;
    void fetchCategoryOptions();
    void loadData();
  }
);

watch(
  () => props.categoryName,
  (val) => {
    if (isStandaloneRoute.value) return;
    if (val === undefined || val === null) return;
    selectedCategoryName.value = String(val);
    void loadData();
  }
);

watch(
  () => props.visible,
  async (val) => {
    if (isStandaloneRoute.value) return;
    if (!inited) return;
    if (val === false) return;
    await nextTick();
    ensureCharts();
    handleResize();
  }
);

watch(
  () => route.fullPath,
  () => {
    if (!isStandaloneRoute.value) return;
    applyRouteQueryState();
    void fetchCategoryOptions();
    void loadData();
  },
  { immediate: true }
);

onMounted(async () => {
  platformList.value = await loadPlatformList();
  if (isStandaloneRoute.value) {
    applyRouteQueryState();
  }
  if (selectedPlatformId.value && !platformList.value.some((x) => x.id === selectedPlatformId.value)) {
    selectedPlatformId.value = null;
  }

  await fetchCategoryOptions();
  await nextTick();
  ensureCharts();
  await loadData();
  inited = true;
  resizeObserver = new ResizeObserver(() => {
    handleResize();
  });
  if (lineRef.value) resizeObserver.observe(lineRef.value);
  if (barRef.value) resizeObserver.observe(barRef.value);
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  lineChart?.dispose();
  barChart?.dispose();
  lineChart = null;
  barChart = null;
});

defineExpose({
  refresh: loadData,
});
</script>

<style scoped>
.category-page {
  padding: 24px;
}

.panel {
  position: relative;
  background: rgba(255, 255, 255, 0.9);
  border: 1px solid rgba(59, 130, 246, 0.16);
  border-radius: 18px;
  padding: 16px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-header h2 {
  margin: 0;
  color: #1d3f67;
}

.panel-header p {
  margin: 8px 0 0;
  color: #567299;
  font-size: 13px;
}

.controls {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px;
}

.label {
  color: #5b6f8d;
  font-size: 13px;
}

.value {
  margin-top: 4px;
  color: #1f3f67;
  font-size: 22px;
  font-weight: 700;
}

.chart-card {
  margin-top: 14px;
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 8px 10px 12px;
}

.chart-title {
  font-size: 14px;
  color: #35577f;
  margin: 4px 0 8px;
}

.chart {
  width: 100%;
}

.chart.top {
  height: 320px;
}

.chart.bottom {
  height: 420px;
}

.detail-card {
  margin-top: 14px;
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.detail-meta {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #5b7598;
  font-size: 13px;
}

.detail-alert {
  margin-bottom: 10px;
}

.loading-mask {
  position: absolute;
  right: 16px;
  top: 16px;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(59, 130, 246, 0.2);
  border-radius: 10px;
  padding: 8px 10px;
  color: #385f8e;
}

.error-tip {
  margin-top: 10px;
  color: #d9534f;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.loading-icon {
  animation: spin 1s linear infinite;
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

@media (max-width: 960px) {
  .panel-header {
    flex-direction: column;
  }

  .controls {
    justify-content: flex-start;
  }

  .stat-row {
    grid-template-columns: 1fr;
  }

  .chart.top {
    height: 280px;
  }

  .chart.bottom {
    height: 360px;
  }

  .detail-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .loading-mask {
    position: static;
    margin-top: 10px;
    width: fit-content;
  }
}
</style>
