<template>
  <section class="resources-page">
    <h3 class="section-title">项目资源总览</h3>

    <div class="resource-columns">
      <article class="resource-card">
        <header class="card-header">
          <h4>商品列表</h4>
          <el-button link type="primary" @click="goToMoreProducts">查看更多</el-button>
        </header>

        <div class="card-body">
          <div v-if="loading" class="state">正在加载商品排行榜...</div>
          <div v-else-if="error" class="state error">{{ error }}</div>

          <ul v-else-if="topProducts.length" class="rank-list">
            <li v-for="(item, index) in topProducts" :key="item.key" class="rank-item">
              <span class="rank-index" :class="{ top: index < 3 }">{{ index + 1 }}</span>

              <div class="rank-main">
                <p class="product-name" :title="item.productName">{{ item.productName }}</p>
                <p class="product-meta">{{ item.platformName }} · {{ item.category }}</p>
              </div>

              <div class="rank-sales">
                <span>销量</span>
                <strong>{{ item.salesCount }}</strong>
              </div>
            </li>
          </ul>

          <el-empty v-else description="暂无商品数据" :image-size="90" />
        </div>
      </article>

      <article class="resource-card placeholder-card">
        <header class="card-header">
          <h4>购物车</h4>
          <el-button link type="primary" @click="goToCartItems">查看商品</el-button>
        </header>

        <div class="card-body">
          <div v-if="cartLoading" class="state">正在加载购物车...</div>
          <div v-else-if="cartError" class="state error">{{ cartError }}</div>

          <ul v-else-if="cartPreviewItems.length" class="cart-list">
            <li v-for="item in cartPreviewItems" :key="item.id" class="cart-item">
              <div class="cart-main">
                <p class="cart-name" :title="item.productName">{{ item.productName }}</p>
                <p class="cart-meta">
                  {{ item.cityName }}
                  <span v-if="item.category"> · {{ item.category }}</span>
                </p>
              </div>
              <div class="cart-amount">
                <span>价格</span>
                <strong>CNY {{ formatAmount(item.amount) }}</strong>
              </div>
            </li>
          </ul>

          <el-empty v-else description="购物车暂无商品" :image-size="90" />
        </div>
      </article>

      <article class="resource-card placeholder-card">
        <header class="card-header">
          <h4>我的订单</h4>
          <el-button link type="primary" @click="goToMyOrders">查看详情</el-button>
        </header>

        <div class="card-body">
          <div v-if="orderLoading" class="state">正在加载订单...</div>
          <div v-else-if="orderError" class="state error">{{ orderError }}</div>

          <ul v-else-if="orderPreviewItems.length" class="order-list">
            <li v-for="item in orderPreviewItems" :key="item.co_id || item.co_order_no" class="order-item">
              <div class="order-main">
                <p class="order-name" :title="item.product_name || item.co_order_no">
                  {{ item.product_name || '未知商品' }}
                </p>
                <p class="order-meta">
                  {{ item.co_order_no || '-' }}
                </p>
                <p class="order-time">{{ item.co_created_at || '-' }}</p>
              </div>
              <div class="order-side">
                <span class="order-price">CNY {{ formatAmount(item.amount) }}</span>
                <el-tag size="small" :class="getOrderStatusClass(item.status)">
                  {{ getOrderStatusText(item.status) }}
                </el-tag>
              </div>
            </li>
          </ul>

          <el-empty v-else description="暂无订单数据" :image-size="90" />
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { getResourceProductDataset, type ResourceProductItem } from '@/api/modules/resourceProducts';
import { getMqItemList, getOrderList, type CartItemRecord } from '@/api/modules/mq';

const router = useRouter();
const loading = ref(false);
const error = ref('');
const products = ref<ResourceProductItem[]>([]);
const cartLoading = ref(false);
const cartError = ref('');
const cartItems = ref<CartItemRecord[]>([]);
const orderLoading = ref(false);
const orderError = ref('');
const orderItems = ref<
  Array<{
    co_id: number;
    co_order_no: string;
    product_name: string;
    amount: number;
    co_created_at: string;
    status: number;
  }>
>([]);

const topProducts = computed(() => products.value.slice(0, 8));
const cartPreviewItems = computed(() =>
  cartItems.value.slice(0, 8).map((item) => ({
    ...item,
    productName: item.product_name || `商品ID ${item.product_id}`,
    cityName: item.city_name || `城市ID ${item.city_id}`,
  }))
);

async function loadProductRanking() {
  loading.value = true;
  error.value = '';
  try {
    const dataset = await getResourceProductDataset();
    products.value = dataset.products;
  } catch (err: any) {
    products.value = [];
    error.value = err?.message || '获取商品排行榜失败';
  } finally {
    loading.value = false;
  }
}

function toTimestamp(raw: string) {
  const value = new Date(String(raw || '').replace(' ', 'T')).getTime();
  return Number.isFinite(value) ? value : 0;
}

async function loadCartItems(forceRefresh = false) {
  if (!forceRefresh && cartLoading.value) return;
  cartLoading.value = true;
  cartError.value = '';
  try {
    const res = await getMqItemList();
    if (res.code !== 200) throw new Error(res.msg || '获取购物车失败');
    cartItems.value = [...(res.data || [])].sort((a, b) => {
      const timeDiff = toTimestamp(b.created_at) - toTimestamp(a.created_at);
      if (timeDiff !== 0) return timeDiff;
      return Number(b.id || 0) - Number(a.id || 0);
    });
  } catch (err: any) {
    cartItems.value = [];
    cartError.value = err?.message || '获取购物车失败';
  } finally {
    cartLoading.value = false;
  }
}

