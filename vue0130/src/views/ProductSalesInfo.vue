<template>
  <section class="product-sales-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>
            {{ text.productLabel }}{{ productName || '-' }}
            <span v-if="categoryName">| {{ text.categoryLabel }}{{ categoryName }}</span>
            <span v-if="platformName">| {{ text.platformLabel }}{{ platformName }}</span>
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
        <div class="stats">
          <div class="stat-card">
            <div class="label">{{ text.countLabel }}</div>
            <div class="value">{{ rows.length }}</div>
          </div>
          <div class="stat-card">
            <div class="label">{{ text.amountLabel }}</div>
            <div class="value">CNY {{ formatAmount(totalAmount) }}</div>
          </div>
        </div>

        <el-table
          ref="salesTableRef"
          :data="rows"
          border
          stripe
          height="640"
          :default-sort="{ prop: 'co_created_at', order: 'descending' }"
          :row-class-name="rowClassName"
          @row-click="onRowClick"
        >
          <el-table-column
            prop="co_created_at"
            :label="text.colTime"
            min-width="170"
            sortable
            :sort-method="sortByCreatedAt"
            :sort-orders="['descending', 'ascending', null]"
          />
          <el-table-column prop="co_order_no" :label="text.colOrderNo" min-width="190" />
          <el-table-column prop="product_name" :label="text.colProduct" min-width="170" />
          <el-table-column prop="p_brand" :label="text.colBrand" min-width="130" />
          <el-table-column
            prop="amount"
            :label="text.colAmount"
            min-width="130"
            align="right"
            sortable
            :sort-method="sortByAmount"
            :sort-orders="['descending', 'ascending', null]"
          >
            <template #default="{ row }">CNY {{ formatAmount(Number(row.amount || 0)) }}</template>
          </el-table-column>
          <el-table-column prop="user_name" :label="text.colUser" min-width="120" />
          <el-table-column prop="user_email" :label="text.colEmail" min-width="190" />
          <el-table-column prop="pr_name" :label="text.colProvince" min-width="110" />
          <el-table-column prop="c_name" :label="text.colCity" min-width="110" />
        </el-table>

        <div v-if="!rows.length" class="empty-wrap">
          <el-empty :description="text.empty" />
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getCategoryData, type CategoryOrderRecord } from '@/api/category/category';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

type OrderRow = PlatformRecord | CategoryOrderRecord;

const text = {
  title: '产品销售明细',
  back: '返回分类消费数据',
  productLabel: '产品：',
  categoryLabel: '分类：',
  platformLabel: '平台：',
  loading: '正在加载产品销售数据...',
  fetchFailed: '获取产品销售数据失败',
  missingProduct: '缺少产品参数',
  empty: '当前产品暂无订单',
  countLabel: '订单数',
  amountLabel: '总金额',
  colTime: '订单创建时间',
  colOrderNo: '订单号',
  colProduct: '商品',
  colBrand: '分类/品牌',
  colAmount: '金额',
  colUser: '用户',
  colEmail: '邮箱',
  colProvince: '省份',
  colCity: '城市',
};

const route = useRoute();
const router = useRouter();
const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);

const rows = ref<OrderRow[]>([]);
const salesTableRef = ref<any>(null);
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

const productName = computed(() => parseQueryString(route.query.productName));
const categoryName = computed(() => parseQueryString(route.query.categoryName));
const selectedPlatformId = computed(() => parseQueryNumber(route.query.platformId));
const platformName = computed(
  () => platformList.value.find((item) => item.id === selectedPlatformId.value)?.name || ''
);

const totalAmount = computed(() =>
  rows.value.reduce((sum, row: any) => sum + Number(row.amount || 0), 0)
);

function toTimestamp(raw: string) {
  const value = new Date(raw).getTime();
  return Number.isFinite(value) ? value : 0;
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function toAmount(raw: unknown) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : 0;
}

function sortByAmount(a: any, b: any) {
  return toAmount(a?.amount) - toAmount(b?.amount);
}

function sortByCreatedAt(a: any, b: any) {
  return toTimestamp(String(a?.co_created_at || '')) - toTimestamp(String(b?.co_created_at || ''));
}

function matchProductName(value: unknown) {
  return String(value || '').trim() === productName.value;
}

async function fetchByPlatform(platformId: number): Promise<OrderRow[]> {
  if (categoryName.value) {
    const res = await getCategoryData(platformId, categoryName.value);
    if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
    return res.data || [];
  }
  const res = await getPlatformData(platformId);
  if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
  return res.data || [];
}

async function loadRows() {
  const reqId = ++requestSerial;
  if (!productName.value) {
    rows.value = [];
    error.value = text.missingProduct;
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

    rows.value = source
      .filter((row: any) => matchProductName(row.product_name))
      .sort((a: any, b: any) => toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at));

    await nextTick();
    salesTableRef.value?.sort?.('co_created_at', 'descending');
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

function resolvePlatformIdByName(name: string) {
  return platformList.value.find((item) => item.name === name)?.id ?? null;
}

function onRowClick(row: any) {
  const orderId = Number(row?.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) return;

  const query: Record<string, string> = {
    orderId: String(orderId),
    backTo: 'productSales',
    productName: productName.value,
  };

  const pid = selectedPlatformId.value || resolvePlatformIdByName(String(row?.pf_name || ''));
  if (pid) query.platformId = String(pid);
  if (categoryName.value) query.categoryName = categoryName.value;

  void router.push({
    name: 'HomeAllOrderDetail',
    query,
  });
}

function goBack() {
  const query: Record<string, string> = {};
  if (selectedPlatformId.value) query.platformId = String(selectedPlatformId.value);
  if (categoryName.value) query.categoryName = categoryName.value;
  void router.push({
    name: 'HomeCategory',
    query,
  });
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
.product-sales-page {
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

.stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
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
  .product-sales-page {
    padding: 12px;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .stats {
    grid-template-columns: 1fr;
  }
}
</style>
