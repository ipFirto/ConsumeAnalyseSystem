<template>
  <section class="charts-dashboard">
    <div class="top-left">
      <div class="panel-card">
        <LineChart ref="lineChartRef" @category-click="onCategoryClick" />
      </div>
    </div>

    <div class="top-right">
      <div class="panel-card">
        <SmoothChart ref="smoothChartRef" @province-click="onProvinceClick" />
      </div>
    </div>

    <div class="bottom">
      <div class="panel-card panel-card-wide">
        <BarChart ref="barChartRef" @platform-click="onPlatformClick" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import BarChart from './BarChart.vue';
import LineChart from './LineChart.vue';
import SmoothChart from './SmoothChart.vue';
import {
  createDashboardStream,
  getDashboardSnapshot,
  type DashboardEventPayload,
  type HomeSnapshotPayload,
} from '@/api/modules/dashboard';
import type { CategoryPieItem, ProvinceOrderCountRow } from '@/api/modules/chart';

const emit = defineEmits<{
  (e: 'platform-click', platformId: number): void;
  (e: 'category-click', categoryId: number, categoryName: string, platformId?: number): void;
  (e: 'province-click', provinceId: number, provinceName: string, platformId?: number): void;
}>();

const props = defineProps<{
  visible?: boolean;
}>();

type BarChartExpose = {
  refresh?: () => void | Promise<void>;
  resize?: () => void;
  applyCounts?: (values: number[]) => void;
};

type LineChartExpose = {
  refresh?: () => void | Promise<void>;
  resize?: () => void;
  applyLineData?: (platformId: number, rows: CategoryPieItem[]) => void;
  applyLineBatch?: (lineByPlatform: Record<string, CategoryPieItem[]>, focusPlatformId?: number) => void;
};

type SmoothChartExpose = {
  refresh?: () => void | Promise<void>;
  resize?: () => void;
  applyRows?: (rows: ProvinceOrderCountRow[]) => void;
};

const lineChartRef = ref<LineChartExpose | null>(null);
const smoothChartRef = ref<SmoothChartExpose | null>(null);
const barChartRef = ref<BarChartExpose | null>(null);

let source: EventSource | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let reconnectDelayMs = 2000;
const MAX_RECONNECT_DELAY_MS = 12000;

function onPlatformClick(platformId: number) {
  emit('platform-click', platformId);
}

function onCategoryClick(categoryId: number, categoryName: string, platformId?: number) {
  emit('category-click', categoryId, categoryName, platformId);
}

function onProvinceClick(provinceId: number, provinceName: string, platformId?: number) {
  emit('province-click', provinceId, provinceName, platformId);
}

function applyHomePayload(payload?: DashboardEventPayload | HomeSnapshotPayload | null) {
  if (!payload || typeof payload !== 'object') return;

  const bar = (payload as any).bar;
  if (Array.isArray(bar)) {
    barChartRef.value?.applyCounts?.(bar.map((item) => Number(item || 0)));
  }

  const smooth = (payload as any).smooth;
  if (Array.isArray(smooth)) {
    smoothChartRef.value?.applyRows?.(smooth as ProvinceOrderCountRow[]);
  }

  const lineByPlatform = (payload as any).lineByPlatform;
  const linePlatformId = Number((payload as any).linePlatformId || 0) || undefined;
  if (lineByPlatform && typeof lineByPlatform === 'object') {
    lineChartRef.value?.applyLineBatch?.(
      lineByPlatform as Record<string, CategoryPieItem[]>,
      linePlatformId
    );
  } else {
    const line = (payload as any).line;
    if (Array.isArray(line)) {
      lineChartRef.value?.applyLineData?.(linePlatformId || 0, line as CategoryPieItem[]);
    }
  }
}

async function loadSnapshot() {
  const res = await getDashboardSnapshot({ scope: 'home' });
  applyHomePayload(res.data?.snapshot || null);
}

function closeStreamOnly() {
  if (source) {
    source.close();
    source = null;
  }
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function scheduleReconnect() {
  if (reconnectTimer) return;
  reconnectTimer = setTimeout(async () => {
    reconnectTimer = null;
    try {
      await loadSnapshot();
    } catch (err) {
      console.error('reload dashboard snapshot failed:', err);
    }
    openStream();
    reconnectDelayMs = Math.min(MAX_RECONNECT_DELAY_MS, reconnectDelayMs * 1.5);
  }, reconnectDelayMs);
}

function openStream() {
  closeStreamOnly();

  const token = localStorage.getItem('token') || undefined;
  source = createDashboardStream({
    topics: 'home',
    token,
    onOpen: () => {
      reconnectDelayMs = 2000;
    },
    onEvent: (evt) => {
      if (evt.type !== 'patch') return;
      applyHomePayload(evt.payload || null);
    },
    onError: (err) => {
      console.error('dashboard stream error:', err);
      closeStreamOnly();
      scheduleReconnect();
    },
  });
}

function stopStream() {
  clearReconnectTimer();
  closeStreamOnly();
}

async function bootstrapRealtime() {
  try {
    await loadSnapshot();
  } catch (err) {
    console.error('load dashboard snapshot failed:', err);
  }
  openStream();
}

async function refresh() {
  await loadSnapshot();
}

function resizeCharts() {
  lineChartRef.value?.resize?.();
  smoothChartRef.value?.resize?.();
  barChartRef.value?.resize?.();
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible) return;
    await nextTick();
    requestAnimationFrame(() => {
      resizeCharts();
    });
  }
);

onMounted(() => {
  void bootstrapRealtime();
});

onBeforeUnmount(() => {
  stopStream();
});

defineExpose({
  refresh,
  resize: resizeCharts,
});
</script>

<style scoped>
.charts-dashboard {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  gap: 18px;
}

.top-left,
.top-right,
.bottom {
  min-width: 0;
  min-height: 0;
}

.bottom {
  grid-column: 1 / -1;
}

.panel-card {
  height: 100%;
  min-height: 0;
  border-radius: 18px;
  border: 1px solid rgba(18, 18, 18, 0.22);
  background:
    linear-gradient(150deg, rgba(255, 255, 255, 0.86), rgba(232, 232, 232, 0.7)),
    radial-gradient(30rem 16rem at -10% -20%, rgba(255, 255, 255, 0.5), rgba(255, 255, 255, 0)),
    radial-gradient(30rem 16rem at 110% 130%, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0));
  box-shadow:
    0 16px 26px rgba(0, 0, 0, 0.11),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(4px);
  padding: 12px;
  overflow: hidden;
  position: relative;
}

.panel-card::before {
  content: '';
  position: absolute;
  left: -12%;
  top: -42%;
  width: 62%;
  height: 62%;
  border-radius: 999px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.34), rgba(255, 255, 255, 0));
  pointer-events: none;
}

.panel-card-wide {
  padding-top: 8px;
}

@media (max-width: 980px) {
  .charts-dashboard {
    grid-template-columns: 1fr;
    grid-template-rows: repeat(3, minmax(0, 1fr));
    min-height: 960px;
  }

  .bottom {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .charts-dashboard {
    gap: 12px;
    min-height: 720px;
  }

  .panel-card {
    border-radius: 14px;
    padding: 10px;
  }
}
</style>

