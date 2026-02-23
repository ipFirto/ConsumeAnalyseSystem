<template>
  <section class="mock-data-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>模拟数据</h2>
          <p>用于快速创建测试订单并验证支付链路（创建订单 / 支付 / 状态刷新）。</p>
        </div>
        <div class="header-actions">
          <el-button :loading="loadingOrders" @click="refreshAll">刷新</el-button>
        </div>
      </header>

      <div class="form-grid">
        <el-form label-position="top">
          <el-form-item label="平台">
            <el-select
              v-model="selectedPlatformId"
              style="width: 100%"
              filterable
              placeholder="选择平台"
              @change="onPlatformChange"
            >
              <el-option v-for="item in platformOptions" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>

          <el-form-item label="商品">
            <el-select
              v-model="form.productId"
              style="width: 100%"
              filterable
              placeholder="选择商品"
              :loading="loadingProducts"
            >
              <el-option
                v-for="item in productOptions"
                :key="item.id"
                :label="`${item.product_name} (CNY ${formatAmount(item.amount)})`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="城市">
            <el-select
              v-model="form.cityId"
              style="width: 100%"
              filterable
              placeholder="选择城市"
              :loading="loadingArea"
            >
              <el-option
                v-for="item in cityOptions"
                :key="item.id"
                :label="`${item.provinceName} / ${item.name}`"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-form>

        <el-form label-position="top">
          <el-form-item label="数量">
            <el-input-number v-model="form.quantity" :min="1" :step="1" :precision="0" style="width: 100%" />
          </el-form-item>

          <el-form-item label="支付方式">
            <el-select v-model="form.paymentMethod" style="width: 100%">
              <el-option label="ALIPAY" value="ALIPAY" />
              <el-option label="WECHAT" value="WECHAT" />
              <el-option label="MOCK" value="MOCK" />
            </el-select>
          </el-form-item>

          <el-form-item label="备注">
            <el-input v-model.trim="form.remark" maxlength="60" placeholder="可选备注" />
          </el-form-item>
        </el-form>

        <el-form label-position="top">
          <el-form-item label="自动支付">
            <el-switch v-model="autoPay" />
          </el-form-item>

          <el-form-item label="预计金额">
            <div class="summary-amount">CNY {{ formatAmount(predictedAmount) }}</div>
          </el-form-item>

          <el-form-item label="快捷操作">
            <div class="quick-actions">
              <el-button type="primary" :loading="creating" @click="createOneMockOrder">生成1条</el-button>
              <el-button :loading="creatingBatch" @click="createBatchMockOrders">生成5条</el-button>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <div class="table-tools">
        <span>未支付订单 {{ unpaidRows.length }} 条</span>
        <el-button type="success" :disabled="!selectedRows.length" :loading="paying" @click="paySelected">
          批量支付 {{ selectedRows.length ? `(${selectedRows.length})` : '' }}
        </el-button>
      </div>

      <div class="table-wrap">
        <el-table
          :data="rows"
          border
          stripe
          height="100%"
          row-key="co_id"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="54" :selectable="selectableForPay" />
          <el-table-column prop="co_order_no" label="订单号" min-width="200" />
          <el-table-column prop="product_name" label="商品" min-width="160" />
          <el-table-column prop="city_name" label="城市" min-width="120" />
          <el-table-column label="金额" min-width="120" align="right">
            <template #default="{ row }">CNY {{ formatAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="payment_method" label="支付方式" min-width="120" />
          <el-table-column prop="co_created_at" label="创建时间" min-width="170" />
          <el-table-column label="状态" min-width="120" align="center">
            <template #default="{ row }">
              <el-tag :class="statusClass(row.status)">{{ statusText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="110" align="center" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="Number(row.status) === 1"
                type="primary"
                link
                :loading="payingOrderNos.includes(row.co_order_no)"
                @click="payOne(row)"
              >
                支付
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getAreaOptions } from '@/api/modules/area';
import { getProductList, getProductPlatforms, type ProductRecord } from '@/api/modules/product';
import {
  getRecentOrderList,
  orderSubmit,
  paySubmit,
  type MqOrderRecord,
  type OrderEventPayload,
} from '@/api/modules/mq';

interface PlatformOption {
  id: number;
  name: string;
}

interface CityOption {
  id: number;
  name: string;
  provinceName: string;
}

const platformOptions = ref<PlatformOption[]>([]);
const selectedPlatformId = ref<number | null>(null);
const productOptions = ref<ProductRecord[]>([]);
const cityOptions = ref<CityOption[]>([]);

const loadingProducts = ref(false);
const loadingArea = ref(false);
const loadingOrders = ref(false);
const creating = ref(false);
const creatingBatch = ref(false);
const paying = ref(false);
const payingOrderNos = ref<string[]>([]);

const autoPay = ref(true);
const rows = ref<MqOrderRecord[]>([]);
const selectedRows = ref<MqOrderRecord[]>([]);

const form = reactive({
  productId: 0,
  cityId: 0,
  quantity: 1,
  paymentMethod: 'ALIPAY',
  remark: '模拟订单',
});

const selectedProduct = computed(() =>
  productOptions.value.find((item) => Number(item.id) === Number(form.productId)) || null
);

const predictedAmount = computed(() =>
  Number(selectedProduct.value?.amount || 0) * Math.max(1, Number(form.quantity || 1))
);

const unpaidRows = computed(() => rows.value.filter((item) => Number(item.status) === 1));

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function statusText(status: number) {
  if (Number(status) === 2) return '已支付';
  if (Number(status) === 3) return '已超时';
  return '未支付';
}

function statusClass(status: number) {
  if (Number(status) === 2) return 'paid';
  if (Number(status) === 3) return 'timeout';
  return 'unpaid';
}

function selectableForPay(row: MqOrderRecord) {
  return Number(row.status) === 1 && Boolean(String(row.co_order_no || '').trim());
}

function onSelectionChange(selection: MqOrderRecord[]) {
  selectedRows.value = (selection || []).filter((item) => selectableForPay(item));
}

function toTimestamp(raw: string) {
  const value = new Date(String(raw || '').replace(' ', 'T')).getTime();
  return Number.isFinite(value) ? value : 0;
}

function normalizeOrderEvents(raw: unknown): OrderEventPayload[] {
  const list = Array.isArray(raw)
    ? raw
    : raw && typeof raw === 'object'
      ? ((raw as any).orders || (raw as any).list || (raw as any).records || (raw as any).rows || [])
      : [];

  if (!Array.isArray(list)) return [];
  return list
    .map((item: any) => ({
      orderNo: String(item?.orderNo ?? item?.co_order_no ?? item?.coOrderNo ?? '').trim(),
      userId: Number(item?.userId ?? item?.user_id ?? 0) || undefined,
      productId: Number(item?.productId ?? item?.product_id ?? 0) || undefined,
      cityId: Number(item?.cityId ?? item?.city_id ?? 0) || undefined,
      quantity: Math.max(1, Number(item?.quantity ?? 1)),
      amount: Number(item?.amount ?? 0),
      remark: String(item?.remark ?? item?.co_remark ?? '').trim(),
      paymentMethod: String(item?.paymentMethod ?? item?.payment_method ?? 'ALIPAY').trim() || 'ALIPAY',
      createdAt: String(item?.createdAt ?? item?.co_created_at ?? '').trim(),
      status: Number(item?.status ?? item?.co_status ?? 1),
    }))
    .filter((item) => Boolean(item.orderNo));
}

function buildPayPayload(sourceRows: MqOrderRecord[]): OrderEventPayload[] {
  const map = new Map<string, OrderEventPayload>();
  sourceRows.forEach((row) => {
    if (!selectableForPay(row)) return;
    const orderNo = String(row.co_order_no || '').trim();
    if (!orderNo || map.has(orderNo)) return;

    map.set(orderNo, {
      orderNo,
      userId: Number(row.user_id || 0) || undefined,
      productId: Number(row.product_id || 0) || undefined,
      cityId: Number(row.city_id || 0) || undefined,
      amount: Number(row.amount || 0),
      quantity: Math.max(1, Number(row.quantity || 1)),
      remark: String(row.co_remark || ''),
      paymentMethod: String(row.payment_method || form.paymentMethod || 'ALIPAY'),
      createdAt: String(row.co_created_at || '').replace(' ', 'T'),
      status: Number(row.status || 1),
    });
  });
  return Array.from(map.values());
}

async function loadPlatforms() {
  const res = await getProductPlatforms();
  if (res.code !== 200 || !res.data.length) throw new Error(res.msg || '获取平台列表失败');

  platformOptions.value = res.data.map((item) => ({
    id: Number(item.id),
    name: String(item.name || '').trim(),
  }));
  selectedPlatformId.value = selectedPlatformId.value || platformOptions.value[0]?.id || null;
}

async function loadProducts() {
  const pid = Number(selectedPlatformId.value || 0);
  if (!pid) {
    productOptions.value = [];
    form.productId = 0;
    return;
  }
  loadingProducts.value = true;
  try {
    const res = await getProductList({
      platformId: pid,
      status: 1,
      limit: 200,
      offset: 0,
    });
    if (res.code !== 200) throw new Error(res.msg || '获取商品列表失败');
    productOptions.value = res.data || [];
    if (!productOptions.value.some((item) => Number(item.id) === Number(form.productId))) {
      form.productId = Number(productOptions.value[0]?.id || 0);
    }
  } finally {
    loadingProducts.value = false;
  }
}

async function loadAreas() {
  loadingArea.value = true;
  try {
    const res = await getAreaOptions();
    if (res.code !== 200) throw new Error(res.msg || '获取城市列表失败');

    const provinceNameMap = new Map<number, string>();
    (res.data?.provinces || []).forEach((item: any) => {
      provinceNameMap.set(Number(item?.id || 0), String(item?.name || '').trim());
    });

    cityOptions.value = (res.data?.cities || [])
      .map((item: any) => ({
        id: Number(item?.id || 0),
        name: String(item?.name || '').trim(),
        provinceName: provinceNameMap.get(Number(item?.provinceId ?? item?.province_id ?? 0)) || '-',
      }))
      .filter((item: CityOption) => item.id > 0 && Boolean(item.name));

    if (!cityOptions.value.some((item) => Number(item.id) === Number(form.cityId))) {
      form.cityId = Number(cityOptions.value[0]?.id || 0);
    }
  } finally {
    loadingArea.value = false;
  }
}

async function loadOrders(forceRefresh = false) {
  if (!forceRefresh && loadingOrders.value) return;
  loadingOrders.value = true;
  try {
    const res = await getRecentOrderList({ limit: 30 });
    if (res.code !== 200) throw new Error(res.msg || '获取订单失败');
    rows.value = [...(res.data || [])].sort((a, b) => {
      const timeDiff = toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at);
      if (timeDiff !== 0) return timeDiff;
      return Number(b.co_id || 0) - Number(a.co_id || 0);
    });
    selectedRows.value = [];
  } finally {
    loadingOrders.value = false;
  }
}

