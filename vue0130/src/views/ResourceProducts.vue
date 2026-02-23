<template>
  <section class="resource-products-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>商品列表</h2>
          <p>按销量从高到低展示，支持平台、品类与关键词筛选。</p>
        </div>
        <div class="header-actions">
          <el-button @click="refreshProducts">刷新数据</el-button>
          <el-button type="primary" @click="goBack">返回资源页</el-button>
        </div>
      </header>

      <div class="filters">
        <el-select v-model="selectedPlatformId" class="filter-item" placeholder="选择平台">
          <el-option :label="allPlatformLabel" :value="ALL_FILTER_KEY" />
          <el-option v-for="item in platformOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>

        <el-select v-model="selectedCategory" class="filter-item" placeholder="选择品类">
          <el-option :label="allCategoryLabel" :value="ALL_FILTER_KEY" />
          <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
        </el-select>

        <el-input
          v-model="keyword"
          class="search-input"
          clearable
          placeholder="搜索商品（支持模糊查找）"
        />

        <div class="city-filter">
          <span>城市ID</span>
          <el-input-number v-model="cityId" :min="1" :step="1" :precision="0" class="city-input" />
        </div>
      </div>

      <div v-if="loading" class="state">正在加载商品数据...</div>
      <div v-else-if="error" class="state error">{{ error }}</div>
      <div v-else-if="!filteredProducts.length" class="empty-wrap">
        <el-empty description="没有匹配的商品" />
      </div>

      <div v-else class="product-grid">
        <article v-for="(item, index) in filteredProducts" :key="item.key" class="product-tile">
          <span class="tile-rank">#{{ index + 1 }}</span>
          <h3 :title="item.productName">{{ item.productName }}</h3>
          <p>{{ item.platformName }}</p>
          <p>{{ item.category }}</p>
          <p class="amount-line">金额 CNY {{ formatAmount(item.itemAmount) }}</p>
          <p class="stock-line">库存剩余 {{ formatStock(item.stockRemaining) }}</p>
          <div class="tile-footer">
            <strong>销量 {{ item.salesCount }}</strong>
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="item.productId <= 0"
              :loading="addingItemKey === item.key"
              @click="handleAddToCart(item)"
            >
              添加购物车
            </el-button>
          </div>
        </article>
      </div>
    </div>

    <button class="checkout-fab" type="button" @click="goToCartItems">
      去结算
    </button>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { getResourceProductDataset, type ResourceProductItem } from '@/api/modules/resourceProducts';
import { addToCart } from '@/api/modules/mq';
import type { PlatformMeta } from '@/constants/platform';

const ALL_FILTER_KEY = '__all__';
const allPlatformLabel = '全部平台';
const allCategoryLabel = '全部品类';
const CART_CITY_ID_CACHE_KEY = 'resource.cart.city_id';
const cachedCityId = Number(localStorage.getItem(CART_CITY_ID_CACHE_KEY) || '');

const router = useRouter();
const loading = ref(false);
const error = ref('');

const products = ref<ResourceProductItem[]>([]);
const platformOptions = ref<PlatformMeta[]>([]);

const selectedPlatformId = ref<number | string>(ALL_FILTER_KEY);
const selectedCategory = ref<string>(ALL_FILTER_KEY);
const keyword = ref('');
const cityId = ref<number>(Number.isFinite(cachedCityId) && cachedCityId > 0 ? Math.floor(cachedCityId) : 1);
const addingItemKey = ref('');

const categoryOptions = computed(() => {
  const list =
    selectedPlatformId.value === ALL_FILTER_KEY
      ? products.value
      : products.value.filter((item) => item.platformId === Number(selectedPlatformId.value));

  return Array.from(new Set(list.map((item) => item.category))).sort((a, b) =>
    a.localeCompare(b, 'zh-CN')
  );
});

watch(categoryOptions, (nextList) => {
  if (selectedCategory.value !== ALL_FILTER_KEY && !nextList.includes(selectedCategory.value)) {
    selectedCategory.value = ALL_FILTER_KEY;
  }
});

const filteredProducts = computed(() => {
  const key = keyword.value.trim().toLowerCase();

  return products.value.filter((item) => {
    if (selectedPlatformId.value !== ALL_FILTER_KEY && item.platformId !== Number(selectedPlatformId.value)) {
      return false;
    }
    if (selectedCategory.value !== ALL_FILTER_KEY && item.category !== selectedCategory.value) {
      return false;
    }
    if (!key) return true;

    const matchTarget = `${item.productName} ${item.platformName} ${item.category}`.toLowerCase();
    return matchTarget.includes(key);
  });
});

async function loadProducts(forceRefresh = false) {
  loading.value = true;
  error.value = '';
  try {
    const dataset = await getResourceProductDataset({ forceRefresh });
    products.value = dataset.products;
    platformOptions.value = dataset.platforms;
  } catch (err: any) {
    products.value = [];
    platformOptions.value = [];
    error.value = err?.message || '获取商品列表失败';
  } finally {
    loading.value = false;
  }
}

