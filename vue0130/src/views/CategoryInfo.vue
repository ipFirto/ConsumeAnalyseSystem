<template>
  <section class="category-info-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>
            {{ text.currentCategory }}{{ categoryNameLabel }}
            <span> | {{ text.currentTime }}{{ timeKey || '--' }}</span>
            <span> | {{ text.window }}</span>
          </p>
        </div>
        <div class="header-actions">
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

      <template v-else>
        <el-alert
          v-if="!timeKey"
          :title="text.needTime"
          type="warning"
          :closable="false"
          show-icon
          class="alert"
        />

        <el-table
          v-else
          :data="rows"
          border
          stripe
          height="640"
          :default-sort="{ prop: 'co_created_at', order: 'ascending' }"
          :row-class-name="rowClassName"
          @row-click="onRowClick"
        >
          <el-table-column prop="co_created_at" :label="text.colTime" min-width="170" sortable />
          <el-table-column prop="co_order_no" :label="text.colOrderNo" min-width="190" />
          <el-table-column prop="product_name" :label="text.colProduct" min-width="170" />
          <el-table-column prop="p_brand" :label="text.colBrand" min-width="120" />
          <el-table-column :label="text.colAmount" min-width="130" align="right" sortable>
            <template #default="{ row }">CNY {{ formatAmount(Number(row.amount || 0)) }}</template>
          </el-table-column>
          <el-table-column prop="user_name" :label="text.colUser" min-width="120" />
          <el-table-column prop="user_email" :label="text.colEmail" min-width="190" />
          <el-table-column prop="pr_name" :label="text.colProvince" min-width="110" />
          <el-table-column prop="c_name" :label="text.colCity" min-width="110" />
          <el-table-column prop="co_remark" :label="text.colRemark" min-width="160" />
        </el-table>

        <div v-if="timeKey && !rows.length" class="empty-wrap">
          <el-empty :description="text.empty" />
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getCategoryData, type CategoryOrderRecord } from '@/api/category/category';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

type OrderRow = PlatformRecord | CategoryOrderRecord;

const text = {
  title: '分类订单明细',
  back: '返回分类消费数据',
  currentCategory: '当前分类：',
  currentTime: '时间点：',
  window: '显示该时间点后一小时内订单',
  loading: '正在加载订单明细...',
  needTime: '缺少时间参数，请从分类趋势图点击时间点进入',
  empty: '当前时间窗口内无订单',
  fetchFailed: '获取订单明细失败',
  colTime: '下单时间',
  colOrderNo: '订单号',
  colProduct: '商品',
  colBrand: '分类/品牌',
  colAmount: '金额',
  colUser: '用户',
  colEmail: '邮箱',
  colProvince: '省份',
  colCity: '城市',
  colRemark: '备注',
};

const route = useRoute();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const rows = ref<OrderRow[]>([]);
const loading = ref(false);
const error = ref('');
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

const timeKey = computed(() => parseQueryString(route.query.time));
const selectedPlatformId = computed(() => parseQueryNumber(route.query.platformId));
const selectedCategoryName = computed(() => parseQueryString(route.query.categoryName));
const categoryNameLabel = computed(() => selectedCategoryName.value || '全部分类');

function toTimestamp(raw: string) {
  const value = new Date(raw).getTime();
  return Number.isFinite(value) ? value : 0;
}

function parseBucketStart(bucket: string) {
  if (!bucket) return null;
  const normalized = bucket.includes('T') ? bucket : bucket.replace(' ', 'T');
  const full = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(normalized) ? `${normalized}:00` : normalized;
  const d = new Date(full);
  if (Number.isNaN(d.getTime())) return null;
  return d;
}

function formatAmount(v: number) {
  return Number(v || 0).toFixed(2);
}

function resolvePlatformIdByName(name: string) {
  return platformList.value.find((item) => item.name === name)?.id ?? null;
}

async function fetchByPlatform(platformId: number): Promise<OrderRow[]> {
  if (selectedCategoryName.value) {
    const res = await getCategoryData(platformId, selectedCategoryName.value);
    if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
    return res.data || [];
  }
  const res = await getPlatformData(platformId);
  if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
  return res.data || [];
}

async function loadRows() {
  const reqId = ++requestSerial;
  const start = parseBucketStart(timeKey.value);
  if (!start) {
    rows.value = [];
    error.value = timeKey.value ? text.fetchFailed : '';
    return;
  }

  loading.value = true;
  error.value = '';

  try {
    if (!platformList.value.length) {
      platformList.value = await loadPlatformList();
    }

    let source: OrderRow[] = [];
    if (selectedPlatformId.value) {
      source = await fetchByPlatform(selectedPlatformId.value);
    } else {
      const all = await Promise.all(platformList.value.map((p) => fetchByPlatform(p.id).catch(() => [])));
      source = all.flat();
    }

    if (reqId !== requestSerial) return;

    const startMs = start.getTime();
    const endMs = startMs + 60 * 60 * 1000;

    rows.value = source
      .filter((row) => {
        const ts = toTimestamp(String((row as any).co_created_at || ''));
        return ts >= startMs && ts < endMs;
      })
      .sort((a, b) => toTimestamp((a as any).co_created_at) - toTimestamp((b as any).co_created_at));
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    rows.value = [];
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
  }
}

function rowClassName() {
  return 'clickable-row';
}

function onRowClick(row: any) {
  const orderId = Number(row?.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) return;

  const query: Record<string, string> = {
    orderId: String(orderId),
    backTo: 'category',
  };

  const pid = selectedPlatformId.value || resolvePlatformIdByName(String(row?.pf_name || ''));
  if (pid) query.platformId = String(pid);
  if (timeKey.value) query.time = timeKey.value;
  if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;

  void router.push({
    name: 'HomeAllOrderDetail',
    query,
  });
}

function goBack() {
  const query: Record<string, string> = {};
  if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
  if (selectedCategoryName.value) query.categoryName = selectedCategoryName.value;
  void router.push({ name: 'HomeCategory', query });
}

watch(
  () => route.fullPath,
  () => {
    void loadRows();
  },
  { immediate: true }
);
</script>

<style scoped>
.category-info-page {
  padding: 24px;
}

.panel {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(59, 130, 246, 0.18);
  border-radius: 18px;
  padding: 16px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
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

.state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #4d6a91;
}

.state.error {
  color: #d9534f;
}

.alert {
  margin-bottom: 10px;
}

.empty-wrap {
  margin-top: 10px;
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

@media (max-width: 768px) {
  .category-info-page {
    padding: 12px;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
