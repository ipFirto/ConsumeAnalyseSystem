<template>
  <div class="chart-page">
    <div class="toolbar">
      <el-checkbox-group v-model="checkboxGroup1">
        <el-checkbox-button v-for="p in platforms" :key="p" :value="p">
          {{ p }}
        </el-checkbox-button>
      </el-checkbox-group>

      <div class="right-tools">
        <el-switch
          v-model="followLatest"
          active-text="跟随最新"
          inactive-text="停留查看"
        />
        <el-button size="small" @click="backToLatest">回到最新</el-button>
      </div>
    </div>

    <div ref="chartRef" class="chart"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from "vue";
import * as echarts from "echarts";
// import { fetchPlatformData } from "@/api/modules/chart";
import type { ConsumeOrderDTO } from "@/api/modules/chart";

/** 1) 平台列表 */
const platforms = ["抖音", "天猫", "京东", "拼多多", "得物"] as const;
type PlatformName = (typeof platforms)[number];

/** 2) 平台名 -> 后端 platformId（⚠️按你的真实映射改） */
const PLATFORM_ID: Record<PlatformName, number> = {
  抖音: 1,
  天猫: 2,
  京东: 3,
  拼多多: 4,
  得物: 5,
};

/** 3) 每个平台固定颜色 */
const COLOR: Record<PlatformName, string> = {
  抖音: "#5470C6",
  天猫: "#91CC75",
  京东: "#EE6666",
  拼多多: "#FAC858",
  得物: "#73C0DE",
};

const checkboxGroup1 = ref<PlatformName[]>([...platforms]);

/** 是否跟随最新（新数据出现在右边，窗口滑动） */
const followLatest = ref(true);

const chartRef = ref<HTMLDivElement | null>(null);
let chart: echarts.ECharts | null = null;
let ro: ResizeObserver | null = null;
let timer: number | null = null;

/** 避免 watch + interval 并发拉取 */
// let pulling = false;

/** programmatic dataZoom 时，避免把 followLatest 误判为用户操作 */
let programmaticZooming = false;

/** 每个平台的数据 */
const ordersByPlatform = ref<Record<PlatformName, ConsumeOrderDTO[]>>({} as any);

/** ---------------- 时间处理 ---------------- */
function toTimeValue(t: string) {
  const iso = t?.includes("T") ? t : t?.replace(" ", "T");
  return new Date(iso).getTime();
}

/** 只保留近 1 小时 */
function keepLastHour(list: ConsumeOrderDTO[]) {
  const now = Date.now();
  const oneHourAgo = now - 60 * 60 * 1000;
  return list
    .filter((o) => {
      const tt = toTimeValue(o.coCreatedAt);
      return tt >= oneHourAgo && tt <= now;
    })
    .sort((a, b) => toTimeValue(a.coCreatedAt) - toTimeValue(b.coCreatedAt));
}

// /** 去重合并（按 coId） */
// function mergeById(oldList: ConsumeOrderDTO[], newList: ConsumeOrderDTO[]) {
//   const map = new Map<number, ConsumeOrderDTO>();
//   for (const i of oldList) map.set(i.coId, i);
//   for (const i of newList) map.set(i.coId, i);
//   return Array.from(map.values());
// }

/** 转成 series 点：附带 productName 给 tooltip */
function toSeriesData(list: ConsumeOrderDTO[]) {
  const sorted = [...list].sort(
    (a, b) => toTimeValue(a.coCreatedAt) - toTimeValue(b.coCreatedAt)
  );

  return sorted.map((i) => ({
    value: [toTimeValue(i.coCreatedAt), i.amount],
    productName: i.productName,
    coId: i.coId,
    orderNo: i.coOrderNo,
  }));
}

/** ---------------- localStorage：每个平台单独缓存 ---------------- */
const KEY = (pid: number) => `chart_orders_${pid}`;

