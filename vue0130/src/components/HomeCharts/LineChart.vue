<template>
  <div class="line-content">
    <div class="pie-wrap">
      <div class="toolbar">
        <el-select v-model="platformId" :placeholder="text.selectPlatform" style="width: 180px" @change="onPlatformChange">
          <el-option v-for="p in platforms" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>

      <div ref="chartRef" class="chart"></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import * as echarts from 'echarts';
import { getCategoryPie, type CategoryPieItem } from '@/api/modules/chart';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  selectPlatform: '选择平台',
  platform: '平台',
  categoryShare: '品类占比',
  category: '品类',
  sales: '销量：',
  ratio: '占比：',
  bizError: '业务错误',
  loadFailed: '加载饼图失败',
};

const PIE_COLORS = ['#2563eb', '#06b6d4', '#22c55e', '#84cc16', '#f59e0b', '#f97316', '#ec4899', '#a855f7'];

const emit = defineEmits<{
  (e: 'category-click', categoryId: number, categoryName: string, platformId: number): void;
}>();

const platforms = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const platformId = ref<number>(1);
const platformName = computed(
  () => platforms.value.find((p) => p.id === platformId.value)?.name ?? text.platform
);

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let resizeObserver: ResizeObserver | null = null;

const CATEGORY_ID_BY_NAME = new Map<string, number>();
const lineCacheByPlatform = new Map<number, CategoryPieItem[]>();

function pickDefaultPlatformId(list: PlatformMeta[]) {
  const rows = Array.isArray(list) ? list : [];
  const douyin = rows.find((p) => String(p.code || '').toLowerCase() === 'douyin' || p.name === '抖音');
  if (douyin?.id && douyin.id > 0) {
    return douyin.id;
  }
  const first = rows[0]?.id;
  return Number.isFinite(first) && Number(first) > 0 ? Number(first) : 1;
}

function resolveCategoryId(name: string, fallback: number) {
  return CATEGORY_ID_BY_NAME.get(name) ?? fallback;
}

function colorForId(id: number): string {
  const normalized = Math.max(0, Number(id || 0) - 1);
  return PIE_COLORS[normalized % PIE_COLORS.length] || '#2563eb';
}

function handleResize() {
  chart?.resize();
}

function resize() {
  chart?.resize();
}

function bindClick() {
  if (!chart) return;
  chart.off('click');
  chart.on('click', (params: any) => {
    if (params?.seriesType !== 'pie') return;
    const data = params.data as any;
    const categoryId = Number(data?.categoryId);
    const categoryName = String(params?.name ?? '');
    if (!categoryId) return;
    emit('category-click', categoryId, categoryName, platformId.value);
  });
}

function renderPie(list: CategoryPieItem[]) {
  if (!chart) return;

  const seriesData = list.map((item: any, idx: number) => {
    const name: string = item.category ?? item.categoryName ?? item.name ?? '';
    const value: number = Number(item.cnt ?? item.value ?? 0);
    const categoryId = Number(item.categoryId ?? item.category_id) || resolveCategoryId(name, idx + 1);
    return {
      name,
      value,
      categoryId,
      itemStyle: { color: colorForId(categoryId), borderColor: '#ffffff', borderWidth: 1 },
    };
  });

  chart.setOption(
    {
      title: {
        text: `${platformName.value} ${text.categoryShare}`,
        left: 'center',
        top: 2,
        textStyle: { color: '#171717', fontSize: 18, fontWeight: 700 },
      },
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(10, 10, 10, 0.95)',
        borderColor: 'rgba(255, 255, 255, 0.4)',
        textStyle: { color: '#f4f4f4' },
        formatter: (p: any) => `${p.name}<br/>${text.sales}${p.value}<br/>${text.ratio}${p.percent}%`,
      },
      legend: {
        type: 'scroll',
        orient: 'vertical',
        left: 6,
        top: 34,
        itemWidth: 16,
        itemHeight: 10,
        textStyle: { color: '#393939', fontSize: 13 },
      },
      series: [
        {
          name: text.category,
          type: 'pie',
          radius: ['36%', '66%'],
          center: ['56%', '54%'],
          cursor: 'pointer',
          data: seriesData,
          label: { color: '#3b3b3b' },
          labelLine: { lineStyle: { color: '#7a7a7a' } },
          emphasis: {
            scale: true,
            scaleSize: 8,
            itemStyle: {
              shadowBlur: 20,
              shadowOffsetX: 0,
              shadowColor: 'rgba(0, 0, 0, 0.28)',
            },
          },
        },
      ],
    },
    { notMerge: false, lazyUpdate: true }
  );

  bindClick();
}

function applyLineData(platform: number, rows: CategoryPieItem[]) {
  const pid = Number(platform || 0);
  if (!pid) return;
  const safeRows = Array.isArray(rows) ? rows : [];
  lineCacheByPlatform.set(pid, safeRows);
  if (pid === platformId.value) {
    renderPie(safeRows);
  }
}

function applyLineBatch(lineByPlatform: Record<string, CategoryPieItem[]>, focusPlatformId?: number) {
  if (lineByPlatform && typeof lineByPlatform === 'object') {
    Object.entries(lineByPlatform).forEach(([pid, rows]) => {
      const parsedPid = Number(pid);
      if (!Number.isFinite(parsedPid) || parsedPid <= 0) return;
      lineCacheByPlatform.set(parsedPid, Array.isArray(rows) ? rows : []);
    });
  }

  const focus = Number(focusPlatformId || 0);
  if (focus > 0 && lineCacheByPlatform.has(focus)) {
    platformId.value = focus;
  }

  const currentRows = lineCacheByPlatform.get(platformId.value);
  if (currentRows) {
    renderPie(currentRows);
  }
}

async function refresh() {
  try {
    const res = (await getCategoryPie(platformId.value)).data;
    if (res.code !== 200) throw new Error(res.msg || text.bizError);
    const rows = Array.isArray(res.data) ? res.data : [];
    lineCacheByPlatform.set(platformId.value, rows);
    renderPie(rows);
  } catch (e) {
    console.error(`${text.loadFailed}:`, e);
    renderPie([]);
  }
}

async function onPlatformChange() {
  const cached = lineCacheByPlatform.get(platformId.value);
  if (cached) {
    renderPie(cached);
    return;
  }
  await refresh();
}

defineExpose({
  refresh,
  resize,
  applyLineData,
  applyLineBatch,
});

onMounted(async () => {
  await nextTick();
  platforms.value = await loadPlatformList();
  platformId.value = pickDefaultPlatformId(platforms.value);
  if (!chartRef.value) return;
  chart = echarts.init(chartRef.value);
  resizeObserver = new ResizeObserver(() => {
    chart?.resize();
  });
  resizeObserver.observe(chartRef.value);

  const cached = lineCacheByPlatform.get(platformId.value);
  if (cached) {
    renderPie(cached);
  } else {
    await refresh();
  }

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
</script>

<style scoped>
.line-content {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.pie-wrap {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.toolbar {
  width: 100%;
  display: flex;
  justify-content: flex-start;
  margin: 6px 0 8px;
}

.chart {
  width: 100%;
  flex: 1;
  min-height: 220px;
}
</style>