async function payRows(rowsToPay: MqOrderRecord[]) {
  const payload = buildPayPayload(rowsToPay);
  if (!payload.length) {
    ElMessage.warning('请选择未支付订单');
    return;
  }

  const waitingNos = payload.map((item) => String(item.orderNo || '')).filter(Boolean);
  payingOrderNos.value = [...new Set([...payingOrderNos.value, ...waitingNos])];

  try {
    const payRes = await paySubmit(payload);
    if (payRes.code !== 200) throw new Error(payRes.msg || '支付失败');
    ElMessage.success(typeof payRes.data === 'string' && payRes.data ? payRes.data : '支付成功');
    await loadOrders(true);
  } finally {
    payingOrderNos.value = payingOrderNos.value.filter((item) => !waitingNos.includes(item));
  }
}

async function createOrders(orderEvents: OrderEventPayload[]) {
  const submitRes = await orderSubmit(orderEvents);
  if (submitRes.code !== 200) throw new Error(submitRes.msg || '创建订单失败');
  ElMessage.success(typeof submitRes.data === 'string' && submitRes.data ? submitRes.data : '模拟订单创建成功');

  if (!autoPay.value) {
    await loadOrders(true);
    return;
  }

  const payEvents = normalizeOrderEvents(submitRes.data);
  if (payEvents.length) {
    const payRes = await paySubmit(payEvents);
    if (payRes.code !== 200) throw new Error(payRes.msg || '自动支付失败');
    ElMessage.success('自动支付完成');
  } else {
    ElMessage.warning('未识别到可支付订单，请手动支付');
  }
  await loadOrders(true);
}

