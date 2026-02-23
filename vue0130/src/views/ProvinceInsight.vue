<template>
  <section class="province-insight-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ provinceNameLabel }} 省消费洞察</h2>
          <p>
            平台维度：{{ platformLabel }}
            <span v-if="stats.totalOrders"> | 订单量：{{ stats.totalOrders }}</span>
          </p>
        </div>
        <div class="header-actions">
          <el-select
            v-model="selectedPlatformId"
            style="width: 190px"
            :placeholder="text.platformPlaceholder"
            @change="onPlatformChange"
          >
            <el-option :label="text.allPlatform" :value="null" />
            <el-option v-for="item in platformList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <el-button @click="goBack">{{ text.back }}</el-button>
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

      <template v-else-if="provinceRows.length">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ text.totalOrders }}</div>
            <div class="value">{{ stats.totalOrders }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.totalAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.totalAmount) }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.avgAmount }}</div>
            <div class="value">CNY {{ formatAmount(stats.avgAmount) }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.cityCount }}</div>
            <div class="value">{{ stats.cityCount }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.userCount }}</div>
            <div class="value">{{ stats.userCount }}</div>
          </div>
        </div>

        <div class="chart-card chart-card-lg">
          <div class="chart-title">{{ text.trendChartTitle }}</div>
          <div ref="trendRef" class="chart trend"></div>
        </div>

        <div class="chart-grid">
          <div class="chart-card">
            <div class="chart-title">{{ text.platformChartTitle }}</div>
            <div ref="platformRef" class="chart half"></div>
          </div>
          <div class="chart-card">
            <div class="chart-title">{{ text.cityChartTitle }}</div>
            <div ref="cityRef" class="chart half"></div>
          </div>
        </div>

        <div class="chart-grid">
          <div class="chart-card">
            <div class="chart-title">{{ text.hourChartTitle }}</div>
            <div ref="hourRef" class="chart half"></div>
          </div>
          <div class="chart-card">
            <div class="chart-title">{{ text.brandChartTitle }}</div>
            <div ref="brandRef" class="chart half"></div>
          </div>
        </div>

        <div class="table-card">
          <div class="chart-title">{{ text.latestOrdersTitle }}</div>
          <el-table
            :data="recentRows"
            border
            stripe
            height="500"
            :default-sort="{ prop: 'co_created_at', order: 'descending' }"
            :row-class-name="rowClassName"
            @row-click="onOrderRowClick"
          >
            <el-table-column prop="co_created_at" label="下单时间" min-width="170" sortable />
            <el-table-column prop="co_order_no" label="订单号" min-width="190" />
            <el-table-column prop="pf_name" label="平台" min-width="100" />
            <el-table-column prop="product_name" label="商品" min-width="150" />
            <el-table-column prop="p_brand" label="品牌/分类" min-width="140" />
            <el-table-column prop="c_name" label="城市" min-width="120" />
            <el-table-column label="金额" min-width="120" align="right" sortable>
              <template #default="{ row }">CNY {{ formatAmount(Number(row.amount || 0)) }}</template>
            </el-table-column>
            <el-table-column prop="user_name" label="用户" min-width="120" />
          </el-table>
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
import { useRoute, useRouter } from 'vue-router';
import * as echarts from 'echarts';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  back: '返回省份消费数据',
  loading: '正在加载省份洞察数据...',
  empty: '当前省份暂无订单数据',
  missingProvince: '缺少省份参数',
  fetchFailed: '获取省份洞察数据失败',
  platformPlaceholder: '平台筛选',
  allPlatform: '全部平台',
  totalOrders: '订单总量',
  totalAmount: '消费总额',
  avgAmount: '平均客单价',
  cityCount: '覆盖城市',
  userCount: '活跃用户',
  trendChartTitle: '消费趋势（按日期）',
  platformChartTitle: '平台贡献结构',
  cityChartTitle: '城市消费 Top10',
  hourChartTitle: '时段消费分布',
  brandChartTitle: '品牌/分类贡献 Top12',
  latestOrdersTitle: '最新订单明细（可点击进入订单详情）',
};

const route = useRoute();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const selectedPlatformId = ref<number | null>(null);
const provinceRows = ref<PlatformRecord[]>([]);
const loading = ref(false);
const error = ref('');

