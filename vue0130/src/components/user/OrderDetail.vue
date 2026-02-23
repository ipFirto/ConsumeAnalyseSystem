<template>
  <section class="order-list-page">
    <div class="panel">
      <header class="panel-header">
        <div>
          <h2>{{ text.title }}</h2>
          <p>{{ text.subTitle }}</p>
        </div>
        <div class="header-actions">
          <el-button :loading="loading" @click="loadData">{{ text.refresh }}</el-button>
        </div>
      </header>

      <div class="filter-bar">
        <el-input v-model.trim="keyword" :placeholder="text.keywordPlaceholder" clearable />
        <el-select v-model="statusFilter" :placeholder="text.statusPlaceholder">
          <el-option :label="text.statusAll" value="all" />
          <el-option :label="text.statusEnabled" value="1" />
          <el-option :label="text.statusDisabled" value="0" />
        </el-select>
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
        <div class="table-wrap">
          <el-table
            class="order-table"
            :data="pagedRows"
            border
            stripe
            size="small"
            show-overflow-tooltip
            height="100%"
            row-key="co_id"
          >
          <el-table-column prop="co_id" :label="text.colId" width="64" />
          <el-table-column prop="co_order_no" :label="text.colOrderNo" min-width="140" />
          <el-table-column prop="user_name" :label="text.colUserName" min-width="90" />
          <el-table-column prop="user_email" :label="text.colEmail" min-width="125" />
          <el-table-column prop="u_phone" :label="text.colPhone" min-width="100" />
          <el-table-column prop="product_name" :label="text.colProduct" min-width="110" />
          <el-table-column prop="p_brand" :label="text.colBrand" min-width="90" />
          <el-table-column :label="text.colAmount" min-width="86" align="right">
            <template #default="{ row }">CNY {{ formatAmount(row.amount) }}</template>
          </el-table-column>
          <el-table-column :label="text.colStatus" min-width="72" align="center">
            <template #default="{ row }">
              <el-select v-if="isEditing(row)" v-model="editDraft.u_status">
                <el-option :label="text.statusEnabled" :value="1" />
                <el-option :label="text.statusDisabled" :value="0" />
              </el-select>
              <el-tag v-else :type="Number(row.u_status) === 1 ? 'success' : 'danger'">
                {{ Number(row.u_status) === 1 ? text.statusEnabled : text.statusDisabled }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="text.colRemark" min-width="100">
            <template #default="{ row }">
              <el-input
                v-if="isEditing(row)"
                v-model="editDraft.co_remark"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 3 }"
              />
              <span v-else>{{ row.co_remark || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="pf_name" :label="text.colPlatform" min-width="72" />
          <el-table-column prop="co_created_at" :label="text.colCreatedAt" min-width="124" />
          <el-table-column :label="text.colActions" min-width="100">
            <template #default="{ row }">
              <div class="action-group">
                <template v-if="isEditing(row)">
                  <el-button type="primary" link @click="saveEdit(row)">{{ text.save }}</el-button>
                  <el-button link @click="cancelEdit">{{ text.cancel }}</el-button>
                </template>
                <template v-else>
                  <el-button type="primary" link @click="startEdit(row)">{{ text.edit }}</el-button>
                  <el-button link @click="viewDetail(row)">{{ text.viewDetail }}</el-button>
                </template>
              </div>
            </template>
          </el-table-column>
          </el-table>
        </div>

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
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { Loading, WarningFilled } from '@element-plus/icons-vue';
import { getUserOrderList, type UserOrderRecord } from '@/api/modules/userOrder';
import { DEFAULT_PLATFORM_LIST, loadPlatformList, type PlatformMeta } from '@/constants/platform';

interface UserOrderRow extends UserOrderRecord {}

const text = {
  title: '用户订单详情',
  subTitle: '数据来源：/show/userOrder，可在表格中修改部分字段',
  refresh: '刷新',
  loading: '正在加载用户订单...',
  fetchFailed: '获取用户订单失败',
  keywordPlaceholder: '搜索订单号 / 用户 / 邮箱 / 商品',
  statusPlaceholder: '筛选用户状态',
  statusAll: '全部状态',
  statusEnabled: '启用',
  statusDisabled: '禁用',
  colId: 'ID',
  colOrderNo: '订单号',
  colUserName: '用户名称',
  colEmail: '邮箱',
  colPhone: '手机号',
  colProduct: '商品',
  colBrand: '分类/品牌',
  colAmount: '金额',
  colStatus: '状态',
  colRemark: '备注',
  colPlatform: '平台',
  colCreatedAt: '下单时间',
  colActions: '操作',
  edit: '编辑',
  save: '保存',
  cancel: '取消',
  viewDetail: '查看详情',
  totalLabel: '筛选后记录：',
  nameRequired: '用户名称不能为空',
  amountInvalid: '金额不能为负数',
  saveSuccess: '已保存当前行修改',
};

const router = useRouter();

const rows = ref<UserOrderRow[]>([]);
const loading = ref(false);
const error = ref('');
const keyword = ref('');
const statusFilter = ref<'all' | '1' | '0'>('all');
const page = ref({
  current: 1,
  size: 20,
});

const platformList = ref<PlatformMeta[]>([...DEFAULT_PLATFORM_LIST]);

const editingRowId = ref<number | null>(null);
const editDraft = ref<{
  u_status: number;
  co_remark: string;
}>({
  u_status: 1,
  co_remark: '',
});

let requestSerial = 0;

function asString(raw: unknown) {
  return String(raw ?? '').trim();
}

function asNumber(raw: unknown, fallback = 0) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function normalizeList(rawData: unknown): any[] {
  if (Array.isArray(rawData)) return rawData;
  if (rawData && typeof rawData === 'object') {
    const obj = rawData as Record<string, unknown>;
    if (Array.isArray(obj.list)) return obj.list;
    if (Array.isArray(obj.records)) return obj.records;
    if (Array.isArray(obj.rows)) return obj.rows;
  }
  return [];
}

function normalizeRow(raw: any): UserOrderRow {
  return {
    co_id: asNumber(raw?.co_id ?? raw?.coId ?? raw?.id),
    co_order_no: asString(raw?.co_order_no ?? raw?.coOrderNo ?? raw?.orderNo),
    user_name: asString(raw?.user_name ?? raw?.userName),
    user_email: asString(raw?.user_email ?? raw?.userEmail),
    u_phone: asString(raw?.u_phone ?? raw?.uPhone),
    u_status: asNumber(raw?.u_status ?? raw?.uStatus, 1),
    product_name: asString(raw?.product_name ?? raw?.productName),
    p_brand: asString(raw?.p_brand ?? raw?.pBrand),
    amount: asNumber(raw?.amount),
    pf_name: asString(raw?.pf_name ?? raw?.pfName),
    pr_name: asString(raw?.pr_name ?? raw?.prName),
    c_name: asString(raw?.c_name ?? raw?.cName),
    co_created_at: asString(raw?.co_created_at ?? raw?.coCreatedAt),
    co_remark: asString(raw?.co_remark ?? raw?.coRemark),
  };
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

function isEditing(row: UserOrderRow) {
  return editingRowId.value === Number(row.co_id);
}

function startEdit(row: UserOrderRow) {
  editingRowId.value = Number(row.co_id);
  editDraft.value = {
    u_status: Number(row.u_status) === 1 ? 1 : 0,
    co_remark: row.co_remark || '',
  };
}

function cancelEdit() {
  editingRowId.value = null;
}

function saveEdit(row: UserOrderRow) {
  row.u_status = Number(editDraft.value.u_status) === 1 ? 1 : 0;
  row.co_remark = asString(editDraft.value.co_remark);

  editingRowId.value = null;
  ElMessage.success(text.saveSuccess);
}

function resolvePlatformIdByName(name: string) {
  return platformList.value.find((item) => item.name === name)?.id ?? null;
}

function viewDetail(row: UserOrderRow) {
  const orderId = Number(row.co_id);
  if (!Number.isFinite(orderId) || orderId <= 0) return;

  const query: Record<string, string> = {
    orderId: String(orderId),
    backTo: 'orderDetail',
  };
  const platformId = resolvePlatformIdByName(String(row.pf_name || ''));
  if (platformId) query.platformId = String(platformId);

  void router.push({
    name: 'HomeAllOrderDetail',
    query,
  });
}

const filteredRows = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return rows.value.filter((row) => {
    if (statusFilter.value !== 'all' && String(Number(row.u_status) === 1 ? 1 : 0) !== statusFilter.value) {
      return false;
    }

    if (!kw) return true;
    const text = [
      row.co_order_no,
      row.user_name,
      row.user_email,
      row.product_name,
      row.p_brand,
      row.u_phone,
      row.pf_name,
    ]
      .join(' ')
      .toLowerCase();
    return text.includes(kw);
  });
});

const pagedRows = computed(() => {
  const start = (page.value.current - 1) * page.value.size;
  return filteredRows.value.slice(start, start + page.value.size);
});

watch(
  () => [filteredRows.value.length, page.value.size],
  () => {
    const maxPage = Math.max(1, Math.ceil(filteredRows.value.length / page.value.size));
    if (page.value.current > maxPage) {
      page.value.current = maxPage;
    }
  }
);

watch([keyword, statusFilter], () => {
  page.value.current = 1;
});

async function loadData() {
  const reqId = ++requestSerial;
  loading.value = true;
  error.value = '';
  try {
    const res = await getUserOrderList();
    if (reqId !== requestSerial) return;
    if (Number(res.code) !== 200) {
      throw new Error(String(res.msg || text.fetchFailed));
    }
    rows.value = normalizeList(res.data).map(normalizeRow).filter((row) => Number(row.co_id) > 0);
  } catch (err: any) {
    if (reqId !== requestSerial) return;
    rows.value = [];
    error.value = err?.message || text.fetchFailed;
  } finally {
    if (reqId !== requestSerial) return;
    loading.value = false;
  }
}

onMounted(async () => {
  platformList.value = await loadPlatformList();
  await loadData();
});

defineExpose({
  refresh: loadData,
});
</script>

<style scoped>
.order-list-page {
  padding: 24px;
  width: 100%;
  min-width: 0;
  min-height: calc(100dvh - 72px);
  display: flex;
}

.panel {
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(59, 130, 246, 0.18);
  border-radius: 18px;
  padding: 16px;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-bar {
  margin: 14px 0 12px;
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 10px;
}

.state {
  min-height: 180px;
  flex: 1;
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

.action-group {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 4px;
}

.table-wrap {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

:deep(.order-table) {
  width: 100%;
  font-size: 12px;
}

:deep(.order-table .el-table__cell) {
  padding: 6px 4px;
}

:deep(.order-table .cell) {
  padding: 0 1px;
}

:deep(.order-table .el-select .el-select__wrapper),
:deep(.order-table .el-input__wrapper) {
  min-height: 26px;
}

:deep(.order-table .el-table__header .cell) {
  font-size: 12px;
}

:deep(.order-table .el-button) {
  font-size: 12px;
  padding: 0 2px;
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

@keyframes spin {
  from {
    transform: rotate(0);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 960px) {
  .order-list-page {
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

  .filter-bar {
    grid-template-columns: 1fr;
  }

  .pager-wrap {
    flex-direction: column;
    align-items: flex-start;
  }

  .table-wrap {
    overflow: auto;
    border-radius: 12px;
    -webkit-overflow-scrolling: touch;
  }

  :deep(.order-table) {
    min-width: 980px;
  }
}
</style>