async function createOneMockOrder() {
  const productId = Number(form.productId || 0);
  const cityId = Number(form.cityId || 0);
  if (!productId || !cityId) {
    ElMessage.warning('请先选择商品和城市');
    return;
  }

  const amount = predictedAmount.value;
  if (!amount) {
    ElMessage.warning('金额无效，请检查商品数据');
    return;
  }

  creating.value = true;
  try {
    await createOrders([
      {
        productId,
        cityId,
        quantity: Math.max(1, Number(form.quantity || 1)),
        amount,
        remark: form.remark || `模拟订单 x${Math.max(1, Number(form.quantity || 1))}`,
        paymentMethod: form.paymentMethod,
        createdAt: new Date().toISOString(),
      },
    ]);
  } catch (err: any) {
    ElMessage.error(err?.message || '模拟下单失败');
  } finally {
    creating.value = false;
  }
}

function randomPick<T>(list: T[]) {
  if (!list.length) return null;
  const idx = Math.floor(Math.random() * list.length);
  return list[idx] || null;
}

async function createBatchMockOrders() {
  if (!productOptions.value.length || !cityOptions.value.length) {
    ElMessage.warning('缺少商品或城市数据，无法批量模拟');
    return;
  }

  creatingBatch.value = true;
  try {
    const batch: OrderEventPayload[] = Array.from({ length: 5 }, (_, index) => {
      const product = randomPick(productOptions.value) ?? productOptions.value[0];
      const city = randomPick(cityOptions.value) ?? cityOptions.value[0];
      if (!product || !city) {
        return {
          productId: undefined,
          cityId: undefined,
          amount: 0,
          remark: `批量模拟订单-${index + 1}`,
          paymentMethod: form.paymentMethod,
          createdAt: new Date().toISOString(),
        };
      }
      const quantity = Math.floor(Math.random() * 3) + 1;
      return {
        productId: Number(product.id),
        cityId: Number(city.id),
        quantity,
        amount: Number(product.amount || 0) * quantity,
        remark: `批量模拟订单-${index + 1}`,
        paymentMethod: form.paymentMethod,
        createdAt: new Date().toISOString(),
      };
    }).filter((item) => Number(item.productId || 0) > 0 && Number(item.cityId || 0) > 0);

    if (!batch.length) {
      ElMessage.warning('无法生成模拟订单，请先检查商品和城市数据');
      return;
    }

    await createOrders(batch);
  } catch (err: any) {
    ElMessage.error(err?.message || '批量模拟失败');
  } finally {
    creatingBatch.value = false;
  }
}

