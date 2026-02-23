<template>
  <section class="user-info-container">
    <div class="user-info-card">
      <header class="card-header">
        <div class="identity-wrap">
          <div class="avatar-wrap">
            <div class="avatar">{{ nameInitial }}</div>
            <div class="online-dot"></div>
          </div>

          <div class="title-wrap">
            <h2>个人信息</h2>
            <p>{{ profileName }}</p>
            <div class="identity-tags">
              <span class="badge good">账号状态正常</span>
              <span class="badge">资料完整度 {{ profileCompleteness }}%</span>
              <span class="badge">最近登录：{{ formatDate(profile?.lastLoginTime) }}</span>
              <span class="badge">累计订单：{{ allOrderCount }}</span>
            </div>
          </div>
        </div>

        <div class="header-actions">
          <div class="action-buttons">
            <el-button type="primary" plain @click="refreshAll">刷新资料</el-button>
            <el-button type="primary" @click="openEditDrawer" :disabled="!profile">编辑资料</el-button>
            <el-button @click="copyEmail" :disabled="!profile?.userEmail">复制邮箱</el-button>
          </div>
          <p class="action-tip">数据更新时间：{{ formatDate(lastRefreshedAt) }}</p>
        </div>
      </header>

      <div v-if="loading" class="state-block">
        <el-icon class="loading-icon"><Loading /></el-icon>
        <span>加载个人信息中...</span>
      </div>

      <div v-else-if="error" class="state-block error-state">
        <el-icon class="error-icon"><Warning /></el-icon>
        <span>{{ error }}</span>
        <el-button type="primary" size="small" @click="refreshAll">重试</el-button>
      </div>

      <div v-else-if="profile" class="user-info-content">
        <section class="metric-grid">
          <article class="metric-card">
            <p class="metric-label">资料完整度</p>
            <p class="metric-value">{{ profileCompleteness }}%</p>
            <div class="metric-progress">
              <el-progress :percentage="profileCompleteness" :show-text="false" :stroke-width="8" />
            </div>
            <p class="metric-desc">{{ completenessHint }}</p>
          </article>

          <article class="metric-card">
            <p class="metric-label">注册时长</p>
            <p class="metric-value">{{ accountAgeDays }}</p>
            <p class="metric-unit">天</p>
            <p class="metric-desc">注册于 {{ formatDate(profile.registerTime) }}</p>
          </article>

          <article class="metric-card">
            <p class="metric-label">安全等级</p>
            <p class="metric-value">{{ securityLevelText }}</p>
            <p class="metric-unit">级</p>
            <p class="metric-desc">{{ securityLevelHint }}</p>
          </article>

          <article class="metric-card">
            <p class="metric-label">活跃状态</p>
            <p class="metric-value">{{ activityState }}</p>
            <p class="metric-unit">状态</p>
            <p class="metric-desc">上次活跃：{{ formatDate(profile.lastLoginTime) }}</p>
          </article>
        </section>

        <section class="portrait-panel">
          <div class="section-title">
            <h3>消费画像</h3>
            <span>近30天行为</span>
          </div>

          <div v-if="portraitLoading" class="portrait-state">
            <el-icon class="loading-icon"><Loading /></el-icon>
            <span>消费画像计算中...</span>
          </div>

          <div v-else-if="portraitError" class="portrait-state error-state">
            <el-icon class="error-icon"><Warning /></el-icon>
            <span>{{ portraitError }}</span>
            <el-button type="primary" size="small" @click="fetchOrderPortrait">重试</el-button>
          </div>

          <div v-else class="portrait-grid">
            <article class="portrait-item">
              <p class="portrait-label">近30天订单数</p>
              <p class="portrait-value">{{ last30OrderCount }}</p>
              <p class="portrait-note">累计订单量</p>
            </article>

            <article class="portrait-item">
              <p class="portrait-label">近30天消费总额</p>
              <p class="portrait-value">CNY {{ formatAmount(last30OrderAmount) }}</p>
              <p class="portrait-note">订单金额汇总</p>
            </article>

            <article class="portrait-item">
              <p class="portrait-label">近30天客单价</p>
              <p class="portrait-value">CNY {{ formatAmount(last30AvgAmount) }}</p>
              <p class="portrait-note">消费总额 / 订单数</p>
            </article>

            <article class="portrait-item">
              <p class="portrait-label">活跃消费天数</p>
              <p class="portrait-value">{{ last30ActiveDays }}</p>
              <p class="portrait-note">近30天内有下单的天数</p>
            </article>

            <article class="portrait-item">
              <p class="portrait-label">偏好平台</p>
              <p class="portrait-value">{{ topPlatformName }}</p>
              <p class="portrait-note">出现频次最高</p>
            </article>

            <article class="portrait-item">
              <p class="portrait-label">常购品类</p>
              <p class="portrait-value">{{ topCategoryName }}</p>
              <p class="portrait-note">以订单数量统计</p>
            </article>
          </div>
        </section>

        <section class="detail-grid">
          <article class="detail-card">
            <div class="section-title">
              <h3>基础资料</h3>
              <span>信息详情</span>
            </div>

            <div class="info-item">
              <label>邮箱</label>
              <span class="value">{{ profile.userEmail || '未设置' }}</span>
            </div>
            <div class="info-item">
              <label>用户名</label>
              <span class="value">{{ profile.userName || '未设置' }}</span>
            </div>
            <div class="info-item">
              <label>手机号</label>
              <span class="value">{{ profile.phone || '未设置' }}</span>
            </div>
            <div class="info-item">
              <label>手机号(脱敏)</label>
              <span class="value">{{ maskedPhone }}</span>
            </div>
            <div class="info-item">
              <label>注册时间</label>
              <span class="value">{{ formatDate(profile.registerTime) }}</span>
            </div>
            <div class="info-item">
              <label>最后登录时间</label>
              <span class="value">{{ formatDate(profile.lastLoginTime) }}</span>
            </div>
          </article>

          <article class="detail-card timeline-card">
            <div class="section-title">
              <h3>账号动态</h3>
              <span>近期轨迹</span>
            </div>

            <div class="timeline-list">
              <div class="timeline-item">
                <span class="timeline-dot"></span>
                <div class="timeline-content">
                  <p class="timeline-title">账号创建</p>
                  <p class="timeline-time">{{ formatDate(profile.registerTime) }}</p>
                </div>
              </div>
              <div class="timeline-item">
                <span class="timeline-dot"></span>
                <div class="timeline-content">
                  <p class="timeline-title">最近登录</p>
                  <p class="timeline-time">{{ formatDate(profile.lastLoginTime) }}</p>
                </div>
              </div>
              <div class="timeline-item">
                <span class="timeline-dot"></span>
                <div class="timeline-content">
                  <p class="timeline-title">资料刷新</p>
                  <p class="timeline-time">{{ formatDate(lastRefreshedAt) }}</p>
                </div>
              </div>
            </div>

            <div class="security-panel">
              <p class="security-head">安全检查</p>
              <ul class="security-list">
                <li v-for="item in securityChecks" :key="item.label" :class="{ done: item.done }">
                  <span class="check-icon">{{ item.done ? 'OK' : 'TODO' }}</span>
                  <span>{{ item.label }}</span>
                </li>
              </ul>
            </div>
          </article>
        </section>
      </div>

      <div v-else class="state-block">
        <el-icon class="empty-icon"><InfoFilled /></el-icon>
        <span>暂无个人信息</span>
      </div>
    </div>

    <el-drawer
      v-model="editDrawerVisible"
      title="编辑个人资料"
      size="420px"
      :destroy-on-close="false"
      :with-header="true"
      :append-to-body="true"
      :z-index="3000"
    >
      <div class="edit-form">
        <el-form label-position="top">
          <el-form-item label="邮箱">
            <el-input v-model="editForm.userEmail" disabled />
          </el-form-item>

          <el-form-item label="用户名">
            <el-input
              v-model="editForm.userName"
              maxlength="20"
              show-word-limit
              placeholder="请输入用户名（2-20个字符）"
            />
          </el-form-item>

          <el-form-item label="手机号">
            <el-input
              v-model="editForm.phone"
              maxlength="11"
              placeholder="请输入11位手机号（可留空）"
            />
          </el-form-item>
        </el-form>

        <div class="edit-tips">
          当前项目暂未提供“修改资料”接口，点击保存会先本地更新页面展示。
        </div>
      </div>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="editDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveProfile">保存</el-button>
        </div>
      </template>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted } from 'vue';