function refreshProducts() {
  void loadProducts(true);
}

function goBack() {
  void router.push({ name: 'HomeIndex' });
}

function goToCartItems() {
  void router.push({ name: 'HomeCart' });
}

onMounted(() => {
  void loadProducts();
});

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function formatStock(value: number) {
  const safe = Math.floor(Number(value || 0));
  return String(safe > 0 ? safe : 0);
}

async function handleAddToCart(item: ResourceProductItem) {
  const pid = Number(item.productId);
  const safeCityId = Number(cityId.value);

  if (!Number.isFinite(pid) || pid <= 0) {
    ElMessage.warning('当前商品缺少商品ID，无法加入购物车');
    return;
  }
  if (!Number.isFinite(safeCityId) || safeCityId <= 0) {
    ElMessage.warning('请输入有效的城市ID');
    return;
  }

  addingItemKey.value = item.key;
  try {
    const res = await addToCart({ pid, cityId: safeCityId });
    if (res.code !== 200) throw new Error(res.msg || '添加购物车失败');
    ElMessage.success(res.data || '添加购物车成功');
  } catch (err: any) {
    ElMessage.error(err?.message || '添加购物车失败');
  } finally {
    if (addingItemKey.value === item.key) {
      addingItemKey.value = '';
    }
  }
}

watch(cityId, (next) => {
  const safe = Number(next);
  if (Number.isFinite(safe) && safe > 0) {
    localStorage.setItem(CART_CITY_ID_CACHE_KEY, String(Math.floor(safe)));
  }
});
</script>

<style scoped>
.resource-products-page {
  min-height: 100dvh;
  padding: 24px;
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

.filters {
  display: grid;
  grid-template-columns: 220px 220px minmax(260px, 1fr) 180px;
  gap: 10px;
  margin-bottom: 16px;
}

.filter-item {
  width: 100%;
}

.search-input {
  width: 100%;
}

.city-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #4f6f94;
  font-size: 13px;
}

.city-input {
  width: 112px;
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

.product-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.product-tile {
  aspect-ratio: 1 / 1;
  border-radius: 12px;
  border: 1px solid rgba(37, 99, 235, 0.18);
  background: rgba(255, 255, 255, 0.9);
  padding: 10px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 20px rgba(18, 55, 98, 0.08);
}

.tile-rank {
  color: #6688ad;
  font-size: 12px;
}

.product-tile h3 {
  margin: 8px 0 4px;
  font-size: 14px;
  color: #204a74;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  min-height: 38px;
}

.product-tile p {
  margin: 4px 0 0;
  color: #5c7897;
  font-size: 12px;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-tile strong {
  color: #1f5b94;
  font-size: 14px;
}

.amount-line {
  color: #2f6ba2 !important;
  font-weight: 600;
}

.stock-line {
  color: #2f8a54 !important;
  font-weight: 600;
}

.tile-footer {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.checkout-fab {
  position: fixed;
  right: 30px;
  bottom: 28px;
  width: 78px;
  height: 78px;
  border-radius: 999px;
  border: 1px solid rgba(32, 107, 183, 0.26);
  background: linear-gradient(145deg, #2f7ed8, #19b395);
  color: #ffffff;
  font-size: 17px;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 14px 28px rgba(15, 62, 112, 0.3);
  z-index: 1500;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.checkout-fab:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(15, 62, 112, 0.36);
}

.checkout-fab:active {
  transform: translateY(0);
}

@media (max-width: 1200px) {
  .product-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .resource-products-page {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .header-actions {
    width: 100%;
    flex-wrap: wrap;
  }

  .filters {
    grid-template-columns: 1fr;
  }

  .product-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .checkout-fab {
    right: 16px;
    bottom: 16px;
    width: 66px;
    height: 66px;
    font-size: 15px;
  }
}

@media (max-width: 640px) {
  .resource-products-page {
    padding: 10px;
  }

  .panel {
    padding: 12px;
    border-radius: 14px;
  }

  .panel-header h2 {
    font-size: 22px;
  }

  .panel-header p {
    font-size: 12px;
  }

  .filters {
    gap: 8px;
    margin-bottom: 12px;
  }

  .city-filter {
    justify-content: space-between;
    padding: 0 2px;
  }

  .city-input {
    width: 120px;
  }

  .product-grid {
    grid-template-columns: 1fr;
    gap: 10px;
  }

  .product-tile {
    aspect-ratio: auto;
    min-height: 176px;
    padding: 11px;
  }

  .product-tile h3 {
    font-size: 16px;
    min-height: auto;
  }

  .product-tile p {
    white-space: normal;
  }

  .checkout-fab {
    right: 12px;
    bottom: 12px;
    width: 60px;
    height: 60px;
    font-size: 14px;
  }
}
</style>
