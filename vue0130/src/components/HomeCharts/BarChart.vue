<template>
  <div class="bar-content">
    <div ref="chartRef" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts';
import { getBarByPlatform } from '@/api/modules/chart';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const emit = defineEmits<{
  (e: 'platform-click', platformId: number): void;
}>();

const TEXT = {
  orderCount: '订单数',
  orderCountWithColon: '订单数：',
  bizError: '业务错误',
  autoRefreshFailed: '自动刷新失败',
};

const platforms = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const COLORS: Array<[string, string]> = [
  ['#2563eb', '#60a5fa'],
  ['#06b6d4', '#22d3ee'],
  ['#22c55e', '#4ade80'],
  ['#f59e0b', '#fbbf24'],
  ['#ec4899', '#f472b6'],
];

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

function buildBarData(values: number[]) {
  return values.map((v, i) => {
    const [start, end] = COLORS[i % COLORS.length] || ['#2f7ed8', '#5ea7f2'];
    return {
      value: v,
      itemStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: start },
          { offset: 1, color: end },
        ]),
        borderRadius: [10, 10, 0, 0],
      },
    };
  });
}

function bindClick() {
  if (!chart) return;

  chart.off('click');
  chart.on('click', (params: any) => {
    const platformName = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    const dataIndex = Number(params?.dataIndex);
    if (Number.isFinite(dataIndex) && dataIndex >= 0) {
      chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex });
      chart?.dispatchAction({ type: 'updateAxisPointer', xAxisIndex: 0, value: platformName });
    }
    const target = platforms.value.find((p) => p.name === platformName);
    if (!target) return;
    emit('platform-click', target.id);
  });
}

function initChart() {
  if (!chartRef.value) return;

  chart = echarts.init(chartRef.value);
  const option: echarts.EChartsOption = {
    grid: { left: 40, right: 20, top: 36, bottom: 54, containLabel: true },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(12, 12, 12, 0.94)',
      borderColor: 'rgba(255, 255, 255, 0.42)',
      textStyle: { color: '#f3f3f3' },
      formatter: (ps: any) => {
        const p = Array.isArray(ps) ? ps[0] : ps;
        return `${p.name}<br/>${TEXT.orderCountWithColon}${p.value}`;
      },
    },
    xAxis: {
      type: 'category',
      data: platforms.value.map((p) => p.name),
      axisTick: { alignWithLabel: true },
      axisLabel: { interval: 0, rotate: 0, margin: 12, color: '#414141' },
      axisLine: { lineStyle: { color: 'rgba(80, 80, 80, 0.52)' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#414141' },
      splitLine: { lineStyle: { color: 'rgba(94, 94, 94, 0.2)' } },
    },
    series: [
      {
        name: TEXT.orderCount,
        type: 'bar',
        cursor: 'pointer',
        data: buildBarData([0, 0, 0, 0, 0]),
        label: { show: true, position: 'top', color: '#2f2f2f' },
        showBackground: true,
        backgroundStyle: { color: 'rgba(114, 114, 114, 0.08)', borderRadius: [10, 10, 0, 0] },
      },
    ],
  };

  chart.setOption(option, { notMerge: true });
  bindClick();
}

async function fetchCounts(): Promise<number[]> {
  const res = await getBarByPlatform();
  if (res.code !== 200) throw new Error(res.msg || TEXT.bizError);
  return platforms.value.map((p, i) => res.data?.[p.id - 1] ?? res.data?.[i] ?? 0);
}

function updateChart(values: number[]) {
  if (!chart) return;
  chart.setOption({ series: [{ data: buildBarData(values) }] }, { notMerge: false, lazyUpdate: true });
}

function applyCounts(values: number[]) {
  updateChart(values || []);
}

async function tick() {
  try {
    const values = await fetchCounts();
    updateChart(values);
  } catch (e) {
    console.error(`${TEXT.autoRefreshFailed}:`, e);
  }
}

async function refresh() {
  await tick();
}

function handleResize() {
  chart?.resize();
}

function resize() {
  chart?.resize();
}

onMounted(async () => {
  await nextTick();
  platforms.value = await loadPlatformList();
  initChart();
  if (chartRef.value) {
    resizeObserver = new ResizeObserver(() => {
      chart?.resize();
    });
    resizeObserver.observe(chartRef.value);
  }
  chart?.setOption({ xAxis: { data: platforms.value.map((p) => p.name) } });
  await tick();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.off('click');
  chart?.dispose();
  chart = null;
});

defineExpose({
  refresh,
  resize,
  applyCounts,
});
</script>

<style scoped>
.bar-content {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  justify-content: center;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>