import * as authApi from '@/api/modules/auth';
import { getUserOrderList, type UserOrderRecord } from '@/api/modules/userOrder';
import { ElMessage } from 'element-plus';
import { Loading, Warning, InfoFilled } from '@element-plus/icons-vue';

type SecurityCheck = {
  label: string;
  done: boolean;
};

type PortraitOrderRow = Pick<UserOrderRecord, 'amount' | 'co_created_at' | 'pf_name' | 'c_name' | 'p_brand'>;

const userInfo = ref<any>(null);
const loading = ref(false);
const error = ref('');
const portraitLoading = ref(false);
const portraitError = ref('');
const orderRows = ref<PortraitOrderRow[]>([]);
const editDrawerVisible = ref(false);
const lastRefreshedAt = ref<Date | null>(null);
const editForm = reactive({
  userEmail: '',
  userName: '',
  phone: '',
});

const profile = computed(() => userInfo.value?.data ?? null);
const allOrderCount = computed(() => orderRows.value.length);

const last30Orders = computed(() => {
  const now = Date.now();
  const threshold = now - 30 * 24 * 60 * 60 * 1000;
  return orderRows.value.filter((row) => {
    const ts = toTimestamp(row.co_created_at);
    return ts > 0 && ts >= threshold && ts <= now;
  });
});

