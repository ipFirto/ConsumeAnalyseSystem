<template>
  <section class="order-detail-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>
            {{ text.orderIdLabel }}{{ currentOrderId ?? '-' }}
            <span v-if="resolvedPlatformName">| {{ text.platformLabel }}{{ resolvedPlatformName }}</span>
          </p>
        </div>
        <div class="header-actions">
          <el-button @click="goBack">{{ backButtonText }}</el-button>
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

      <template v-else-if="order">
        <div class="summary-grid">
          <div class="summary-card">
            <div class="label">{{ text.amount }}</div>
            <div class="value">CNY {{ formatAmount(Number(order.amount || 0)) }}</div>
          </div>
          <div class="summary-card">
            <div class="label">{{ text.orderNo }}</div>
            <div class="value">{{ order.co_order_no || '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="label">{{ text.userName }}</div>
            <div class="value">{{ order.user_name || '-' }}</div>
          </div>
          <div class="summary-card">
            <div class="label">{{ text.createdAt }}</div>
            <div class="value">{{ order.co_created_at || '-' }}</div>
          </div>
        </div>

        <div class="detail-card">
          <div class="detail-title">{{ text.allFields }}</div>
          <div class="detail-grid">
            <div v-for="item in detailItems" :key="item.key" class="detail-item">
              <div class="detail-label">{{ item.label }}</div>
              <div class="detail-value">{{ item.value }}</div>
            </div>
          </div>
        </div>
      </template>

      <div v-else class="state">
        <el-empty :description="text.empty" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

const props = defineProps<{
  orderId?: number | null;
  visible?: boolean;
}>();

const text = {
  title: '订单详情',
  backPlatform: '返回平台消费数据',
  backPlatformDate: '返回平台日期明细',
  backProvinceInsight: '返回省份消费洞察',
  backCategory: '返回分类订单明细',
  backProductSales: '返回产品销量明细',
  backOrderDetail: '返回订单详情列表',
  orderIdLabel: '订单ID：',
  platformLabel: '平台：',
  loading: '正在加载订单详情...',
  missingOrderId: '缺少订单ID参数',
  fetchFailed: '获取订单详情失败',
  notFound: '未找到该订单',
  empty: '暂无订单数据',
  allFields: '订单完整参数',
  amount: '订单金额',
  orderNo: '订单号',
  userName: '用户名称',
  createdAt: '下单时间',
};

const route = useRoute();
const router = useRouter();

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);
const loading = ref(false);
const error = ref('');
const order = ref<PlatformRecord | null>(null);
const resolvedPlatformId = ref<number | null>(null);
const resolvedPlatformName = ref('');
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

const routeOrderId = computed(() => parseQueryNumber(route.query.orderId));
const currentOrderId = computed(() => props.orderId ?? routeOrderId.value ?? null);
const routePlatformId = computed(() => parseQueryNumber(route.query.platformId));
const backTo = computed(() => parseQueryString(route.query.backTo));
const dateQuery = computed(() => parseQueryString(route.query.date));
const provinceIdQuery = computed(() => parseQueryString(route.query.provinceId));
const provinceNameQuery = computed(() => parseQueryString(route.query.provinceName));
const categoryNameQuery = computed(() => parseQueryString(route.query.categoryName));
const timeQuery = computed(() => parseQueryString(route.query.time));
const productNameQuery = computed(() => parseQueryString(route.query.productName));

const backButtonText = computed(() => {
  if (backTo.value === 'platformDate') return text.backPlatformDate;
  if (backTo.value === 'provinceInsight') return text.backProvinceInsight;
  if (backTo.value === 'category') return text.backCategory;
  if (backTo.value === 'productSales') return text.backProductSales;
  if (backTo.value === 'orderDetail') return text.backOrderDetail;
  return text.backPlatform;
});

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function formatStatus(status: number) {
  return Number(status) === 1 ? '启用' : '禁用';
}

const detailItems = computed(() => {
  if (!order.value) return [];
  const row = order.value;
  return [
    { key: 'co_id', label: '订单ID', value: String(row.co_id ?? '-') },
    { key: 'co_order_no', label: '订单号', value: row.co_order_no || '-' },
    { key: 'amount', label: '订单金额', value: `CNY ${formatAmount(Number(row.amount || 0))}` },
    { key: 'pf_name', label: '平台名称', value: row.pf_name || resolvedPlatformName.value || '-' },
    { key: 'product_name', label: '商品名称', value: row.product_name || '-' },
    { key: 'p_brand', label: '品牌/分类', value: row.p_brand || '-' },
    { key: 'co_created_at', label: '下单时间', value: row.co_created_at || '-' },
    { key: 'co_remark', label: '订单备注', value: row.co_remark || '-' },
    { key: 'user_id', label: '用户ID', value: String(row.user_id ?? '-') },
    { key: 'user_name', label: '用户名称', value: row.user_name || '-' },
    { key: 'user_email', label: '用户邮箱', value: row.user_email || '-' },
    { key: 'u_phone', label: '手机号', value: row.u_phone || '-' },
    { key: 'u_status', label: '用户状态', value: formatStatus(Number(row.u_status || 0)) },
    { key: 'pr_id', label: '省份ID', value: String(row.pr_id ?? '-') },
    { key: 'pr_name', label: '省份', value: row.pr_name || '-' },
    { key: 'pr_type', label: '省份类型', value: String(row.pr_type ?? '-') },
    { key: 'c_id', label: '城市ID', value: String(row.c_id ?? '-') },
    { key: 'c_name', label: '城市', value: row.c_name || '-' },
    { key: 'c_code', label: '城市编码', value: row.c_code || '-' },
  ];
});

async function findOrderInPlatform(platformId: number, orderId: number) {
  const res = await getPlatformData(platformId);
  if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
  return (res.data || []).find((item) => Number(item.co_id) === orderId) || null;
}

async function loadOrderDetail() {
  if (props.visible === false) return;

  const orderId = currentOrderId.value;
  if (!orderId) {
    order.value = null;
    error.value = text.missingOrderId;
    return;
  }

  const reqId = ++requestSerial;
  loading.value = true;
  error.value = '';
  order.value = null;
  resolvedPlatformId.value = null;
  resolvedPlatformName.value = '';

  try {
    if (!platformList.value.length) {
      platformList.value = await loadPlatformList();
    }

    const firstTryPlatformId = routePlatformId.value;
    let matched: PlatformRecord | null = null;
    let matchedPlatformId: number | null = null;

    if (firstTryPlatformId) {
      matched = await findOrderInPlatform(firstTryPlatformId, orderId);
      if (matched) matchedPlatformId = firstTryPlatformId;
    }

    if (!matched) {
      for (const p of platformList.value) {
        if (firstTryPlatformId && p.id === firstTryPlatformId) continue;
        const row = await findOrderInPlatform(p.id, orderId);
        if (row) {
          matched = row;
          matchedPlatformId = p.id;
          break;
        }
      }
    }

    if (reqId !== requestSerial) return;
    if (!matched) {
      error.value = text.notFound;
      return;
    }

    order.value = matched;
    resolvedPlatformId.value = matchedPlatformId;
    resolvedPlatformName.value =
      platformList.value.find((item) => item.id === matchedPlatformId)?.name || matched.pf_name || '-';
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
  }
}

function goBack() {
  if (backTo.value === 'provinceInsight') {
    const query: Record<string, string> = {};
    const pid = resolvedPlatformId.value || routePlatformId.value;
    if (pid) query.platformId = String(pid);
    if (provinceIdQuery.value) query.provinceId = provinceIdQuery.value;
    if (provinceNameQuery.value) query.provinceName = provinceNameQuery.value;
    void router.push({ name: 'HomeProvinceInsight', query });
    return;
  }

  if (backTo.value === 'platformDate') {
    const pid = resolvedPlatformId.value || routePlatformId.value;
    if (pid && dateQuery.value) {
      void router.push({
        name: 'HomePlatformDate',
        query: {
          platformId: String(pid),
          date: dateQuery.value,
        },
      });
      return;
    }
  }

  if (backTo.value === 'productSales') {
    const query: Record<string, string> = {};
    const pid = resolvedPlatformId.value || routePlatformId.value;
    if (pid) query.platformId = String(pid);
    if (productNameQuery.value) query.productName = productNameQuery.value;
    if (categoryNameQuery.value) query.categoryName = categoryNameQuery.value;
    void router.push({ name: 'HomeProductSales', query });
    return;
  }

  if (backTo.value === 'category') {
    const query: Record<string, string> = {};
    const pid = resolvedPlatformId.value || routePlatformId.value;
    if (pid) query.platformId = String(pid);
    if (categoryNameQuery.value) query.categoryName = categoryNameQuery.value;
    if (timeQuery.value) query.time = timeQuery.value;
    void router.push({ name: 'HomeCategoryInfo', query });
    return;
  }

  if (backTo.value === 'orderDetail') {
    void router.push({ name: 'HomeOrderDetail' });
    return;
  }

  const query: Record<string, string> = { restorePlatform: '1' };
  const pid = resolvedPlatformId.value || routePlatformId.value;
  if (pid) query.platformId = String(pid);
  void router.push({ name: 'HomePlatform', query });
}

watch(
  () => [currentOrderId.value, routePlatformId.value, props.visible, route.fullPath],
  () => {
    void loadOrderDetail();
  },
  { immediate: true }
);
</script>

<style scoped>
.order-detail-page {
  padding: 24px;
  min-height: calc(100dvh - 72px);
  display: flex;
}

.panel {
  background: rgba(255, 255, 255, 0.86);
  border: 1px solid rgba(59, 130, 246, 0.18);
  border-radius: 18px;
  padding: 18px;
  flex: 1;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  color: #1f3f67;
}

.panel-header p {
  margin: 8px 0 0;
  color: #567299;
  font-size: 14px;
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

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin: 10px 0 16px;
}

.summary-card {
  border-radius: 12px;
  padding: 12px;
  border: 1px solid rgba(37, 99, 235, 0.14);
  background: rgba(255, 255, 255, 0.82);
}

.summary-card .label {
  color: #5b6f8d;
  font-size: 13px;
}

.summary-card .value {
  margin-top: 6px;
  color: #1f3f67;
  font-size: 20px;
  font-weight: 700;
  word-break: break-all;
}

.detail-card {
  border-radius: 14px;
  border: 1px solid rgba(59, 130, 246, 0.14);
  background: rgba(255, 255, 255, 0.78);
  padding: 14px;
}

.detail-title {
  font-size: 15px;
  color: #35577f;
  margin-bottom: 12px;
  font-weight: 600;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.detail-item {
  border: 1px solid rgba(59, 130, 246, 0.16);
  border-radius: 10px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.74);
}

.detail-label {
  font-size: 12px;
  color: #5d7a9f;
}

.detail-value {
  margin-top: 4px;
  color: #1f3f67;
  font-size: 14px;
  word-break: break-all;
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
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .order-detail-page {
    padding: 12px;
    min-height: auto;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .summary-grid,
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