async function payOne(row: MqOrderRecord) {
  if (paying.value) return;
  paying.value = true;
  try {
    await payRows([row]);
  } catch (err: any) {
    ElMessage.error(err?.message || '支付失败');
  } finally {
    paying.value = false;
  }
}

async function paySelected() {
  if (!selectedRows.value.length || paying.value) return;
  paying.value = true;
  try {
    await payRows(selectedRows.value);
  } catch (err: any) {
    ElMessage.error(err?.message || '支付失败');
  } finally {
    paying.value = false;
  }
}

async function onPlatformChange() {
  try {
    await loadProducts();
  } catch (err: any) {
    ElMessage.error(err?.message || '加载商品失败');
  }
}

async function refreshAll() {
  try {
    await Promise.all([loadProducts(), loadAreas(), loadOrders(true)]);
  } catch (err: any) {
    ElMessage.error(err?.message || '刷新失败');
  }
}

onMounted(async () => {
  try {
    await loadPlatforms();
    await refreshAll();
  } catch (err: any) {
    ElMessage.error(err?.message || '初始化失败');
  }
});
</script>

<style scoped>
.mock-data-page {
  min-height: calc(100dvh - 72px);
  display: flex;
}

.panel {
  border-radius: 18px;
  border: 1px solid rgba(59, 130, 246, 0.18);
  background: rgba(255, 255, 255, 0.88);
  padding: 16px;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-header h2 {
  margin: 0;
  color: #1f3f67;
}

.panel-header p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #58749a;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 12px;
}

.summary-amount {
  height: 32px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: 8px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  color: #1f5b94;
  font-weight: 700;
  background: rgba(255, 255, 255, 0.9);
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.table-tools {
  margin: 4px 0 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  color: #4f6e95;
  font-size: 13px;
}

.table-wrap {
  flex: 1;
  min-height: 0;
}

:deep(.el-tag.unpaid) {
  color: #b5761d;
  background: rgba(255, 243, 218, 0.9);
  border-color: rgba(236, 193, 121, 0.8);
}

:deep(.el-tag.paid) {
  color: #1f8a4a;
  background: rgba(231, 249, 238, 0.9);
  border-color: rgba(123, 211, 156, 0.86);
}

:deep(.el-tag.timeout) {
  color: #c64b4b;
  background: rgba(255, 238, 238, 0.92);
  border-color: rgba(235, 151, 151, 0.82);
}

@media (max-width: 1100px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .mock-data-page {
    min-height: auto;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-tools {
    flex-direction: column;
    align-items: flex-start;
    gap: 8px;
  }
}
</style>