const last30OrderCount = computed(() => last30Orders.value.length);
const last30OrderAmount = computed(() => last30Orders.value.reduce((sum, row) => sum + toNumber(row.amount), 0));
const last30AvgAmount = computed(() => {
  if (!last30OrderCount.value) return 0;
  return last30OrderAmount.value / last30OrderCount.value;
});

const last30ActiveDays = computed(() => {
  const daySet = new Set(
    last30Orders.value
      .map((row) => toDateKey(row.co_created_at))
      .filter((value) => value.length > 0)
  );
  return daySet.size;
});

const topPlatformName = computed(() => findTopName(last30Orders.value.map((row) => row.pf_name), '暂无'));
const topCategoryName = computed(() =>
  findTopName(last30Orders.value.map((row) => row.c_name || row.p_brand), '暂无')
);

const profileName = computed(() => {
  const name = String(profile.value?.userName || '').trim();
  return name || '未命名用户';
});

const nameInitial = computed(() => profileName.value.slice(0, 1).toUpperCase());

const profileCompleteness = computed(() => {
  const data = profile.value;
  if (!data) return 0;

  const fields = [data.userEmail, data.userName, data.phone, data.registerTime, data.lastLoginTime];
  const filled = fields.filter((field) => String(field || '').trim().length > 0).length;
  return Math.round((filled / fields.length) * 100);
});

const completenessHint = computed(() => {
  if (profileCompleteness.value >= 100) return '资料完整，账号画像清晰';
  if (profileCompleteness.value >= 80) return '整体良好，可继续完善联系方式';
  if (profileCompleteness.value >= 60) return '基础信息已具备，建议补齐手机号';
  return '资料较少，建议尽快完善';
});

