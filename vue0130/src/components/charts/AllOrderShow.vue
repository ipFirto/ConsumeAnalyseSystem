<template>
  <section class="product-view-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>{{ text.subTitle }}</p>
        </div>
      </header>

      <div class="filter-bar">
        <el-select v-model="platformId" :placeholder="text.platformPlaceholder" @change="onPlatformChange">
          <el-option v-for="item in platformOptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-select v-model="status">
          <el-option :label="text.statusEnabled" :value="1" />
          <el-option :label="text.statusDisabled" :value="0" />
        </el-select>
        <el-select v-model="categoryFilter">
          <el-option :label="text.categoryAll" value="all" />
          <el-option v-for="name in categoryOptions" :key="name" :label="name" :value="name" />
        </el-select>
        <el-input v-model.trim="keyword" :placeholder="text.keywordPlaceholder" clearable />
        <el-select v-model="limit">
          <el-option v-for="item in limitOptions" :key="item" :label="`${item}`" :value="item" />
        </el-select>
      </div>

      <div class="stats">
        <div class="stat-card">
          <div class="label">{{ text.loadedCount }}</div>
          <div class="value">{{ rows.length }}</div>
        </div>
        <div class="stat-card">
          <div class="label">{{ text.filteredCount }}</div>
          <div class="value">{{ filteredRows.length }}</div>
        </div>
        <div class="stat-card">
          <div class="label">{{ text.maxProductId }}</div>
          <div class="value">{{ maxProductId }}</div>
        </div>
        <div class="stat-card">
          <div class="label">{{ text.lastUpdated }}</div>
          <div class="value small">{{ lastUpdatedLabel }}</div>
        </div>
      </div>

      <div v-if="loading && !rows.length" class="state">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loading }}</span>
      </div>

      <div v-else-if="error && !rows.length" class="state error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ error }}</span>
      </div>

      <template v-else>
        <el-table
          :data="pagedRows"
          border
          stripe
          height="620"
          row-key="id"
          :default-sort="{ prop: 'created_at', order: 'descending' }"
        >
          <el-table-column prop="id" :label="text.colId" min-width="90" sortable />
          <el-table-column prop="product_name" :label="text.colProductName" min-width="180">
            <template #default="{ row }">
              <el-input
                v-if="isEditing(row)"
                v-model.trim="editDraft.product_name"
                :placeholder="text.colProductName"
              />
              <span v-else>{{ row.product_name || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="brand" :label="text.colBrand" min-width="130">
            <template #default="{ row }">
              <el-input v-if="isEditing(row)" v-model.trim="editDraft.brand" :placeholder="text.colBrand" />
              <span v-else>{{ row.brand || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" :label="text.colCategory" min-width="130">
            <template #default="{ row }">
              <el-select v-if="isEditing(row)" v-model="editDraft.category" filterable allow-create default-first-option>
                <el-option v-for="name in categoryOptions" :key="name" :label="name" :value="name" />
              </el-select>
              <span v-else>{{ row.category || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="platform_name" :label="text.colPlatform" min-width="120" />
          <el-table-column prop="status" :label="text.colStatus" min-width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'">
                {{ Number(row.status) === 1 ? text.statusEnabled : text.statusDisabled }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="created_at"
            :label="text.colCreatedAt"
            min-width="180"
            sortable
            :sort-method="sortByCreatedAt"
          >
            <template #default="{ row }">{{ formatDateTime(row.created_at) }}</template>
          </el-table-column>
          <el-table-column
            prop="updated_at"
            :label="text.colUpdatedAt"
            min-width="180"
            sortable
            :sort-method="sortByUpdatedAt"
          >
            <template #default="{ row }">{{ formatDateTime(row.updated_at) }}</template>
          </el-table-column>
          <el-table-column :label="text.colAction" min-width="220" fixed="right" align="center">
            <template #default="{ row }">
              <div class="action-group">
                <template v-if="isEditing(row)">
                  <el-button
                    type="primary"
                    link
                    :loading="savingRowId === Number(row.id)"
                    @click="saveEdit(row)"
                  >
                    {{ text.save }}
                  </el-button>
                  <el-button link @click="cancelEdit">{{ text.cancel }}</el-button>
                </template>
                <template v-else>
                  <el-button type="primary" link @click="openDetail(row)">{{ text.detail }}</el-button>
                  <el-button type="primary" link @click="startEdit(row)">{{ text.edit }}</el-button>
                  <el-button
                    :type="Number(row.status) === 0 ? 'success' : 'danger'"
                    link
                    :loading="deletingRowId === Number(row.id)"
                    @click="toggleStatus(row)"
                  >
                    {{ Number(row.status) === 0 ? text.enable : text.delete }}
                  </el-button>
                </template>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager-wrap">
          <span class="pager-info">{{ text.totalLabel }} {{ filteredRows.length }}</span>
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

    <el-drawer
      v-model="detailVisible"
      :title="text.detailTitle"
      size="420px"
      append-to-body
      :z-index="4000"
    >
      <div v-if="detailLoading" class="state">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>{{ text.loadingDetail }}</span>
      </div>
      <div v-else-if="detailError" class="state error">
        <el-icon><WarningFilled /></el-icon>
        <span>{{ detailError }}</span>
      </div>
      <template v-else-if="detailRow">
        <div class="detail-grid">
          <div class="detail-item"><span>{{ text.colId }}</span><b>{{ detailRow.id }}</b></div>
          <div class="detail-item"><span>{{ text.colProductName }}</span><b>{{ detailRow.product_name || '-' }}</b></div>
          <div class="detail-item"><span>{{ text.colBrand }}</span><b>{{ detailRow.brand || '-' }}</b></div>
          <div class="detail-item"><span>{{ text.colCategory }}</span><b>{{ detailRow.category || '-' }}</b></div>
          <div class="detail-item"><span>{{ text.colPlatform }}</span><b>{{ detailRow.platform_name || '-' }}</b></div>
          <div class="detail-item">
            <span>{{ text.colStatus }}</span>
            <b>{{ Number(detailRow.status) === 1 ? text.statusEnabled : text.statusDisabled }}</b>
          </div>
          <div class="detail-item"><span>{{ text.colCreatedAt }}</span><b>{{ formatDateTime(detailRow.created_at) }}</b></div>
          <div class="detail-item"><span>{{ text.colUpdatedAt }}</span><b>{{ formatDateTime(detailRow.updated_at) }}</b></div>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import type { ProductPlatformOption, ProductRecord } from '@/api/modules/product';
import {
  deleteProduct,
  getProductCategories,
  getProductList,
  getProductOne,
  getProductPlatforms,
  updateProduct,
} from '@/api/modules/product';
import { DEFAULT_PLATFORM_LIST } from '@/constants/platform';

const text = {
  title: '商品数据查看',
  subTitle: '数据来源：/product/list，首次全量加载，页面内实时增量刷新',
  loading: '正在加载商品数据...',
  fetchFailed: '获取商品数据失败',
  loadingDetail: '正在加载商品详情...',
  detailFailed: '获取商品详情失败',
  incrementalFailed: '增量刷新失败',
  platformPlaceholder: '选择平台',
  categoryAll: '全部分类',
  keywordPlaceholder: '搜索商品名 / 品牌 / 分类 / 平台 / ID',
  statusEnabled: '启用',
  statusDisabled: '禁用',
  loadedCount: '已加载商品',
  filteredCount: '筛选后数量',
  maxProductId: '当前最大ID',
  lastUpdated: '最近刷新',
  totalLabel: '筛选后记录：',
  colId: 'ID',
  colProductName: '商品名',
  colBrand: '品牌',
  colCategory: '分类',
  colPlatform: '平台',
  colStatus: '状态',
  colCreatedAt: '创建时间',
  colUpdatedAt: '更新时间',
  colAction: '操作',
  detail: '查看详情',
  detailTitle: '商品详情',
  edit: '编辑',
  save: '保存',
  cancel: '取消',
  delete: '禁用',
  enable: '启用',
  saveSuccess: '商品修改成功',
  saveFailed: '商品修改失败',
  deleteConfirm: '确认禁用该商品吗？',
  deleteSuccess: '商品禁用成功',
  deleteFailed: '商品禁用失败',
  enableConfirm: '确认启用该商品吗？',
  enableSuccess: '商品启用成功',
  enableFailed: '商品启用失败',
  fieldRequired: '商品名称、品牌、分类不能为空',
};

const limitOptions = [50, 100, 200, 500];
const AUTO_INCREMENT_INTERVAL_MS = 5000;

const platformOptions = ref<ProductPlatformOption[]>([]);
const platformId = ref<number | null>(null);
const status = ref<number>(1);
const categoryOptions = ref<string[]>([]);
const categoryFilter = ref<string>('all');
const keyword = ref('');
const limit = ref<number>(200);

const rows = ref<ProductRecord[]>([]);
const loading = ref(false);
const loadingIncremental = ref(false);
const error = ref('');
const lastUpdatedAt = ref<Date | null>(null);

const page = ref({
  current: 1,
  size: 20,
});

const detailVisible = ref(false);
const detailLoading = ref(false);
const detailError = ref('');
const detailRow = ref<ProductRecord | null>(null);
const editingRowId = ref<number | null>(null);
const savingRowId = ref<number | null>(null);
const deletingRowId = ref<number | null>(null);
const editDraft = ref({
  product_name: '',
  brand: '',
  category: '',
});

let requestSerial = 0;
let incrementalTimer: ReturnType<typeof setInterval> | null = null;

function toTimestamp(raw: string) {
  const normalized = raw.includes('T') ? raw : raw.replace(' ', 'T');
  const value = new Date(normalized).getTime();
  return Number.isFinite(value) ? value : 0;
}

function sortByCreatedAt(a: ProductRecord, b: ProductRecord) {
  return toTimestamp(a.created_at) - toTimestamp(b.created_at);
}

function sortByUpdatedAt(a: ProductRecord, b: ProductRecord) {
  return toTimestamp(a.updated_at) - toTimestamp(b.updated_at);
}

function sortRows(list: ProductRecord[]) {
  return [...list].sort((a, b) => {
    const timeDiff = toTimestamp(b.created_at) - toTimestamp(a.created_at);
    if (timeDiff !== 0) return timeDiff;
    return b.id - a.id;
  });
}

function mergeRows(incoming: ProductRecord[]) {
  const map = new Map<number, ProductRecord>();
  for (const row of rows.value) {
    map.set(row.id, row);
  }
  for (const row of incoming) {
    map.set(row.id, row);
  }
  rows.value = sortRows(Array.from(map.values()));
}

function formatDateTime(raw: string) {
  if (!raw) return '-';
  const ts = toTimestamp(raw);
  if (!ts) return raw;
  return new Date(ts).toLocaleString('zh-CN', { hour12: false });
}

const maxProductId = computed(() => rows.value.reduce((max, row) => Math.max(max, Number(row.id || 0)), 0));

const lastUpdatedLabel = computed(() =>
  lastUpdatedAt.value ? lastUpdatedAt.value.toLocaleTimeString('zh-CN', { hour12: false }) : '--:--:--'
);

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => {
    if (categoryFilter.value !== 'all' && row.category !== categoryFilter.value) return false;
    if (!kw) return true;
    const textStack = [row.id, row.product_name, row.brand, row.category, row.platform_name].join(' ').toLowerCase();
    return textStack.includes(kw);
  });
});

const pagedRows = computed(() => {
  const start = (page.value.current - 1) * page.value.size;
  return filteredRows.value.slice(start, start + page.value.size);
});

watch([keyword, categoryFilter], () => {
  page.value.current = 1;
});

watch(
  () => [filteredRows.value.length, page.value.size],
  () => {
    const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / page.value.size));
    if (page.value.current > maxPage) page.value.current = maxPage;
  }
);

async function loadPlatforms() {
  try {
    const res = await getProductPlatforms();
    if (res.code === 200 && res.data.length) {
      platformOptions.value = res.data;
    } else {
      throw new Error(res.msg || text.fetchFailed);
    }
  } catch {
    platformOptions.value = DEFAULT_PLATFORM_LIST.map((item) => ({
      id: item.id,
      name: item.name,
    }));
  }
  if (!platformId.value) {
    platformId.value = platformOptions.value[0]?.id ?? 1;
  }
}

async function loadCategories() {
  const pid = Number(platformId.value);
  if (!Number.isFinite(pid) || pid <= 0) {
    categoryOptions.value = [];
    categoryFilter.value = 'all';
    return;
  }
  try {
    const res = await getProductCategories(pid);
    categoryOptions.value = res.code === 200 ? res.data : [];
  } catch {
    categoryOptions.value = [];
  }
  if (categoryFilter.value !== 'all' && !categoryOptions.value.includes(categoryFilter.value)) {
    categoryFilter.value = 'all';
  }
}

async function loadInitial() {
  const pid = Number(platformId.value);
  if (!Number.isFinite(pid) || pid <= 0) {
    rows.value = [];
    error.value = text.platformPlaceholder;
    return;
  }

  cancelEdit();
  const reqId = ++requestSerial;
  loading.value = true;
  error.value = '';
  try {
    const res = await getProductList({
      platformId: pid,
      status: status.value,
      limit: limit.value,
      offset: 0,
    });
    if (reqId !== requestSerial) return;
    if (res.code !== 200) throw new Error(res.msg || text.fetchFailed);
    rows.value = sortRows(res.data || []);
    lastUpdatedAt.value = new Date();
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    rows.value = [];
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
  }
}

function onPlatformChange() {
  page.value.current = 1;
  cancelEdit();
  void (async () => {
    await loadCategories();
    await loadInitial();
    startIncrementalTimer();
  })();
}

function stopIncrementalTimer() {
  if (incrementalTimer) {
    clearInterval(incrementalTimer);
    incrementalTimer = null;
  }
}

function startIncrementalTimer() {
  stopIncrementalTimer();
  incrementalTimer = setInterval(() => {
    void loadIncremental();
  }, AUTO_INCREMENT_INTERVAL_MS);
}

function isEditing(row: ProductRecord) {
  return editingRowId.value === Number(row.id);
}

function startEdit(row: ProductRecord) {
  editingRowId.value = Number(row.id);
  editDraft.value = {
    product_name: String(row.product_name || ''),
    brand: String(row.brand || ''),
    category: String(row.category || ''),
  };
}

function cancelEdit() {
  editingRowId.value = null;
  savingRowId.value = null;
}

function replaceRow(nextRow: ProductRecord) {
  rows.value = rows.value.map((row) => (Number(row.id) === Number(nextRow.id) ? nextRow : row));
  if (detailRow.value && Number(detailRow.value.id) === Number(nextRow.id)) {
    detailRow.value = nextRow;
  }
}

async function loadIncremental() {
  const pid = Number(platformId.value);
  if (!Number.isFinite(pid) || pid <= 0 || loading.value || loadingIncremental.value) return;

  loadingIncremental.value = true;
  try {
    const res = await getProductList({
      platformId: pid,
      productId: maxProductId.value || undefined,
      status: status.value,
      limit: limit.value,
      offset: 0,
    });
    if (res.code !== 200) throw new Error(res.msg || text.incrementalFailed);

    mergeRows(res.data || []);
    lastUpdatedAt.value = new Date();
  } catch (err: any) {
    if (!rows.value.length) {
      error.value = err?.message || text.incrementalFailed;
    }
  } finally {
    loadingIncremental.value = false;
  }
}

async function saveEdit(row: ProductRecord) {
  const productId = Number(row.id);
  if (!Number.isFinite(productId) || productId <= 0) return;

  const productName = String(editDraft.value.product_name || '').trim();
  const brand = String(editDraft.value.brand || '').trim();
  const category = String(editDraft.value.category || '').trim();
  if (!productName || !brand || !category) {
    ElMessage.warning(text.fieldRequired);
    return;
  }

  savingRowId.value = productId;
  try {
    const res = await updateProduct({
      productId,
      productName,
      brand,
      platformName: row.platform_name || undefined,
      category,
    });
    if (res.code !== 200) throw new Error(res.msg || text.saveFailed);

    const detailRes = await getProductOne(productId);
    if (detailRes.code === 200 && detailRes.data) {
      replaceRow(detailRes.data);
    } else {
      replaceRow({
        ...row,
        product_name: productName,
        brand,
        category,
      });
    }
    editingRowId.value = null;
    lastUpdatedAt.value = new Date();
    ElMessage.success(text.saveSuccess);
  } catch (err: any) {
    ElMessage.error(err?.message || text.saveFailed);
  } finally {
    savingRowId.value = null;
  }
}

async function toggleStatus(row: ProductRecord) {
  const productId = Number(row.id);
  if (!Number.isFinite(productId) || productId <= 0) return;
  const nextStatus = Number(row.status) === 0 ? 1 : 0;
  const isEnableAction = nextStatus === 1;
  const confirmText = isEnableAction ? text.enableConfirm : text.deleteConfirm;

  try {
    await ElMessageBox.confirm(confirmText, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  deletingRowId.value = productId;
  try {
    const res = await deleteProduct({ productId, status: nextStatus });
    if (res.code !== 200) throw new Error(res.msg || (isEnableAction ? text.enableFailed : text.deleteFailed));

    if (status.value !== nextStatus) {
      rows.value = rows.value.filter((item) => Number(item.id) !== productId);
    } else {
      replaceRow({
        ...row,
        status: nextStatus,
        updated_at: new Date().toISOString().replace('T', ' ').slice(0, 19),
      });
    }
    if (detailRow.value && Number(detailRow.value.id) === productId) {
      detailVisible.value = false;
      detailRow.value = null;
    }
    if (editingRowId.value === productId) {
      editingRowId.value = null;
    }
    lastUpdatedAt.value = new Date();
    ElMessage.success(isEnableAction ? text.enableSuccess : text.deleteSuccess);
  } catch (err: any) {
    ElMessage.error(err?.message || (isEnableAction ? text.enableFailed : text.deleteFailed));
  } finally {
    deletingRowId.value = null;
  }
}

async function openDetail(row: ProductRecord) {
  const productId = Number(row.id);
  if (!Number.isFinite(productId) || productId <= 0) return;

  detailVisible.value = true;
  detailLoading.value = true;
  detailError.value = '';
  detailRow.value = null;
  try {
    const res = await getProductOne(productId);
    if (res.code !== 200) throw new Error(res.msg || text.detailFailed);
    detailRow.value = res.data || row;
  } catch (err: any) {
    detailError.value = err?.message || text.detailFailed;
    detailRow.value = row;
  } finally {
    detailLoading.value = false;
  }
}

watch([status, limit], () => {
  page.value.current = 1;
  cancelEdit();
  if (!platformId.value) return;
  void (async () => {
    await loadInitial();
    startIncrementalTimer();
  })();
});

onMounted(async () => {
  await loadPlatforms();
  if (platformId.value) {
    await loadCategories();
    await loadInitial();
    startIncrementalTimer();
  }
});

onBeforeUnmount(() => {
  stopIncrementalTimer();
});
</script>

<style scoped>
.product-view-page {
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-header h2 {
  margin: 0;
  color: #1f3f67;
}

.panel-header p {
  margin: 8px 0 0;
  color: #58749a;
  font-size: 13px;
}

.filter-bar {
  margin: 14px 0 12px;
  display: grid;
  grid-template-columns: 1.2fr 0.8fr 1fr 2fr 0.8fr;
  gap: 10px;
}

.stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
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

.stat-card .value.small {
  font-size: 18px;
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

.loading-icon {
  animation: spin 1s linear infinite;
}

.pager-wrap {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.pager-info {
  color: #4f6e95;
  font-size: 13px;
}

.action-group {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  flex-wrap: nowrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
}

.detail-item {
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 10px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.detail-item span {
  color: #607b9d;
  font-size: 12px;
}

.detail-item b {
  color: #1b3f67;
  font-size: 15px;
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

@media (max-width: 1180px) {
  .stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-bar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 960px) {
  .product-view-page {
    padding: 12px;
  }

  .panel {
    padding: 12px;
  }

  .panel-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-bar,
  .stats {
    grid-template-columns: 1fr;
  }

  .pager-wrap {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>