const trendRef = ref<HTMLDivElement | null>(null);
const platformRef = ref<HTMLDivElement | null>(null);
const cityRef = ref<HTMLDivElement | null>(null);
const hourRef = ref<HTMLDivElement | null>(null);
const brandRef = ref<HTMLDivElement | null>(null);

let trendChart: echarts.ECharts | null = null;
let platformChart: echarts.ECharts | null = null;
let cityChart: echarts.ECharts | null = null;
let hourChart: echarts.ECharts | null = null;
let brandChart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let requestSerial = 0;

function parseQueryString(raw: unknown) {
  if (Array.isArray(raw)) return String(raw[0] ?? '').trim();
  if (raw == null) return '';
  return String(raw).trim();
}

function parseQueryNumber(raw: unknown) {
  const value = Number(parseQueryString(raw));
  return Number.isFinite(value) && value > 0 ? value : null;
}

const provinceIdQuery = computed(() => parseQueryNumber(route.query.provinceId));
const provinceNameQuery = computed(() => parseQueryString(route.query.provinceName));

const provinceNameLabel = computed(() => {
  if (provinceNameQuery.value) return provinceNameQuery.value;
  return provinceRows.value[0]?.pr_name || '-';
});

const platformLabel = computed(() => {
  if (!selectedPlatformId.value) return text.allPlatform;
  return platformList.value.find((p) => p.id === selectedPlatformId.value)?.name || `ID ${selectedPlatformId.value}`;
});

const stats = computed(() => {
  const totalOrders = provinceRows.value.length;
  const totalAmount = provinceRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0);
  const avgAmount = totalOrders ? totalAmount / totalOrders : 0;
  const cityCount = new Set(provinceRows.value.map((row) => String(row.c_name || '').trim()).filter(Boolean)).size;
  const userCount = new Set(
    provinceRows.value.map((row) => `${row.user_id || ''}-${row.user_email || ''}`).filter(Boolean)
  ).size;
  return { totalOrders, totalAmount, avgAmount, cityCount, userCount };
});

const recentRows = computed(() =>
  [...provinceRows.value]
    .sort((a, b) => toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at))
    .slice(0, 120)
);

const trendDataset = computed(() => {
  const map = new Map<string, { amount: number; orders: number }>();
  for (const row of provinceRows.value) {
    const day = toDateKey(row.co_created_at);
    const old = map.get(day) || { amount: 0, orders: 0 };
    old.amount += Number(row.amount || 0);
    old.orders += 1;
    map.set(day, old);
  }
  const days = Array.from(map.keys()).sort((a, b) => a.localeCompare(b));
  return {
    days,
    amounts: days.map((d) => Number(map.get(d)?.amount || 0)),
    orders: days.map((d) => Number(map.get(d)?.orders || 0)),
  };
});

const platformDataset = computed(() => {
  const map = new Map<string, { amount: number; orders: number }>();
  for (const row of provinceRows.value) {
    const key = String(row.pf_name || '-');
    const old = map.get(key) || { amount: 0, orders: 0 };
    old.amount += Number(row.amount || 0);
    old.orders += 1;
    map.set(key, old);
  }
  return Array.from(map.entries())
    .map(([name, v]) => ({ name, amount: v.amount, orders: v.orders }))
    .sort((a, b) => b.amount - a.amount);
});