const accountAgeDays = computed(() => {
  const raw = profile.value?.registerTime;
  if (!raw) return 0;
  const registerTime = new Date(raw);
  if (Number.isNaN(registerTime.getTime())) return 0;

  const oneDay = 1000 * 60 * 60 * 24;
  const diff = Math.floor((Date.now() - registerTime.getTime()) / oneDay);
  return Math.max(diff, 0);
});

const isRecentLogin = computed(() => {
  const raw = profile.value?.lastLoginTime;
  if (!raw) return false;
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return false;
  const diffDays = (Date.now() - date.getTime()) / (1000 * 60 * 60 * 24);
  return diffDays <= 30;
});

const securityLevelText = computed(() => {
  const hasPhone = Boolean(String(profile.value?.phone || '').trim());
  const score = profileCompleteness.value + (hasPhone ? 15 : 0) + (isRecentLogin.value ? 10 : 0);
  if (score >= 105) return '高';
  if (score >= 80) return '中';
  return '基础';
});

const securityLevelHint = computed(() => {
  if (securityLevelText.value === '高') return '认证资料较完整，风险控制较好';
  if (securityLevelText.value === '中') return '建议补齐手机号并保持登录活跃';
  return '建议完善联系方式并定期登录';
});

const activityState = computed(() => {
  const raw = profile.value?.lastLoginTime;
  if (!raw) return '待激活';
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) return '未知';
  const diffHours = (Date.now() - date.getTime()) / (1000 * 60 * 60);
  if (diffHours <= 24) return '活跃';
  if (diffHours <= 72) return '稳定';
  return '低活跃';
});

const maskedPhone = computed(() => {
  const phone = String(profile.value?.phone || '').trim();
  if (!phone) return '未设置';
  if (/^1\d{10}$/.test(phone)) {
    return `${phone.slice(0, 3)}****${phone.slice(7)}`;
  }
  return phone;
});

const securityChecks = computed<SecurityCheck[]>(() => [
  { label: '邮箱认证已绑定', done: Boolean(String(profile.value?.userEmail || '').trim()) },
  { label: '手机号已绑定', done: Boolean(String(profile.value?.phone || '').trim()) },
  { label: '近30天保持登录', done: isRecentLogin.value },
]);

function toNumber(raw: unknown, fallback = 0) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function toText(raw: unknown) {
  return String(raw ?? '').trim();
}

function toTimestamp(raw: unknown) {
  if (!raw) return 0;
  const date = new Date(raw as any);
  const timestamp = date.getTime();
  return Number.isFinite(timestamp) ? timestamp : 0;
}

