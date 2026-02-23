<template>
  <section class="province-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>
            {{ text.platformContext }}
            {{ platformLabel }}
            <span v-if="selectedProvinceName"> | {{ text.focusProvince }} {{ selectedProvinceName }}</span>
          </p>
        </div>
        <el-input v-model.trim="keyword" :placeholder="text.searchPlaceholder" clearable style="width: 220px" />
      </header>

      <div v-if="loading" class="state">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loading }}</span>
      </div>

      <div v-else-if="error" class="state error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <template v-else-if="filteredRows.length">
        <div class="stat-grid">
          <div class="stat-card">
            <div class="label">{{ text.provinceCount }}</div>
            <div class="value">{{ filteredRows.length }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.totalOrders }}</div>
            <div class="value">{{ stats.totalOrders }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.topProvince }}</div>
            <div class="value">{{ stats.topProvince }}</div>
          </div>
        </div>

        <el-table
          :data="filteredRows"
          border
          stripe
          height="560"
          row-key="province_id"
          :row-class-name="rowClassName"
          @row-click="onRowClick"
        >
          <el-table-column type="index" :label="text.rank" width="80" align="center" />
          <el-table-column prop="province_name" :label="text.province" min-width="180">
            <template #default="{ row }">
              <span>{{ row.province_name }}</span>
              <el-tag v-if="row.province_id === selectedProvinceId" size="small" type="warning" class="tag">
                {{ text.focused }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="order_total" :label="text.orderCount" min-width="160" align="right" />
          <el-table-column :label="text.share" min-width="220">
            <template #default="{ row }">
              <el-progress :percentage="getShare(row.order_total)" :stroke-width="12" />
            </template>
          </el-table-column>
        </el-table>
      </template>

      <div v-else class="state">
        <el-empty :description="text.empty" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getSmoothByOriginal, type ProvinceOrderCountRow } from '@/api/modules/chart';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const text = {
  title: '省份消费数据',
  platformContext: '当前维度：',
  focusProvince: '图表选中省份：',
  searchPlaceholder: '搜索省份名称',
  loading: '正在加载省份数据...',
  provinceCount: '省份数',
  totalOrders: '总订单量',
  topProvince: '最高省份',
  rank: 'TOP',
  province: '省份',
  orderCount: '订单量',
  share: '占比',
  focused: '已选',
  empty: '暂无省份数据',
  fetchFailed: '获取省份数据失败',
};

const props = defineProps<{
  provinceId?: number | null;
  platformId?: number | null;
}>();
const router = useRouter();

const rows = ref<ProvinceOrderCountRow[]>([]);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const selectedProvinceId = ref<number | null>(props.provinceId ?? null);
const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);

const platformLabel = computed(() =>
  props.platformId
    ? `${platformList.value.find((p) => p.id === props.platformId)?.name || `ID ${props.platformId}`}`
    : '全平台'
);

const filteredRows = computed(() => {
  const key = keyword.value.trim();
  const list = rows.value;
  if (!key) return list;
  return list.filter((x) => x.province_name?.includes(key));
});

const stats = computed(() => {
  const totalOrders = filteredRows.value.reduce((sum, item) => sum + Number(item.order_total || 0), 0);
  const topProvince = filteredRows.value[0]?.province_name ?? '-';
  return { totalOrders, topProvince };
});

const selectedProvinceName = computed(() => {
  const row = rows.value.find((x) => x.province_id === selectedProvinceId.value);
  return row?.province_name || '';
});

function getShare(orderTotal: number) {
  const total = stats.value.totalOrders;
  if (!total) return 0;
  return Number(((Number(orderTotal || 0) / total) * 100).toFixed(2));
}

function rowClassName() {
  return 'clickable-row';
}

function onRowClick(row: ProvinceOrderCountRow) {
  const provinceId = Number(row?.province_id);
  const provinceName = String(row?.province_name || '').trim();
  if (!Number.isFinite(provinceId) || provinceId <= 0 || !provinceName) return;

  const query: Record<string, string> = {
    provinceId: String(provinceId),
    provinceName,
  };
  if (props.platformId) query.platformId = String(props.platformId);

  void router.push({
    name: 'HomeProvinceInsight',
    query,
  });
}

async function loadData() {
  loading.value = true;
  error.value = '';
  try {
    const res: any = await getSmoothByOriginal();
    const body = res?.data ?? {};
    const code = Number(body.code ?? 200);
    if (code !== 200) throw new Error(body.msg || text.fetchFailed);

    const data = (body.data || []) as ProvinceOrderCountRow[];
    rows.value = [...data].sort((a, b) => Number(b.order_total) - Number(a.order_total));
  } catch (err: any) {
    rows.value = [];
    error.value = err?.message || text.fetchFailed;
    ElMessage.error(error.value);
  } finally {
    loading.value = false;
  }
}

watch(
  () => props.provinceId,
  (val) => {
    selectedProvinceId.value = val ?? null;
  },
  { immediate: true }
);

watch(
  () => props.platformId,
  () => {
    void loadData();
  },
  { immediate: true }
);

onMounted(async () => {
  platformList.value = await loadPlatformList();
});

defineExpose({
  refresh: loadData,
});
</script>

<style scoped>
.province-page {
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
  align-items: center;
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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

.tag {
  margin-left: 8px;
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

@media (max-width: 900px) {
  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
