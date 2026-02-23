<template>
  <section class="cart-items-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>购物车商品</h2>
          <p>支持多选统计总价，结算前需要先选择省市。</p>
        </div>
        <div class="header-actions">
          <el-button @click="loadCartItems(true)">刷新</el-button>
          <el-button type="primary" @click="goBack">返回资源页</el-button>
        </div>
      </header>

      <div v-if="loading" class="state">正在加载购物车列表...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!rows.length" class="empty-wrap">
        <el-empty description="购物车暂无商品" />
      </div>

      <template v-else>
        <div class="table-wrap">
          <el-table
            class="cart-table"
            :data="rows"
            border
            stripe
            row-key="id"
            height="100%"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="54" />
            <el-table-column label="商品名" min-width="180">
              <template #default="{ row }">
                <span>{{ row.product_name || `商品ID ${row.product_id}` }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="brand" label="品牌" min-width="120" />
            <el-table-column prop="category" label="品类" min-width="120" />
            <el-table-column label="价格" min-width="130" align="right">
              <template #default="{ row }">
                <span>CNY {{ formatAmount(row.amount) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="添加时间" min-width="170">
              <template #default="{ row }">
                <span>{{ formatDateTime(row.created_at) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="数量操作" min-width="170" align="center" fixed="right">
              <template #default="{ row }">
                <div class="quantity-stepper">
                  <el-button
                    size="small"
                    :loading="changingProductIds.includes(Number(row.product_id))"
                    @click="handleDecrease(row)"
                  >
                    -
                  </el-button>
                  <span class="qty-text">{{ Number(row.quantity || 1) }}</span>
                  <el-button
                    size="small"
                    type="primary"
                    :loading="changingProductIds.includes(Number(row.product_id))"
                    @click="handleIncrease(row)"
                  >
                    +
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="summary-bar">
          <div class="summary-text">
            已选 {{ selectedTotalCount }} 件，
            总价 <strong>CNY {{ formatAmount(selectedTotalAmount) }}</strong>
          </div>
        </div>

        <div class="checkout-wrap">
          <el-button
            type="primary"
            :disabled="selectedRows.length === 0"
            :loading="submittingOrder"
            @click="checkout"
          >
            结算
          </el-button>
        </div>
      </template>
    </div>

    <el-dialog
      v-model="checkoutDialogVisible"
      title="选择省市"
      width="460px"
      :close-on-click-modal="false"
      :close-on-press-escape="!submittingOrder"
      :show-close="!submittingOrder"
    >
      <div class="location-hint">请为本次结算订单选择省市信息。</div>
      <el-form label-width="70px" class="location-form">
        <el-form-item label="省份">
          <el-select
            v-model="checkoutProvinceId"
            filterable
            placeholder="请选择省份"
            :loading="areaLoading"
            style="width: 100%"
          >
            <el-option v-for="item in provinceOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="城市">
          <el-select
            v-model="checkoutCityId"
            filterable
            placeholder="请选择城市"
            :loading="areaLoading"
            :disabled="!checkoutProvinceId"
            style="width: 100%"
          >
            <el-option
              v-for="item in filteredCityOptions"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submittingOrder" @click="closeCheckoutDialog">取消</el-button>
        <el-button type="primary" :loading="submittingOrder" @click="confirmCheckout">确认结算</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getAreaOptions } from '@/api/modules/area';
import {
  addToCart,
  deleteCartItem,
  getMqItemList,
  getOrderList,
  orderSubmit,
  paySubmit,
  type CartItemRecord,
  type OrderEventPayload,
} from '@/api/modules/mq';

interface ProvinceOption {
  id: number;
  name: string;
}

interface CityOption {
  id: number;
  name: string;
  provinceId: number;
  provinceName: string;
}

const CHECKOUT_PROVINCE_CACHE_KEY = 'cart.checkout.province_id';
const CHECKOUT_CITY_CACHE_KEY = 'cart.checkout.city_id';

const router = useRouter();
const loading = ref(false);
const error = ref('');
const rows = ref<CartItemRecord[]>([]);
const selectedRows = ref<CartItemRecord[]>([]);
const changingProductIds = ref<number[]>([]);
const submittingOrder = ref(false);

const areaLoading = ref(false);
const provinceOptions = ref<ProvinceOption[]>([]);
const cityOptions = ref<CityOption[]>([]);
const checkoutDialogVisible = ref(false);
const checkoutProvinceId = ref<number | null>(null);
const checkoutCityId = ref<number | null>(null);
const pendingCheckoutRows = ref<CartItemRecord[]>([]);

const selectedTotalCount = computed(() =>
  selectedRows.value.reduce((sum, item) => sum + Number(item.quantity || 1), 0)
);

const selectedTotalAmount = computed(() =>
  selectedRows.value.reduce(
    (sum, item) => sum + Number(item.amount || 0) * Math.max(1, Number(item.quantity || 1)),
    0
  )
);

const filteredCityOptions = computed(() => {
  const pid = Number(checkoutProvinceId.value || 0);
  if (!pid) return [];
  return cityOptions.value.filter((item) => item.provinceId === pid);
});

watch(checkoutProvinceId, (next) => {
  const pid = Number(next || 0);
  if (!pid) {
    checkoutCityId.value = null;
    return;
  }

  const cities = cityOptions.value.filter((item) => item.provinceId === pid);
  const current = Number(checkoutCityId.value || 0);
  if (!cities.some((item) => item.id === current)) {
    checkoutCityId.value = cities[0]?.id ?? null;
  }
});

function toTimestamp(raw: string) {
  const value = new Date(String(raw || '').replace(' ', 'T')).getTime();
  return Number.isFinite(value) ? value : 0;
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function formatDateTime(raw: string) {
  if (!raw) return '-';
  const ts = toTimestamp(raw);
  if (!ts) return raw;
  return new Date(ts).toLocaleString('zh-CN', { hour12: false });
}

function formatLocalDateTimeIso(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

function aggregateCartRows(list: CartItemRecord[]) {
  const map = new Map<string, CartItemRecord>();

  for (const item of list) {
    const productId = Number(item.product_id || 0);
    const cityId = Number(item.city_id || 0);
    const key = `${productId}::${cityId}`;
    const qty = Math.max(1, Number(item.quantity || 1));
    const prev = map.get(key);

    if (!prev) {
      map.set(key, {
        ...item,
        quantity: qty,
      });
      continue;
    }

    prev.quantity = Number(prev.quantity || 1) + qty;
    if (!prev.product_name && item.product_name) prev.product_name = item.product_name;
    if (!prev.city_name && item.city_name) prev.city_name = item.city_name;
    if (!prev.brand && item.brand) prev.brand = item.brand;
    if (!prev.category && item.category) prev.category = item.category;
    if (toTimestamp(item.created_at) > toTimestamp(prev.created_at)) {
      prev.created_at = item.created_at;
      prev.updated_at = item.updated_at;
      prev.id = item.id;
    }
  }

  return Array.from(map.values());
}

async function loadCartItems(forceRefresh = false) {
  if (!forceRefresh && loading.value) return;
  loading.value = true;
  error.value = '';
  try {
    const res = await getMqItemList();
    if (res.code !== 200) throw new Error(res.msg || '获取购物车列表失败');
    const merged = aggregateCartRows(res.data || []);
    rows.value = [...merged].sort((a, b) => {
      const timeDiff = toTimestamp(b.created_at) - toTimestamp(a.created_at);
      if (timeDiff !== 0) return timeDiff;
      return Number(b.id || 0) - Number(a.id || 0);
    });
    selectedRows.value = [];
  } catch (err: any) {
    rows.value = [];
    selectedRows.value = [];
    error.value = err?.message || '获取购物车列表失败';
  } finally {
    loading.value = false;
  }
}

function onSelectionChange(selection: CartItemRecord[]) {
  selectedRows.value = selection || [];
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
      productId: Number(item?.productId ?? item?.product_id ?? item?.pr_id),
      cityId: Number(item?.cityId ?? item?.city_id ?? item?.c_id),
      amount: Number(item?.amount ?? 0),
      quantity: Math.max(1, Number(item?.quantity ?? 1)),
      remark: String(item?.remark ?? item?.co_remark ?? '').trim(),
      paymentMethod:
        String(item?.paymentMethod ?? item?.payment_method ?? 'ALIPAY').trim() || 'ALIPAY',
      createdAt: String(item?.createdAt ?? item?.co_created_at ?? '').trim(),
      status: Number(item?.status ?? item?.co_status ?? item?.order_status ?? 1),
    }))
    .filter((item) => Boolean(item.orderNo));
}

async function resolvePayOrders(
  submitData: unknown,
  selected: CartItemRecord[],
  createdAfterMs: number
): Promise<OrderEventPayload[]> {
  const fromSubmit = normalizeOrderEvents(submitData);
  if (fromSubmit.length) return fromSubmit;

  const selectedProductSet = new Set(selected.map((item) => Number(item.product_id || 0)));
  const listRes = await getOrderList();
  if (listRes.code !== 200) return [];

  return (listRes.data || [])
    .filter((item) => {
      const pid = Number(item.product_id || 0);
      const createdAt = toTimestamp(item.co_created_at);
      return (
        selectedProductSet.has(pid) && Number(item.status || 0) === 1 && createdAt >= createdAfterMs - 15000
      );
    })
    .sort((a, b) => toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at))
    .map((item) => ({
      orderNo: item.co_order_no,
      productId: item.product_id,
      cityId: item.city_id,
      amount: item.amount,
      createdAt: item.co_created_at,
      remark: item.co_remark,
      paymentMethod: 'ALIPAY',
      status: item.status,
    }));
}

async function ensureAreaOptionsLoaded(force = false) {
  if (areaLoading.value) return;
  if (!force && provinceOptions.value.length && cityOptions.value.length) return;

  areaLoading.value = true;
  try {
    const res = await getAreaOptions();
    if (res.code !== 200) {
      throw new Error(res.msg || '获取省市数据失败');
    }

    const provinceList = Array.isArray(res.data?.provinces) ? res.data.provinces : [];
    const cityList = Array.isArray(res.data?.cities) ? res.data.cities : [];
    const provinceMap = new Map<number, string>();

    provinceOptions.value = provinceList
      .map((item: any) => ({
        id: Number(item?.id || 0),
        name: String(item?.name || '').trim(),
      }))
      .filter((item) => item.id > 0 && Boolean(item.name))
      .sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'));

    for (const item of provinceOptions.value) {
      provinceMap.set(item.id, item.name);
    }

    cityOptions.value = cityList
      .map((item: any) => {
        const provinceId = Number(item?.provinceId ?? item?.province_id ?? 0);
        return {
          id: Number(item?.id || 0),
          name: String(item?.name || '').trim(),
          provinceId,
          provinceName: provinceMap.get(provinceId) || '',
        };
      })
      .filter((item) => item.id > 0 && item.provinceId > 0 && Boolean(item.name))
      .sort((a, b) => {
      if (a.provinceId !== b.provinceId) return a.provinceId - b.provinceId;
      return a.name.localeCompare(b.name, 'zh-CN');
    });

    if (!provinceOptions.value.length || !cityOptions.value.length) {
      throw new Error('未获取到可选省市数据');
    }
  } finally {
    areaLoading.value = false;
  }
}

function pickDefaultArea(rowsForCheckout: CartItemRecord[]) {
  const provinceMap = new Map(provinceOptions.value.map((item) => [item.id, item]));
  const cityMap = new Map(cityOptions.value.map((item) => [item.id, item]));

  const cachedProvinceId = Number(localStorage.getItem(CHECKOUT_PROVINCE_CACHE_KEY) || 0);
  const cachedCityId = Number(localStorage.getItem(CHECKOUT_CITY_CACHE_KEY) || 0);

  let provinceId: number | null = null;
  let cityId: number | null = null;

  if (cachedCityId > 0 && cityMap.has(cachedCityId)) {
    const city = cityMap.get(cachedCityId)!;
    cityId = city.id;
    provinceId = city.provinceId;
  }

  if (!provinceId && cachedProvinceId > 0 && provinceMap.has(cachedProvinceId)) {
    provinceId = cachedProvinceId;
  }

  if (!provinceId || !cityId) {
    const firstRowCityId = Number(rowsForCheckout[0]?.city_id || 0);
    if (firstRowCityId > 0 && cityMap.has(firstRowCityId)) {
      const city = cityMap.get(firstRowCityId)!;
      cityId = city.id;
      provinceId = city.provinceId;
    }
  }

  if (!provinceId) {
    provinceId = provinceOptions.value[0]?.id ?? null;
  }

  if (!cityId) {
    const firstCity = cityOptions.value.find((item) => item.provinceId === provinceId);
    cityId = firstCity?.id ?? null;
  }

  checkoutProvinceId.value = provinceId;
  checkoutCityId.value = cityId;
}

async function handleDecrease(row: CartItemRecord) {
  const productId = Number(row.product_id);
  const cityId = Number(row.city_id || 0);
  if (!Number.isFinite(productId) || productId <= 0) {
    ElMessage.warning('无效商品，无法减少数量');
    return;
  }

  if (changingProductIds.value.includes(productId)) return;
  changingProductIds.value = [...changingProductIds.value, productId];

  try {
    const res = await deleteCartItem({ productId, cityId: cityId > 0 ? cityId : undefined });
    if (res.code !== 200) throw new Error(res.msg || '减少商品数量失败');
    ElMessage.success(res.data || '减少商品数量成功');
    await loadCartItems(true);
  } catch (err: any) {
    ElMessage.error(err?.message || '减少商品数量失败');
  } finally {
    changingProductIds.value = changingProductIds.value.filter((id) => id !== productId);
  }
}

async function handleIncrease(row: CartItemRecord) {
  const productId = Number(row.product_id);
  const cityId = Number(row.city_id);
  if (!Number.isFinite(productId) || productId <= 0) {
    ElMessage.warning('无效商品，无法增加数量');
    return;
  }
  if (!Number.isFinite(cityId) || cityId <= 0) {
    ElMessage.warning('缺少城市信息，无法增加数量');
    return;
  }

  if (changingProductIds.value.includes(productId)) return;
  changingProductIds.value = [...changingProductIds.value, productId];

  try {
    const res = await addToCart({ pid: productId, cityId });
    if (res.code !== 200) throw new Error(res.msg || '增加商品数量失败');
    ElMessage.success(res.data || '增加商品数量成功');
    await loadCartItems(true);
  } catch (err: any) {
    ElMessage.error(err?.message || '增加商品数量失败');
  } finally {
    changingProductIds.value = changingProductIds.value.filter((id) => id !== productId);
  }
}

function closeCheckoutDialog() {
  if (submittingOrder.value) return;
  checkoutDialogVisible.value = false;
  pendingCheckoutRows.value = [];
}

function checkout() {
  if (!selectedRows.value.length) {
    ElMessage.warning('请先选择需要结算的商品');
    return;
  }
  if (submittingOrder.value) return;

  void (async () => {
    try {
      await ensureAreaOptionsLoaded();
      pendingCheckoutRows.value = [...selectedRows.value];
      pickDefaultArea(pendingCheckoutRows.value);
      checkoutDialogVisible.value = true;
    } catch (err: any) {
      ElMessage.error(err?.message || '获取省市数据失败，请稍后重试');
    }
  })();
}

async function submitCheckout(rowsForCheckout: CartItemRecord[], cityId: number) {
  if (!rowsForCheckout.length) {
    ElMessage.warning('请先选择需要结算的商品');
    return;
  }

  submittingOrder.value = true;
  const createdAt = new Date();
  const createdAtText = formatLocalDateTimeIso(createdAt);
  const submitAtMs = createdAt.getTime();

  try {
    const submitPayload: OrderEventPayload[] = rowsForCheckout.map((item) => ({
      productId: Number(item.product_id || 0),
      cityId,
      quantity: Math.max(1, Number(item.quantity || 1)),
      amount: Number(item.amount || 0) * Math.max(1, Number(item.quantity || 1)),
      remark: `购物车结算 x${Math.max(1, Number(item.quantity || 1))}`,
      paymentMethod: 'ALIPAY',
      createdAt: createdAtText,
    }));

    const submitRes = await orderSubmit(submitPayload);
    if (submitRes.code !== 200) throw new Error(submitRes.msg || '创建订单失败');

    ElMessage.success(typeof submitRes.data === 'string' && submitRes.data ? submitRes.data : '订单创建成功');

    let shouldPay = false;
    try {
      await ElMessageBox.confirm('订单已创建，是否立即支付？', '确认支付', {
        confirmButtonText: '确认支付',
        cancelButtonText: '稍后支付',
        type: 'warning',
      });
      shouldPay = true;
    } catch {
      shouldPay = false;
    }

    if (!shouldPay) {
      await loadCartItems(true);
      return;
    }

    const payOrders = await resolvePayOrders(submitRes.data, rowsForCheckout, submitAtMs);
    if (!payOrders.length) {
      ElMessage.warning('未获取到可支付订单，请到我的订单页面支付');
      await loadCartItems(true);
      return;
    }

    const payRes = await paySubmit(payOrders);
    if (payRes.code !== 200) throw new Error(payRes.msg || '支付失败');

    ElMessage.success(typeof payRes.data === 'string' && payRes.data ? payRes.data : '支付成功');
    await loadCartItems(true);
  } catch (err: any) {
    ElMessage.error(err?.message || '结算失败');
  } finally {
    submittingOrder.value = false;
    pendingCheckoutRows.value = [];
  }
}

function confirmCheckout() {
  const provinceId = Number(checkoutProvinceId.value || 0);
  const cityId = Number(checkoutCityId.value || 0);

  if (!provinceId) {
    ElMessage.warning('请选择省份');
    return;
  }
  if (!cityId) {
    ElMessage.warning('请选择城市');
    return;
  }

  localStorage.setItem(CHECKOUT_PROVINCE_CACHE_KEY, String(provinceId));
  localStorage.setItem(CHECKOUT_CITY_CACHE_KEY, String(cityId));

  const rowsForCheckout = [...pendingCheckoutRows.value];
  checkoutDialogVisible.value = false;
  void submitCheckout(rowsForCheckout, cityId);
}

function goBack() {
  void router.push({ name: 'HomeResourceSource' });
}

onMounted(() => {
  void loadCartItems();
});
</script>

<style scoped>
.cart-items-page {
  min-height: calc(100dvh - 72px);
  padding: 24px;
  display: flex;
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
  min-height: 0;
  display: flex;
  flex-direction: column;
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
  color: #1e3f67;
}

.panel-header p {
  margin: 6px 0 0;
  color: #5a7698;
  font-size: 13px;
}

.header-actions {
  display: flex;
  gap: 10px;
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
  min-height: 0;
}

.summary-bar {
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid rgba(59, 130, 246, 0.2);
  background: rgba(255, 255, 255, 0.8);
}

.summary-text {
  color: #365e89;
  font-size: 14px;
}

.summary-text strong {
  color: #1f5b94;
  font-size: 16px;
}

.checkout-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.quantity-stepper {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.qty-text {
  min-width: 28px;
  text-align: center;
  color: #1f5b94;
  font-weight: 700;
}

.location-hint {
  margin-bottom: 8px;
  color: #5b779a;
  font-size: 13px;
}

.location-form {
  margin-top: 6px;
}

@media (max-width: 900px) {
  .cart-items-page {
    padding: 12px;
    height: calc(100dvh - 124px - env(safe-area-inset-bottom));
    min-height: 420px;
    overflow: hidden;
  }

  .panel {
    height: 100%;
    padding: 12px;
    border-radius: 14px;
    overflow: hidden;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-actions :deep(.el-button) {
    flex: 1 1 calc(50% - 6px);
    min-width: 120px;
  }

  .table-wrap {
    flex: 1 1 auto;
    min-height: 220px;
    overflow-x: auto;
    overflow-y: auto;
    border-radius: 12px;
    -webkit-overflow-scrolling: touch;
    overscroll-behavior: contain;
  }

  :deep(.cart-table) {
    min-width: 780px;
  }

  :deep(.cart-table .el-table__cell) {
    padding: 7px 6px;
    font-size: 12px;
  }

  .quantity-stepper {
    gap: 6px;
  }

  .summary-bar {
    position: static;
    margin-top: 10px;
  }

  .checkout-wrap {
    position: static;
    margin-top: 10px;
    padding-top: 0;
    background: none;
  }

  .checkout-wrap :deep(.el-button) {
    width: 100%;
    height: 38px;
    border-radius: 11px;
  }
}

@media (max-width: 560px) {
  .cart-items-page {
    padding: 10px;
    height: calc(100dvh - 120px - env(safe-area-inset-bottom));
    min-height: 400px;
  }

  .header-actions :deep(.el-button) {
    flex-basis: 100%;
  }

  :deep(.cart-table) {
    min-width: 560px;
  }

  :deep(.cart-table .el-table__header th:nth-child(3)),
  :deep(.cart-table .el-table__body td:nth-child(3)),
  :deep(.cart-table .el-table__header th:nth-child(4)),
  :deep(.cart-table .el-table__body td:nth-child(4)),
  :deep(.cart-table .el-table__header th:nth-child(6)),
  :deep(.cart-table .el-table__body td:nth-child(6)) {
    display: none;
  }

  .summary-text {
    font-size: 13px;
  }

  .summary-text strong {
    font-size: 15px;
  }
}
</style>