function loadCache(pid: number): ConsumeOrderDTO[] {
  try {
    const raw = localStorage.getItem(KEY(pid));
    if (!raw) return [];
    return JSON.parse(raw) as ConsumeOrderDTO[];
  } catch {
    return [];
  }
}
// function saveCache(pid: number, list: ConsumeOrderDTO[]) {
//   localStorage.setItem(KEY(pid), JSON.stringify(list));
// }

/** ---------------- ECharts option（固定结构） ---------------- */
const option: echarts.EChartsOption = {
  title: { text: "各平台实时消费数据" },
  grid: { left: 60, right: 30, top: 70, bottom: 70 },
  legend: { top: 32, data: [...platforms] },
  tooltip: {
    trigger: "axis",
    axisPointer: { type: "line" },
    formatter: (params: any) => {
      const first = params?.[0];
      const t = first?.data?.value?.[0];
      const timeStr = t ? new Date(t).toLocaleString() : "-";

      const lines = (params || [])
        .map((p: any) => {
          const d = p.data || {};
          const name = d.productName ?? "-";
          const amount = d.value?.[1] ?? "-";
          return `
            <div style="display:flex;gap:8px;align-items:center;">
              <span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};"></span>
              <span><b>${p.seriesName}</b>：${name}，金额：${amount}</span>
            </div>
          `;
        })
        .join("");

      return `
        <div style="line-height:1.6">
          <div><b>时间：</b>${timeStr}</div>
          <div style="margin-top:6px">${lines}</div>
        </div>
      `;
    },
  },
  xAxis: { type: "time" },
  yAxis: { type: "value" },
  dataZoom: [
    { type: "inside", xAxisIndex: 0, filterMode: "filter", zoomOnMouseWheel: "ctrl" },
    { type: "slider", xAxisIndex: 0, filterMode: "filter", height: 24, bottom: 20 },
  ],

  // ✅ 关键：固定 5 条 series（永远不替换结构），只更新 data
  series: platforms.map((pname) => ({
    name: pname,
    type: "line",
    data: [],
    smooth: false,
    sampling: "none",
    showSymbol: false,
    animation: true,
    animationDurationUpdate: 200,
    animationEasingUpdate: "linear",
    lineStyle: { color: COLOR[pname] },
    itemStyle: { color: COLOR[pname] },
  })),
};

/** ---------------- 跟随最新窗口（近 1 小时） ---------------- */
function setWindowToLatestOneHour() {
  if (!chart) return;
  const now = Date.now();
  const oneHourAgo = now - 60 * 60 * 1000;

  programmaticZooming = true;
  chart.dispatchAction({
    type: "dataZoom",
    dataZoomIndex: 0,
    startValue: oneHourAgo,
    endValue: now,
  });
  chart.dispatchAction({
    type: "dataZoom",
    dataZoomIndex: 1,
    startValue: oneHourAgo,
    endValue: now,
  });

  // 下一帧释放（避免 datazoom 事件误判）
  requestAnimationFrame(() => {
    programmaticZooming = false;
  });
}

function backToLatest() {
  followLatest.value = true;
  setWindowToLatestOneHour();
}

/** ---------------- 渲染：只更新 data（不重建图） ---------------- */
function render() {
  if (!chart) return;

  const selected = new Set<PlatformName>(checkboxGroup1.value);

  // 只更新 data：未选中的平台置空（或你也可以用 opacity 隐藏）
  const seriesUpdate = platforms.map((pname) => {
    const list = keepLastHour(ordersByPlatform.value[pname] || []);
    return {
      name: pname,
      data: selected.has(pname) ? toSeriesData(list) : [],
    };
  });

  // ✅ 不要 replaceMerge，不要重建 series
  chart.setOption(
    {
      legend: { data: checkboxGroup1.value }, // 图例只显示选中的
      series: seriesUpdate,
    },
    { notMerge: false, lazyUpdate: true }
  );

  // ✅ 只在“跟随最新”时才移动窗口
  if (followLatest.value) {
    setWindowToLatestOneHour();
  }
}