async function loadOrders(forceRefresh = false) {
  if (!forceRefresh && orderLoading.value) return;
  orderLoading.value = true;
  orderError.value = '';
  try {
    const res = await getOrderList();
    if (res.code !== 200) throw new Error(res.msg || '获取订单失败');

    orderItems.value = [...(res.data || [])]
      .map((item) => ({
        co_id: Number(item.co_id || 0),
        co_order_no: String(item.co_order_no || '').trim(),
        product_name: String(item.product_name || '').trim(),
        amount: Number(item.amount || 0),
        co_created_at: String(item.co_created_at || '').trim(),
        status: Number(item.status || 0),
      }))
      .sort((a, b) => {
        const timeDiff = toTimestamp(b.co_created_at) - toTimestamp(a.co_created_at);
        if (timeDiff !== 0) return timeDiff;
        return Number(b.co_id || 0) - Number(a.co_id || 0);
      });
  } catch (err: any) {
    orderItems.value = [];
    orderError.value = err?.message || '获取订单失败';
  } finally {
    orderLoading.value = false;
  }
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function goToMoreProducts() {
  void router.push({ name: 'HomeResourceSource' });
}

function goToCartItems() {
  void router.push({ name: 'HomeCart' });
}

function goToMyOrders() {
  void router.push({ name: 'HomeMyOrders' });
}

const orderPreviewItems = computed(() => orderItems.value.slice(0, 8));

function getOrderStatusText(status: number) {
  if (Number(status) === 2) return '已支付';
  if (Number(status) === 3) return '已超时';
  return '未支付';
}

function getOrderStatusClass(status: number) {
  if (Number(status) === 2) return 'paid';
  if (Number(status) === 3) return 'timeout';
  return 'unpaid';
}

onMounted(() => {
  void loadProductRanking();
  void loadCartItems();
  void loadOrders();
});
</script>

<style scoped>
.resources-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.section-title {
  margin: 0;
  font-size: 30px;
  color: #1d4b78;
}

.resource-columns {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.resource-card {
  min-height: 620px;
  border-radius: 16px;
  border: 1px solid rgba(59, 130, 246, 0.24);
  background: rgba(255, 255, 255, 0.64);
  box-shadow: 0 12px 30px rgba(14, 49, 88, 0.08);
  display: flex;
  flex-direction: column;
  padding: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-header h4 {
  margin: 0;
  font-size: 24px;
  color: #1b4e80;
}

.hint-text {
  color: #4f7ca8;
  font-size: 16px;
}

.card-body {
  flex: 1;
  min-height: 0;
}

.state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #4c6d93;
}

.state.error {
  color: #d14d4d;
}

.rank-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid rgba(62, 126, 194, 0.2);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.72);
}

.rank-index {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  color: #3f6793;
  background: #eaf3ff;
  flex-shrink: 0;
}

.rank-index.top {
  color: #ffffff;
  background: linear-gradient(140deg, #f79a52, #f36c58);
}

.rank-main {
  flex: 1;
  min-width: 0;
}

.product-name {
  margin: 0;
  font-size: 15px;
  color: #23496f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #5f7f9f;
}

.rank-sales {
  text-align: right;
  color: #4e7095;
  font-size: 12px;
}

.rank-sales strong {
  display: block;
  margin-top: 2px;
  color: #21558a;
  font-size: 16px;
}

.placeholder-card {
  justify-content: flex-start;
}

.placeholder-body {
  flex: 1;
  border-radius: 12px;
  border: 1px dashed rgba(74, 137, 205, 0.36);
  background: rgba(250, 253, 255, 0.8);
  color: #6486a8;
  display: flex;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 24px;
  font-size: 15px;
}

.cart-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cart-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(62, 126, 194, 0.2);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.72);
}

.cart-main {
  flex: 1;
  min-width: 0;
}

.cart-name {
  margin: 0;
  font-size: 15px;
  color: #23496f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cart-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #5f7f9f;
}

.cart-amount {
  text-align: right;
  color: #4e7095;
  font-size: 12px;
}

.cart-amount strong {
  display: block;
  margin-top: 2px;
  color: #21558a;
  font-size: 16px;
}

.order-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.order-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid rgba(62, 126, 194, 0.2);
  border-radius: 12px;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.72);
}

.order-main {
  flex: 1;
  min-width: 0;
}

.order-name {
  margin: 0;
  font-size: 15px;
  color: #23496f;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.order-meta {
  margin: 4px 0 0;
  font-size: 12px;
  color: #4f6f92;
}

.order-time {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6d88a7;
}

.order-side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
}

.order-price {
  color: #21558a;
  font-size: 14px;
  font-weight: 700;
}

:deep(.order-side .el-tag) {
  border-radius: 999px;
}

:deep(.order-side .el-tag.unpaid) {
  color: #b5761d;
  background: rgba(255, 243, 218, 0.9);
  border-color: rgba(236, 193, 121, 0.8);
}

:deep(.order-side .el-tag.paid) {
  color: #1f8a4a;
  background: rgba(231, 249, 238, 0.9);
  border-color: rgba(123, 211, 156, 0.86);
}

:deep(.order-side .el-tag.timeout) {
  color: #c64b4b;
  background: rgba(255, 238, 238, 0.92);
  border-color: rgba(235, 151, 151, 0.82);
}

@media (max-width: 1200px) {
  .resource-columns {
    grid-template-columns: 1fr;
  }

  .resource-card {
    min-height: 360px;
  }
}
</style>
