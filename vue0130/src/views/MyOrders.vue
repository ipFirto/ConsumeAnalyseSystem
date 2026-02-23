<template>
  <section class="my-orders-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>我的订单</h2>
          <p>默认按时间倒序展示，支持状态筛选与搜索。</p>
        </div>
        <div class="header-actions">
          <el-button
            type="success"
            :disabled="!selectedPayRows.length"
            :loading="batchPaying"
            @click="paySelectedOrders"
          >
            批量支付{{ selectedPayRows.length ? ` (${selectedPayRows.length})` : '' }}
          </el-button>
          <el-button :loading="loading" @click="loadOrders(true)">刷新</el-button>
          <el-button type="primary" @click="goBack">返回资源页</el-button>
        </div>
      </header>

      <div class="filter-bar">
        <el-radio-group v-model="statusFilter" size="small">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="paid">已支付</el-radio-button>
          <el-radio-button label="unpaid">未支付</el-radio-button>
          <el-radio-button label="timeout">已超时</el-radio-button>
          <el-radio-button label="canceled">已取消</el-radio-button>
        </el-radio-group>

        <el-input
          v-model.trim="keyword"
          clearable
          placeholder="搜索订单号或商品名"
          class="search-input"
        />
      </div>

      <div v-if="loading && !rows.length" class="state">正在加载订单...</div>
      <div v-else-if="error && !rows.length" class="state error">{{ error }}</div>
      <div v-else-if="!filteredRows.length" class="empty-wrap">
        <el-empty description="暂无符合条件的订单" />
      </div>

      <template v-else>
        <div class="table-wrap">
          <el-table
            class="my-orders-table"
            :data="pagedRows"
            border
            stripe
            row-key="co_id"
            height="100%"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="54" :selectable="isSelectableForPay" />
            <el-table-column prop="co_order_no" label="订单号" min-width="190" />
            <el-table-column prop="product_name" label="商品名" min-width="170" />
            <el-table-column prop="brand" label="品牌/品类" min-width="160">
              <template #default="{ row }">
                <span>{{ row.brand || '-' }}</span>
                <span v-if="row.category"> - {{ row.category }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="city_name" label="城市" min-width="120" />
            <el-table-column label="金额" min-width="130" align="right">
              <template #default="{ row }">CNY {{ formatAmount(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="剩余支付时间" min-width="140" align="center">
              <template #default="{ row }">
                <span v-if="Number(row.status) === 1">{{ countdownText(row) }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="co_created_at" label="创建时间" min-width="170" />
            <el-table-column label="操作" min-width="170" align="center" fixed="right">
              <template #default="{ row }">
                <div v-if="Number(row.status) === 1" class="order-actions">
                  <el-button
                    type="primary"
                    link
                    :loading="payingOrderNos.includes(row.co_order_no)"
                    @click="payOneOrder(row)"
                  >
                    立即支付
                  </el-button>
                  <el-button
                    type="danger"
                    link
                    :loading="cancelingOrderNos.includes(row.co_order_no)"
                    @click="cancelOneOrder(row)"
                  >
                    取消订单
                  </el-button>
                </div>
                <span v-else>-</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="pager-wrap">
          <span class="selection-info">
            已选待支付 {{ selectedPayRows.length }} 单，金额 CNY {{ formatAmount(selectedPayAmount) }}
          </span>
          <span class="pager-info">共 {{ filteredRows.length }} 条</span>
          <el-pagination
            v-model:current-page="page.current"
            v-model:page-size="page.size"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            :total="filteredRows.length"
          />
        </div>
      </template>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { cancelOrder, getOrderList, paySubmit, type MqOrderRecord, type OrderEventPayload } from '@/api/modules/mq';

type StatusFilter = 'all' | 'paid' | 'unpaid' | 'timeout' | 'canceled';

const router = useRouter();
const loading = ref(false);
const error = ref('');
const rows = ref<MqOrderRecord[]>([]);
const statusFilter = ref<StatusFilter>('all');
const keyword = ref('');
const payingOrderNos = ref<string[]>([]);
const cancelingOrderNos = ref<string[]>([]);
const selectedPayRows = ref<MqOrderRecord[]>([]);
const batchPaying = ref(false);
const nowMs = ref(Date.now());

const page = ref({
  current: 1,
  size: 20,
});

let ticker: ReturnType<typeof setInterval> | null = null;
let expiryRefreshTimer: ReturnType<typeof setTimeout> | null = null;

function toTimestamp(raw: string) {
  const value = new Date(String(raw || '').replace(' ', 'T')).getTime();
  return Number.isFinite(value) ? value : 0;
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function deadlineMs(row: MqOrderRecord) {
  const deadline = toTimestamp(row.pay_deadline);
  if (deadline > 0) return deadline;
  return toTimestamp(row.co_created_at) + 2 * 60 * 1000;
}

function countdownText(row: MqOrderRecord) {
  const rest = deadlineMs(row) - nowMs.value;
  if (rest <= 0) return '00:00';
  const sec = Math.floor(rest / 1000);
  const mm = String(Math.floor(sec / 60)).padStart(2, '0');
  const ss = String(sec % 60).padStart(2, '0');
  return `${mm}:${ss}`;
}

function scheduleExpiryRefresh() {
  if (expiryRefreshTimer) {
    clearTimeout(expiryRefreshTimer);
    expiryRefreshTimer = null;
  }

  const now = Date.now();
  let nearestFuture = Number.POSITIVE_INFINITY;
  let hasExpiredUnpaid = false;

  for (const row of rows.value) {
    if (Number(row.status) !== 1) continue;
    const rest = deadlineMs(row) - now;
    if (rest <= 0) {
      hasExpiredUnpaid = true;
      break;
    }
    if (rest < nearestFuture) nearestFuture = rest;
  }

  if (hasExpiredUnpaid) {
    expiryRefreshTimer = setTimeout(() => {
      void loadOrders(true);
    }, 8000);
    return;
  }

  if (Number.isFinite(nearestFuture)) {
    expiryRefreshTimer = setTimeout(() => {
      void loadOrders(true);
    }, Math.max(1000, nearestFuture + 300));
  }
}

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => {
    const status = Number(row.status);
    if (statusFilter.value === 'paid' && status !== 2) return false;
    if (statusFilter.value === 'unpaid' && status !== 1) return false;
    if (statusFilter.value === 'timeout' && status !== 3) return false;
    if (statusFilter.value === 'canceled' && status !== 4) return false;

    if (!kw) return true;
    const stack = `${row.co_order_no} ${row.product_name}`.toLowerCase();
    return stack.includes(kw);
  });
});