/** ---------------- 拉取某个平台并合并（后端全量/增量都兼容） ---------------- */
// async function pullOne(pname: PlatformName) {
//   const pid = PLATFORM_ID[pname];
//   const incoming = await fetchPlatformData(pid); // 可能是全量（第一次）/ 增量（之后）

//   // 当前已缓存的数据
//   const current = ordersByPlatform.value[pname] || [];

//   // ✅ 只取真正“新增”的（避免重复追加）
//   const existingIds = new Set(current.map(i => i.coId));
//   const newOnes = incoming.filter(i => !existingIds.has(i.coId));

//   if (newOnes.length === 0) return;

//   // 追加到本地缓存（保持升序）
//   const merged = keepLastHour(mergeById(current, newOnes));
//   ordersByPlatform.value[pname] = merged;
//   saveCache(pid, merged);

//   // ✅ 只把新增点 append 到图上（不会整段重绘）
//   if (chart) {
//     const idx = PLATFORM_ID[pname];
//     const appendPts = toSeriesData(newOnes); // 只转新增那部分
//     chart.appendData({ seriesIndex: idx, data: appendPts });
//   }
// }

let trimTick = 0;
/** 拉取当前选中的平台（加锁避免并发） */
async function pullSelectedOnce() {
  // ...并发 pullOne(selected)

  trimTick++;
  if (trimTick % 20 === 0) { // 20 * 3s = 60s 修剪一次
    // 每分钟做一次全量 setOption 修剪（只为删除 1 小时前的点）
    render(); 
  }

  if (followLatest.value) setWindowToLatestOneHour();
}

/** ---------------- 轮询 ---------------- */
function startPolling() {
  stopPolling();
  timer = window.setInterval(async () => {
    try {
      await pullSelectedOnce();
    } catch (e) {
      console.error("轮询失败：", e);
    }
  }, 1000); // 轮询间隔：你想更稳可以改 5000
}

function stopPolling() {
  if (timer != null) {
    clearInterval(timer);
    timer = null;
  }
}

/** ---------------- 生命周期 ---------------- */
onMounted(async () => {
  if (!chartRef.value) return;

  // 确保 DOM 已有尺寸（避免 echarts 宽高 0）
  await nextTick();
  await new Promise<void>((r) => requestAnimationFrame(() => r()));

  chart = echarts.init(chartRef.value);
  chart.setOption(option);

  // 用户拖动/缩放 -> 视为在查看历史，自动关闭跟随最新（避免你感觉“图总在动”）
  chart.on("datazoom", () => {
    if (programmaticZooming) return;
    // 这是用户操作触发的 datazoom
    followLatest.value = false;
  });

  ro = new ResizeObserver(() => chart?.resize());
  ro.observe(chartRef.value);

  // 1) 先恢复缓存（所有平台都恢复，勾选时秒出图）
  const initData = {} as Record<PlatformName, ConsumeOrderDTO[]>;
  for (const p of platforms) {
    const pid = PLATFORM_ID[p];
    initData[p] = keepLastHour(loadCache(pid));
  }
  ordersByPlatform.value = initData;

  render(); // 先用缓存画一次
  // 初始跟随最新
  if (followLatest.value) setWindowToLatestOneHour();

  // 2) 再向后端拉一次（选中的平台）
  await pullSelectedOnce();

  // 3) 开启轮询
  startPolling();
});

onBeforeUnmount(() => {
  stopPolling();
  ro?.disconnect();
  ro = null;
  chart?.dispose();
  chart = null;
});

/** checkbox 勾选变化：立即切换显示 + 拉取一次 */
watch(
  () => checkboxGroup1.value.slice(),
  async () => {
    render(); // 先立即切换显示（不用等接口）
    await pullSelectedOnce();
  }
);
</script>

<style scoped>
.chart-page {
  width: 100%;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 10px 6px;
}

.right-tools {
  display: flex;
  align-items: center;
  gap: 10px;
}

.chart {
  width: 100%;
  min-height: 800px;
}
</style>
