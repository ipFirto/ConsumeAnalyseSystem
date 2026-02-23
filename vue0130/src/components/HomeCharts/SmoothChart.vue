<template>
  <div class="smooth-content">
    <div ref="chartRef" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { getSmoothByOriginal, type ProvinceOrderCountRow } from '@/api/modules/chart';

const emit = defineEmits<{
  (e: 'province-click', provinceId: number, provinceName: string): void;
}>();

const BAR_COLORS = ['#3b82f6', '#06b6d4', '#10b981', '#84cc16', '#f59e0b', '#f97316', '#ef4444', '#a855f7'];

const provinceIdByName = new Map<string, number>();

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;
let updating = false;
let hasAnimatedFromZero = false;

const NORMAL_UPDATE_DURATION = 420;
const INITIAL_GROW_DURATION = 1400;

async function fetchSmoothData(): Promise<ProvinceOrderCountRow[]> {
  const res: any = await getSmoothByOriginal();
  const rows = res?.data?.data ?? res?.data ?? res;
  return (rows ?? []) as ProvinceOrderCountRow[];
}

function colorForName(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  return BAR_COLORS[hash % BAR_COLORS.length] || '#3b82f6';
}

function bindClick() {
  if (!chart) return;

  chart.off('click');
  chart.on('click', (params: any) => {
    const provinceName = String(params?.axisValueLabel ?? params?.axisValue ?? params?.name ?? '').trim();
    const dataIndex = Number(params?.dataIndex);
    if (Number.isFinite(dataIndex) && dataIndex >= 0) {
      chart?.dispatchAction({ type: 'showTip', seriesIndex: 0, dataIndex });
      chart?.dispatchAction({ type: 'updateAxisPointer', yAxisIndex: 0, value: provinceName });
    }
    const provinceId = provinceIdByName.get(provinceName);
    if (provinceId == null) return;
    emit('province-click', provinceId, provinceName);
  });
}

function initOption() {
  if (!chart) return;

  chart.setOption(
    {
      title: {
        text: '省消费数据分析',
        textStyle: { color: '#171717', fontSize: 18, fontWeight: 700 },
      },
      grid: { left: 88, right: 82, top: 52, bottom: 28, containLabel: true },
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: 'rgba(10, 10, 10, 0.95)',
        borderColor: 'rgba(255, 255, 255, 0.4)',
        textStyle: { color: '#f4f4f4' },
        formatter: (params: any) => {
          const p = Array.isArray(params) ? params[0] : params;
          return `${p.name}<br/>订单数：${p.value}`;
        },
      },
      xAxis: {
        type: 'value',
        max: (v: any) => (v.max ? Math.ceil(v.max * 1.1) : 0),
        axisLabel: { color: '#454545' },
        splitLine: { lineStyle: { color: 'rgba(88, 88, 88, 0.22)' } },
      },
      yAxis: {
        type: 'category',
        data: [],
        inverse: true,
        axisLabel: { color: '#454545' },
        axisLine: { lineStyle: { color: 'rgba(90, 90, 90, 0.52)' } },
      },
      series: [
        {
          name: '订单数',
          type: 'bar',
          cursor: 'pointer',
          realtimeSort: true,
          data: [],
          barMaxWidth: 18,
          label: { show: true, position: 'right', distance: 8, valueAnimation: true, color: '#2f2f2f' },
          itemStyle: {
            color: (p: any) => colorForName(p.name),
            borderRadius: [0, 8, 8, 0],
          } as any,
        },
      ],
      animationDuration: 280,
      animationDurationUpdate: NORMAL_UPDATE_DURATION,
      animationEasing: 'cubicOut',
      animationEasingUpdate: 'cubicOut',
    },
    { notMerge: true }
  );
}

async function updateChartWithRows(rows: ProvinceOrderCountRow[]) {
  if (!chart || updating) return;
  updating = true;

  try {
    const safeRows = Array.isArray(rows) ? rows : [];

    provinceIdByName.clear();
    safeRows.forEach((r) => provinceIdByName.set(r.province_name, r.province_id));

    const names = safeRows.map((r) => r.province_name);
    const values = safeRows.map((r) => Number(r.order_total || 0));

    if (!hasAnimatedFromZero) {
      chart.setOption({
        animationDurationUpdate: 0,
        yAxis: { data: names },
        series: [{ data: values.map(() => 0) }],
      });

      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

      chart.setOption({
        animationDurationUpdate: INITIAL_GROW_DURATION,
        animationEasingUpdate: 'cubicOut',
        yAxis: { data: names },
        series: [{ data: values }],
      });
      hasAnimatedFromZero = true;
      return;
    }

    chart.setOption({
      animationDurationUpdate: NORMAL_UPDATE_DURATION,
      yAxis: { data: names },
      series: [{ data: values }],
    });
  } finally {
    updating = false;
  }
}

async function updateChart() {
  const rows = await fetchSmoothData();
  await updateChartWithRows(rows);
}

function applyRows(rows: ProvinceOrderCountRow[]) {
  void updateChartWithRows(rows || []);
}

async function refresh() {
  await updateChart();
}

function handleResize() {
  chart?.resize();
}

function resize() {
  chart?.resize();
}

onMounted(async () => {
  await nextTick();
  if (!chartRef.value) return;

  chart = echarts.init(chartRef.value);
  resizeObserver = new ResizeObserver(() => {
    chart?.resize();
  });
  resizeObserver.observe(chartRef.value);
  initOption();
  bindClick();
  await updateChart();

  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  chart?.off('click');
  window.removeEventListener('resize', handleResize);
  resizeObserver?.disconnect();
  resizeObserver = null;
  chart?.dispose();
  chart = null;
});

defineExpose({
  refresh,
  resize,
  applyRows,
});
</script>

<style scoped>
.smooth-content {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.chart {
  width: 100%;
  height: 100%;
}
</style>