const pagedRows = computed(() => {
  const start = (page.value.current - 1) * page.value.size;
  return filteredRows.value.slice(start, start + page.value.size);
});

const selectedPayAmount = computed(() =>
  selectedPayRows.value.reduce((sum, row) => sum + Number(row.amount || 0), 0)
);

watch(
  () => [filteredRows.value.length, page.value.size],
  () => {
    const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / page.value.size));
    if (page.value.current > maxPage) page.value.current = maxPage;
  }
);

watch([statusFilter, keyword], () => {
  page.value.current = 1;
  selectedPayRows.value = [];
});

function isSelectableForPay(row: MqOrderRecord) {
  return Number(row.status) === 1 && Boolean(String(row.co_order_no || '').trim());
}

function onSelectionChange(selection: MqOrderRecord[]) {
  selectedPayRows.value = (selection || []).filter((row) => isSelectableForPay(row));
}

function normalizeCreatedAt(value: unknown) {
  const text = String(value ?? '').trim();
  if (!text) return '';
  return text.includes(' ') ? text.replace(' ', 'T') : text;
}

function buildPayPayload(rowsToPay: MqOrderRecord[]): OrderEventPayload[] {
  const map = new Map<string, OrderEventPayload>();
  for (const row of rowsToPay) {
    if (!isSelectableForPay(row)) continue;
    const orderNo = String(row.co_order_no || '').trim();
    if (!orderNo || map.has(orderNo)) continue;

    map.set(orderNo, {
      orderNo,
      userId: Number(row.user_id || 0) || undefined,
      productId: Number(row.product_id || 0) || undefined,
      cityId: Number(row.city_id || 0) || undefined,
      amount: Number(row.amount || 0),
      remark: String(row.co_remark || ''),
      paymentMethod: String(row.payment_method || 'ALIPAY'),
      createdAt: normalizeCreatedAt(row.co_created_at),
      status: Number(row.status || 1),
    });
  }
  return Array.from(map.values());
}

async function submitPayOrders(rowsToPay: MqOrderRecord[]) {
  const payload = buildPayPayload(rowsToPay);
  if (!payload.length) {
    ElMessage.warning('请先勾选未支付订单');
    return;
  }

  const orderNos = payload.map((item) => String(item.orderNo || '')).filter(Boolean);
  const waitingNos = orderNos.filter((orderNo) => !payingOrderNos.value.includes(orderNo));
  if (!waitingNos.length) return;

  payingOrderNos.value = [...new Set([...payingOrderNos.value, ...waitingNos])];

  try {
    const res = await paySubmit(payload);
    if (res.code !== 200) throw new Error(res.msg || '支付失败');
    ElMessage.success(typeof res.data === 'string' && res.data ? res.data : '支付成功');
    selectedPayRows.value = [];
    await loadOrders(true);
  } catch (err: any) {
    ElMessage.error(err?.message || '订单已超时，请重新创建订单');
  } finally {
    payingOrderNos.value = payingOrderNos.value.filter((item) => !waitingNos.includes(item));
  }
}