function toDateKey(raw: unknown) {
  const timestamp = toTimestamp(raw);
  if (!timestamp) return '';
  const date = new Date(timestamp);
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function findTopName(values: Array<unknown>, fallbackText: string) {
  const counter = new Map<string, number>();
  values.forEach((value) => {
    const key = toText(value);
    if (!key) return;
    counter.set(key, (counter.get(key) || 0) + 1);
  });

  let topName = '';
  let topCount = 0;
  counter.forEach((count, name) => {
    if (count > topCount) {
      topName = name;
      topCount = count;
    }
  });

  return topName || fallbackText;
}

function normalizeList(rawData: unknown): any[] {
  if (Array.isArray(rawData)) return rawData;
  if (rawData && typeof rawData === 'object') {
    const obj = rawData as Record<string, unknown>;
    if (Array.isArray(obj.list)) return obj.list as any[];
    if (Array.isArray(obj.records)) return obj.records as any[];
    if (Array.isArray(obj.rows)) return obj.rows as any[];
    if (Array.isArray(obj.data)) return obj.data as any[];
  }
  return [];
}

function normalizeOrderRow(raw: any): PortraitOrderRow {
  return {
    amount: toNumber(raw?.amount),
    co_created_at: toText(raw?.co_created_at ?? raw?.coCreatedAt),
    pf_name: toText(raw?.pf_name ?? raw?.pfName),
    c_name: toText(raw?.c_name ?? raw?.cName),
    p_brand: toText(raw?.p_brand ?? raw?.pBrand),
  };
}

function formatDate(dateInput: string | number | Date | null | undefined) {
  if (!dateInput) return '未知';
  const date = new Date(dateInput);
  if (Number.isNaN(date.getTime())) return '未知';

  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

function formatAmount(value: number) {
  return Number(value || 0).toFixed(2);
}

async function fetchUserInfo() {
  loading.value = true;
  error.value = '';

  try {
    const info = await authApi.getUserInfo();
    userInfo.value = info.data;
    lastRefreshedAt.value = new Date();
  } catch (err) {
    error.value = '获取用户信息失败';
    ElMessage.error('获取用户信息失败');
    console.error('获取用户信息失败:', err);
  } finally {
    loading.value = false;
  }
}

async function fetchOrderPortrait() {
  portraitLoading.value = true;
  portraitError.value = '';

  try {
    const res = await getUserOrderList();
    if (Number(res.code) !== 200) {
      throw new Error(String(res.msg || '获取消费画像数据失败'));
    }

    orderRows.value = normalizeList(res.data)
      .map((item) => normalizeOrderRow(item))
      .filter((item) => item.amount >= 0);
  } catch (err: any) {
    orderRows.value = [];
    portraitError.value = err?.message || '获取消费画像数据失败';
  } finally {
    portraitLoading.value = false;
  }
}

async function refreshAll() {
  await Promise.allSettled([fetchUserInfo(), fetchOrderPortrait()]);
}

async function copyEmail() {
  const email = profile.value?.userEmail;
  if (!email) return;

  try {
    await navigator.clipboard.writeText(email);
    ElMessage.success('邮箱已复制');
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
}

function openEditDrawer() {
  const data = profile.value;
  if (!data) return;

  editForm.userEmail = data.userEmail || '';
  editForm.userName = data.userName || '';
  editForm.phone = data.phone || '';
  editDrawerVisible.value = true;
}

function saveProfile() {
  const userName = editForm.userName.trim();
  const phone = editForm.phone.trim();

  if (userName.length < 2 || userName.length > 20) {
    ElMessage.warning('用户名长度需在 2 到 20 个字符之间');
    return;
  }

  if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
    ElMessage.warning('手机号格式不正确');
    return;
  }

  if (profile.value) {
    profile.value.userName = userName;
    profile.value.phone = phone;
  }

  editDrawerVisible.value = false;
  ElMessage.success('保存成功（当前为本地预览效果）');
}

onMounted(() => {
  void refreshAll();
});

defineExpose({
  refresh: refreshAll,
});
</script>

<style lang="scss" scoped>
.user-info-container {
  --panel-bg:
    linear-gradient(146deg, rgba(255, 255, 255, 0.93), rgba(240, 240, 240, 0.9)),
    radial-gradient(42rem 20rem at 0% 0%, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0)),
    radial-gradient(38rem 18rem at 100% 100%, rgba(0, 0, 0, 0.08), rgba(0, 0, 0, 0));
  --border-soft: rgba(16, 16, 16, 0.14);
  --line-soft: rgba(16, 16, 16, 0.1);
  --text-main: #1a1a1a;
  --text-sub: #5f5f5f;
  width: 100%;
  min-height: 100%;
  display: grid;
  place-items: start center;
  padding: 30px 16px 36px;
  box-sizing: border-box;
}

.user-info-card {
  width: min(1120px, 98%);
  border-radius: 24px;
  border: 1px solid var(--border-soft);
  background: var(--panel-bg);
  box-shadow:
    0 24px 44px rgba(0, 0, 0, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 0.52);
  padding: 24px;
  backdrop-filter: blur(4px);
  animation: card-in 0.45s ease;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 16px;
}

.identity-wrap {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.avatar-wrap {
  position: relative;
}

.avatar {
  width: 62px;
  height: 62px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 28px;
  color: #ffffff;
  background: linear-gradient(145deg, #0f0f0f, #454545);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 12px 22px rgba(0, 0, 0, 0.28);
}

.online-dot {
  position: absolute;
  right: -3px;
  bottom: -3px;
  width: 14px;
  height: 14px;
  border-radius: 100%;
  border: 2px solid #ffffff;
  background: #19c264;
  box-shadow: 0 0 10px rgba(25, 194, 100, 0.6);
}

.title-wrap h2 {
  margin: 0;
  font-size: 44px;
  line-height: 1.04;
  color: #0f0f0f;
  letter-spacing: 0.02em;
}

.title-wrap p {
  margin: 6px 0 0;
  font-size: 18px;
  color: #4f4f4f;
}

.identity-tags {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.badge {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  border: 1px solid rgba(16, 16, 16, 0.16);
  padding: 6px 11px;
  font-size: 12px;
  color: #3f3f3f;
  background: rgba(255, 255, 255, 0.8);
}

.badge.good {
  color: #0f6e3f;
  border-color: rgba(15, 110, 63, 0.34);
  background: rgba(25, 194, 100, 0.12);
}

.header-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.action-tip {
  margin: 0;
  font-size: 12px;
  color: #666666;
}

.user-info-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 10px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.metric-card {
  border-radius: 16px;
  border: 1px solid var(--line-soft);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(244, 244, 244, 0.82)),
    radial-gradient(22rem 12rem at 0% 0%, rgba(255, 255, 255, 0.6), rgba(255, 255, 255, 0));
  padding: 14px 14px 12px;
  min-height: 122px;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.08);
}

.metric-label {
  margin: 0;
  font-size: 12px;
  color: #636363;
  letter-spacing: 0.03em;
}

.metric-value {
  margin: 8px 0 0;
  font-size: 30px;
  line-height: 1;
  font-weight: 700;
  color: #171717;
}

.metric-unit {
  margin: 2px 0 0;
  font-size: 12px;
  color: #6b6b6b;
}

.metric-progress {
  margin-top: 10px;
}

.metric-desc {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.45;
  color: #555555;
}

.portrait-panel {
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background:
    linear-gradient(150deg, rgba(255, 255, 255, 0.9), rgba(241, 241, 241, 0.82)),
    radial-gradient(32rem 14rem at 0% 0%, rgba(255, 255, 255, 0.52), rgba(255, 255, 255, 0));
  padding: 14px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.08);
}

.portrait-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.portrait-item {
  border-radius: 14px;
  border: 1px solid rgba(16, 16, 16, 0.12);
  background: rgba(255, 255, 255, 0.78);
  padding: 12px;
  min-height: 108px;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.06);
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.portrait-item:hover {
  transform: translateY(-1px);
  border-color: rgba(16, 16, 16, 0.22);
  box-shadow: 0 12px 20px rgba(0, 0, 0, 0.1);
}

.portrait-label {
  margin: 0;
  font-size: 12px;
  color: #666666;
}

.portrait-value {
  margin: 9px 0 0;
  font-size: 24px;
  line-height: 1.16;
  font-weight: 700;
  color: #171717;
  word-break: break-word;
}

.portrait-note {
  margin: 8px 0 0;
  font-size: 12px;
  color: #595959;
}

.portrait-state {
  min-height: 126px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #4a4a4a;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: 12px;
}

.detail-card {
  border-radius: 18px;
  border: 1px solid var(--line-soft);
  background: rgba(255, 255, 255, 0.82);
  padding: 16px;
  box-shadow: 0 10px 22px rgba(0, 0, 0, 0.1);
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.section-title h3 {
  margin: 0;
  font-size: 18px;
  color: var(--text-main);
}

.section-title span {
  font-size: 12px;
  color: #707070;
}

.info-item {
  display: grid;
  grid-template-columns: 150px 1fr;
  align-items: center;
  gap: 10px;
  margin: 8px 0;
  padding: 11px 12px;
  border-radius: 12px;
  border: 1px solid rgba(18, 18, 18, 0.12);
  background: linear-gradient(146deg, rgba(255, 255, 255, 0.8), rgba(245, 245, 245, 0.72));
}

.info-item label {
  font-size: 14px;
  font-weight: 600;
  color: #4b4b4b;
}

.value {
  font-size: 22px;
  line-height: 1.35;
  color: #1e2f47;
  word-break: break-all;
}

.timeline-card {
  display: flex;
  flex-direction: column;
}

.timeline-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 16px 1fr;
  gap: 8px;
  align-items: flex-start;
}

.timeline-dot {
  width: 10px;
  height: 10px;
  margin-top: 5px;
  border-radius: 50%;
  background: linear-gradient(145deg, #131313, #5a5a5a);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.85), 0 0 0 3px rgba(16, 16, 16, 0.12);
}

.timeline-content {
  border-bottom: 1px dashed rgba(18, 18, 18, 0.14);
  padding-bottom: 8px;
}

.timeline-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #282828;
}

.timeline-time {
  margin: 4px 0 0;
  font-size: 12px;
  color: #646464;
}

.security-panel {
  margin-top: auto;
  border-radius: 12px;
  border: 1px solid rgba(18, 18, 18, 0.14);
  background: rgba(255, 255, 255, 0.72);
  padding: 12px;
}

.security-head {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 700;
  color: #2b2b2b;
}

.security-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.security-list li {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #4c4c4c;
}

.security-list li.done {
  color: #1e5f3b;
}

.check-icon {
  min-width: 36px;
  text-align: center;
  font-size: 11px;
  font-weight: 700;
  color: #8c8c8c;
  border-radius: 999px;
  border: 1px solid rgba(18, 18, 18, 0.16);
  padding: 2px 6px;
  background: rgba(255, 255, 255, 0.7);
}

.security-list li.done .check-icon {
  color: #1c6b42;
  border-color: rgba(28, 107, 66, 0.34);
  background: rgba(28, 107, 66, 0.1);
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 10px;
}

.edit-tips {
  margin-top: 10px;
  font-size: 13px;
  color: #5f779a;
  background: rgba(59, 130, 246, 0.08);
  border: 1px solid rgba(59, 130, 246, 0.15);
  border-radius: 10px;
  padding: 10px 12px;
  line-height: 1.55;
}

.drawer-footer {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.state-block {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 18px;
  min-height: 220px;
}

.error-state {
  color: #f56c6c;
}

.loading-icon {
  font-size: 28px;
  color: #409eff;
  animation: rotate 1s linear infinite;
}

.error-icon,
.empty-icon {
  font-size: 28px;
}

.empty-icon {
  color: #909399;
}

:deep(.el-progress-bar__outer) {
  background-color: rgba(17, 17, 17, 0.12);
}

:deep(.el-progress-bar__inner) {
  background: linear-gradient(135deg, #111111, #666666);
}

:deep(.el-button--primary.is-plain) {
  background: rgba(255, 255, 255, 0.74);
  border-color: rgba(16, 16, 16, 0.24);
  color: #121212;
}

:deep(.el-button--primary.is-plain:hover) {
  border-color: rgba(16, 16, 16, 0.42);
  background: rgba(255, 255, 255, 0.9);
}

@keyframes rotate {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1200px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .portrait-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .user-info-container {
    padding: 18px 8px 24px;
  }

  .user-info-card {
    width: min(1120px, 100%);
    padding: 16px;
    border-radius: 16px;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .title-wrap h2 {
    font-size: 34px;
  }

  .title-wrap p {
    font-size: 16px;
  }

  .header-actions {
    width: 100%;
    align-items: flex-start;
  }

  .action-buttons {
    justify-content: flex-start;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }

  .metric-card {
    min-height: 0;
  }

  .portrait-grid {
    grid-template-columns: 1fr;
  }

  .info-item {
    grid-template-columns: 1fr;
    gap: 4px;
  }

  .value {
    font-size: 18px;
  }
}
</style>