const cityDataset = computed(() => {
  const map = new Map<string, { amount: number; orders: number }>();
  for (const row of provinceRows.value) {
    const key = String(row.c_name || '-').trim() || '-';
    const old = map.get(key) || { amount: 0, orders: 0 };
    old.amount += Number(row.amount || 0);
    old.orders += 1;
    map.set(key, old);
  }
  return Array.from(map.entries())
    .map(([name, v]) => ({ name, amount: v.amount, orders: v.orders }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 10);
});

const hourDataset = computed(() => {
  const amount = new Array<number>(24).fill(0);
  const orders = new Array<number>(24).fill(0);
  for (const row of provinceRows.value) {
    const d = new Date(row.co_created_at);
    if (Number.isNaN(d.getTime())) continue;
    const hour = d.getHours();
    amount[hour] = (amount[hour] ?? 0) + Number(row.amount || 0);
    orders[hour] = (orders[hour] ?? 0) + 1;
  }
  return {
    labels: Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`),
    amount,
    orders,
  };
});

const brandDataset = computed(() => {
  const map = new Map<string, { amount: number; orders: number }>();
  for (const row of provinceRows.value) {
    const key = String(row.p_brand || '-').trim() || '-';
    const old = map.get(key) || { amount: 0, orders: 0 };
    old.amount += Number(row.amount || 0);
    old.orders += 1;
    map.set(key, old);
  }
  return Array.from(map.entries())
    .map(([name, v]) => ({ name, amount: v.amount, orders: v.orders }))
    .sort((a, b) => b.amount - a.amount)
    .slice(0, 12);
});

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

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function syncRouteState() {
  selectedPlatformId.value = parseQueryNumber(route.query.platformId);
}

function matchProvince(row: PlatformRecord) {
  const provinceId = provinceIdQuery.value;
  const provinceName = provinceNameQuery.value;
  if (provinceId && Number(row.pr_id) === provinceId) return true;
  if (provinceName && String(row.pr_name || '').trim() === provinceName) return true;
  return false;
}

async function fetchByPlatform(platformId: number): Promise<PlatformRecord[]> {
  const res = await getPlatformData(platformId);
  if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
  return res.data || [];
}

async function loadData() {
  const reqId = ++requestSerial;
  if (!provinceIdQuery.value && !provinceNameQuery.value) {
    provinceRows.value = [];
    error.value = text.missingProvince;
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    if (!platformList.value.length) {
      platformList.value = await loadPlatformList();
    }

    let sourceRows: PlatformRecord[] = [];
    if (selectedPlatformId.value) {
      sourceRows = await fetchByPlatform(selectedPlatformId.value);
    } else {
      const chunks = await Promise.all(platformList.value.map((item) => fetchByPlatform(item.id).catch(() => [])));
      sourceRows = chunks.flat();
    }

    if (reqId !== requestSerial) return;
    provinceRows.value = sourceRows.filter((row) => matchProvince(row));
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    provinceRows.value = [];
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
    await nextTick();
    renderAllCharts();
  }
}

function ensureCharts() {
  if (trendRef.value) {
    if (trendChart && trendChart.getDom() !== trendRef.value) {
      trendChart.dispose();
      trendChart = null;
    }
    if (!trendChart) trendChart = echarts.init(trendRef.value);
  }
  if (platformRef.value) {
    if (platformChart && platformChart.getDom() !== platformRef.value) {
      platformChart.dispose();
      platformChart = null;
    }
    if (!platformChart) platformChart = echarts.init(platformRef.value);
  }
  if (cityRef.value) {
    if (cityChart && cityChart.getDom() !== cityRef.value) {
      cityChart.dispose();
      cityChart = null;
    }
    if (!cityChart) cityChart = echarts.init(cityRef.value);
  }
  if (hourRef.value) {
    if (hourChart && hourChart.getDom() !== hourRef.value) {
      hourChart.dispose();
      hourChart = null;
    }
    if (!hourChart) hourChart = echarts.init(hourRef.value);
  }
  if (brandRef.value) {
    if (brandChart && brandChart.getDom() !== brandRef.value) {
      brandChart.dispose();
      brandChart = null;
    }
    if (!brandChart) brandChart = echarts.init(brandRef.value);
  }
}

function renderEmpty(chart: echarts.ECharts | null) {
  if (!chart) return;
  chart.setOption(
    {
      title: { text: text.empty, left: 'center', top: 'middle', textStyle: { color: '#8aa0bc' } },
      xAxis: { show: false },
      yAxis: { show: false },
      series: [],
    },
    true
  );
}

function renderTrendChart() {
  if (!trendChart) return;
  const { days, amounts, orders } = trendDataset.value;
  if (!days.length) {
    renderEmpty(trendChart);
    return;
  }
  const start = Math.max(0, days.length - 90);
  trendChart.setOption(
    {
      color: ['#2f7ed8', '#17b794'],
      tooltip: { trigger: 'axis' },
      legend: { top: 8, data: ['消费金额', '订单量'] },
      grid: { left: 56, right: 64, top: 44, bottom: 48 },
      xAxis: { type: 'category', data: days, axisLabel: { rotate: 28, hideOverlap: true } },
      yAxis: [{ type: 'value', name: 'CNY' }, { type: 'value', name: '订单量' }],
      series: [
        {
          name: '消费金额',
          type: 'line',
          smooth: true,
          showSymbol: false,
          areaStyle: {
            opacity: 0.2,
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(47, 126, 216, 0.42)' },
              { offset: 1, color: 'rgba(47, 126, 216, 0.05)' },
            ]),
          },
          data: amounts,
        },
        {
          name: '订单量',
          type: 'bar',
          yAxisIndex: 1,
          barMaxWidth: 18,
          itemStyle: { borderRadius: [4, 4, 0, 0], color: '#17b794' },
          data: orders,
        },
      ],
      dataZoom:
        days.length > 90
          ? [
              { type: 'inside', xAxisIndex: 0, startValue: start, endValue: days.length - 1, maxValueSpan: 240 },
              { type: 'slider', xAxisIndex: 0, startValue: start, endValue: days.length - 1, height: 14, bottom: 8 },
            ]
          : [],
    },
    true
  );
}

function renderPlatformChart() {
  if (!platformChart) return;
  const list = platformDataset.value;
  if (!list.length) {
    renderEmpty(platformChart);
    return;
  }
  platformChart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (p: any) => `${p.name}<br/>金额: CNY ${formatAmount(Number(p.value || 0))}<br/>订单: ${p.data.orders}`,
      },
      legend: { bottom: 4, type: 'scroll' },
      series: [
        {
          type: 'pie',
          radius: ['38%', '68%'],
          center: ['50%', '46%'],
          label: { formatter: '{b}\n{d}%' },
          data: list.map((item) => ({ name: item.name, value: Number(item.amount.toFixed(2)), orders: item.orders })),
        },
      ],
    },
    true
  );
}

function renderCityChart() {
  if (!cityChart) return;
  const list = [...cityDataset.value].reverse();
  if (!list.length) {
    renderEmpty(cityChart);
    return;
  }
  cityChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const item = list[p.dataIndex];
          if (!item) return '';
          return `${item.name}<br/>金额: CNY ${formatAmount(item.amount)}<br/>订单: ${item.orders}`;
        },
      },
      grid: { left: 78, right: 24, top: 18, bottom: 24 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: list.map((item) => item.name) },
      series: [
        {
          type: 'bar',
          barMaxWidth: 18,
          itemStyle: { borderRadius: [0, 8, 8, 0], color: '#3f8ef6' },
          data: list.map((item) => Number(item.amount.toFixed(2))),
        },
      ],
    },
    true
  );
}

function renderHourChart() {
  if (!hourChart) return;
  const ds = hourDataset.value;
  if (!ds.labels.length) {
    renderEmpty(hourChart);
    return;
  }
  hourChart.setOption(
    {
      color: ['#f39b39', '#2f7ed8'],
      tooltip: { trigger: 'axis' },
      legend: { top: 4, data: ['金额', '订单量'] },
      grid: { left: 56, right: 62, top: 36, bottom: 36 },
      xAxis: { type: 'category', data: ds.labels },
      yAxis: [{ type: 'value', name: '金额' }, { type: 'value', name: '订单' }],
      series: [
        { name: '金额', type: 'bar', barMaxWidth: 12, data: ds.amount.map((v) => Number(v.toFixed(2))) },
        { name: '订单量', type: 'line', yAxisIndex: 1, smooth: true, showSymbol: false, data: ds.orders },
      ],
    },
    true
  );
}

function renderBrandChart() {
  if (!brandChart) return;
  const list = brandDataset.value;
  if (!list.length) {
    renderEmpty(brandChart);
    return;
  }
  brandChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          const item = list[p.dataIndex];
          if (!item) return '';
          return `${item.name}<br/>金额: CNY ${formatAmount(item.amount)}<br/>订单: ${item.orders}`;
        },
      },
      grid: { left: 44, right: 18, top: 18, bottom: 70 },
      xAxis: {
        type: 'category',
        data: list.map((item) => item.name),
        axisLabel: { rotate: 32, interval: 0, overflow: 'truncate' },
      },
      yAxis: { type: 'value' },
      series: [
        {
          type: 'bar',
          barMaxWidth: 24,
          itemStyle: { borderRadius: [6, 6, 0, 0], color: '#5f84f8' },
          data: list.map((item) => Number(item.amount.toFixed(2))),
        },
      ],
    },
    true
  );
}

function renderAllCharts() {
  ensureCharts();
  renderTrendChart();
  renderPlatformChart();
  renderCityChart();
  renderHourChart();
  renderBrandChart();
  setupResizeObserver();
  requestAnimationFrame(() => handleResize());
}

function handleResize() {
  trendChart?.resize();
  platformChart?.resize();
  cityChart?.resize();
  hourChart?.resize();
  brandChart?.resize();
}

function setupResizeObserver() {
  if (!resizeObserver) {
    resizeObserver = new ResizeObserver(() => handleResize());
  }
  resizeObserver.disconnect();
  if (trendRef.value) resizeObserver.observe(trendRef.value);
  if (platformRef.value) resizeObserver.observe(platformRef.value);
  if (cityRef.value) resizeObserver.observe(cityRef.value);
  if (hourRef.value) resizeObserver.observe(hourRef.value);
  if (brandRef.value) resizeObserver.observe(brandRef.value);
}

function onPlatformChange(value: number | null) {
  const query: Record<string, string> = {};
  if (provinceIdQuery.value) query.provinceId = String(provinceIdQuery.value);
  if (provinceNameQuery.value) query.provinceName = provinceNameQuery.value;
  if (value) query.platformId = String(value);
  void router.replace({ name: 'HomeProvinceInsight', query });
}

function resolvePlatformIdByName(name: string) {
  return platformList.value.find((item) => item.name === name)?.id ?? null;
}

function rowClassName() {
  return 'clickable-row';
}

function onOrderRowClick(row: PlatformRecord) {
  const orderId = Number(row?.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) return;

  const query: Record<string, string> = {
    orderId: String(orderId),
    backTo: 'provinceInsight',
  };
  const pid = selectedPlatformId.value || resolvePlatformIdByName(String(row?.pf_name || ''));
  if (pid) query.platformId = String(pid);
  if (provinceIdQuery.value) query.provinceId = String(provinceIdQuery.value);
  if (provinceNameLabel.value && provinceNameLabel.value !== '-') query.provinceName = provinceNameLabel.value;

  void router.push({ name: 'HomeAllOrderDetail', query });
}

function goBack() {
  const query: Record<string, string> = {};
  if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
  if (provinceIdQuery.value) query.provinceId = String(provinceIdQuery.value);
  if (provinceNameQuery.value) query.provinceName = provinceNameQuery.value;
  void router.push({ name: 'HomeProvince', query });
}

watch(
  () => route.fullPath,
  () => {
    syncRouteState();
    void loadData();
  },
  { immediate: true }
);

onMounted(async () => {
  platformList.value = await loadPlatformList();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  trendChart?.dispose();
  platformChart?.dispose();
  cityChart?.dispose();
  hourChart?.dispose();
  brandChart?.dispose();
  trendChart = null;
  platformChart = null;
  cityChart = null;
  hourChart = null;
  brandChart = null;
});
</script>

<style scoped>
.province-insight-page {
  padding: 24px;
  min-height: 100dvh;
  background:
    radial-gradient(90rem 58rem at -10% -20%, rgba(65, 136, 232, 0.18), rgba(65, 136, 232, 0)),
    radial-gradient(72rem 52rem at 112% 120%, rgba(25, 180, 157, 0.14), rgba(25, 180, 157, 0)),
    linear-gradient(148deg, #edf5ff 0%, #dce9fb 100%);
}

.panel {
  background: rgba(255, 255, 255, 0.88);
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
  color: #1e3f67;
}

.panel-header p {
  margin: 8px 0 0;
  color: #5a7698;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
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
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin: 10px 0 16px;
}

.stat-card {
  border-radius: 12px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px;
}

.stat-card .label {
  color: #5b6f8d;
  font-size: 13px;
}

.stat-card .value {
  margin-top: 4px;
  color: #1f3f67;
  font-size: 22px;
  font-weight: 700;
}

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.chart-card {
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px 8px;
}

.chart-card-lg {
  margin-top: 4px;
}

.chart-title {
  font-size: 14px;
  color: #35577f;
  margin: 2px 0 8px;
  font-weight: 600;
}

.chart {
  width: 100%;
}

.chart.trend {
  height: 360px;
}

.chart.half {
  height: 300px;
}

.table-card {
  margin-top: 12px;
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 10px 12px;
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

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .province-insight-page {
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

  .chart-grid {
    grid-template-columns: 1fr;
  }

  .chart.trend,
  .chart.half {
    height: 280px;
  }
}
</style>