async function paySelectedOrders() {
  if (batchPaying.value) return;
  batchPaying.value = true;
  try {
    await submitPayOrders(selectedPayRows.value);
  } finally {
    batchPaying.value = false;
  }
}

async function loadOrders(forceRefresh = false) {
  if (!forceRefresh && loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    const res = await getOrderList();
    if (res.code !== 200) throw new Error(res.msg || '获取订单失败');
    rows.value = [...(res.data || [])].sort((a, b) => {
      const timeDiff = toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at);
      if (timeDiff !== 0) return timeDiff;
      return Number(b.co_id || 0) - Number(a.co_id || 0);
    });
    selectedPayRows.value = [];
    scheduleExpiryRefresh();
  } catch (err: any) {
    rows.value = [];
    selectedPayRows.value = [];
    error.value = err?.message || '获取订单失败';
  } finally {
    loading.value = false;
  }
}

async function payOneOrder(row: MqOrderRecord) {
  await submitPayOrders([row]);
}

async function cancelOneOrder(row: MqOrderRecord) {
  const orderNo = String(row.co_order_no || '').trim();
  if (!orderNo) {
    ElMessage.warning('无效订单号');
    return;
  }
  if (cancelingOrderNos.value.includes(orderNo)) return;

  cancelingOrderNos.value = [...cancelingOrderNos.value, orderNo];
  try {
    const res = await cancelOrder(orderNo);
    if (res.code !== 200) throw new Error(res.msg || '取消订单失败');
    ElMessage.success('订单已取消');
    selectedPayRows.value = selectedPayRows.value.filter((item) => String(item.co_order_no) !== orderNo);
    await loadOrders(true);
  } catch (err: any) {
    ElMessage.error(err?.message || '取消订单失败');
  } finally {
    cancelingOrderNos.value = cancelingOrderNos.value.filter((item) => item !== orderNo);
  }
}

function goBack() {
  void router.push({ name: 'HomeResourceSource' });
}

onMounted(() => {
  ticker = setInterval(() => {
    nowMs.value = Date.now();
  }, 1000);

  void loadOrders();
});

onBeforeUnmount(() => {
  if (ticker) {
    clearInterval(ticker);
    ticker = null;
  }
  if (expiryRefreshTimer) {
    clearTimeout(expiryRefreshTimer);
    expiryRefreshTimer = null;
  }
});
</script>

<style scoped>
.my-orders-page {
  min-height: calc(100dvh - 72px);
  padding: 24px;
  display: flex;
  min-width: 0;
  overflow-x: hidden;
  background:
    radial-gradient(88rem 58rem at -10% -22%, rgba(58, 123, 213, 0.24), rgba(58, 123, 213, 0)),
    radial-gradient(74rem 56rem at 112% 125%, rgba(22, 180, 149, 0.18), rgba(22, 180, 149, 0)),
    linear-gradient(152deg, #f4f9ff 0%, #ddeaff 100%);
}

.panel {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(59, 130, 246, 0.18);
  border-radius: 18px;
  padding: 16px;
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.panel-header h2 {
  margin: 0;
  color: #1f3f67;
}

.panel-header p {
  margin: 8px 0 0;
  color: #5b779a;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.filter-bar {
  margin: 14px 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.search-input {
  width: 300px;
  max-width: 100%;
}

.order-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4d6a91;
}

.state.error {
  color: #d9534f;
}

.empty-wrap {
  padding: 24px 0;
}

.table-wrap {
  flex: 1;
  min-width: 0;
  min-height: 0;
  max-width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  -webkit-overflow-scrolling: touch;
  touch-action: pan-x pan-y;
}

:deep(.my-orders-table) {
  min-width: 1050px;
}

.pager-wrap {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.selection-info {
  color: #4f6e95;
  font-size: 13px;
}

.pager-info {
  color: #4f6e95;
  font-size: 13px;
}

@media (max-width: 900px) {
  .my-orders-page {
    padding: 12px;
    min-height: auto;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .filter-bar {
    flex-direction: column;
    align-items: flex-start;
    width: 100%;
  }

  .filter-bar :deep(.el-radio-group) {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    max-width: 100%;
  }

  .filter-bar :deep(.el-radio-button) {
    flex: 0 0 auto;
  }

  .filter-bar :deep(.el-radio-button__inner) {
    padding: 6px 10px;
    font-size: 12px;
  }

  .search-input {
    width: 100%;
  }

  .pager-wrap {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>
