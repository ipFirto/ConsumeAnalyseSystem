<template>
  <div class="home-page" :class="{ 'desktop-collapsed': collapsed }">
    <main class="content">
      <section class="content-stage">
        <router-view />
      </section>
    </main>

    <aside class="sidebar" :class="{ collapsed }">
      <button class="toggle-btn" @click="toggleSidebar">
        {{ collapsed ? '<' : '>' }}
      </button>

      <div class="sidebar-inner" v-show="!collapsed">
        <nav ref="mainNavRef" class="main-nav">
          <ul>
            <li v-for="item in navItems" :key="item.routeName" class="nav-item">
              <a
                href="#"
                class="nav-link"
                :class="{ active: isActive(item.routeName) }"
                @click.prevent="goNav(item.routeName)"
              >
                <el-icon class="nav-icon">
                  <component :is="item.icon" />
                </el-icon>
                <span class="nav-text">{{ item.label }}</span>
              </a>
            </li>
          </ul>
        </nav>

        <div class="sidebar-user-wrap">
          <div class="user-dropdown-container">
            <div v-if="userInfo" class="user-dropdown" @click.stop="toggleDropdown">
              <el-icon class="user-icon"><UserFilled /></el-icon>
              <span class="user-name">{{ userInfo.userName }}</span>
              <el-icon class="arrow" :class="{ open: dropdownVisible }"><ArrowDown /></el-icon>

              <div v-if="dropdownVisible" class="dropdown-menu">
                <div class="dropdown-item" @click.stop="openUserInfo">{{ text.userInfo }}</div>
                <div class="dropdown-item" @click.stop="openResetPassword">{{ text.resetPassword }}</div>
                <div class="dropdown-item" @click.stop="logout">{{ text.logout }}</div>
                <div class="dropdown-item danger" @click.stop="deleteAccount">{{ text.deleteAccount }}</div>
              </div>
            </div>
            <div v-else class="loading"><span>{{ text.loading }}</span></div>
          </div>
        </div>
      </div>
    </aside>

    <ResetPassword v-model:visible="resetPasswordVisible" class="dialog-host" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import * as authApi from '@/api/modules/auth';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ArrowDown,
  Box,
  DataLine,
  Document,
  Grid,
  Histogram,
  House,
  UserFilled,
} from '@element-plus/icons-vue';

import ResetPassword from '@/components/user/ResetPassword.vue';

type NavItem = {
  routeName:
    | 'HomeIndex'
    | 'HomeOrderDetail'
    | 'HomeMockData'
    | 'HomeResourceSource'
    | 'HomeCart'
    | 'HomeMyOrders';
  label: string;
  icon: any;
};

const text = {
  loading: '加载中...',
  home: '首页',
  orderDetail: '订单详情',
  mockData: '模拟数据',
  resourceSource: '商品来源',
  cart: '购物车',
  myOrders: '我的订单',
  userInfo: '个人信息',
  resetPassword: '修改密码',
  logout: '退出登录',
  deleteAccount: '注销用户',
  fetchUserInfoFailed: '获取用户信息失败',
  logoutSuccess: '退出登录成功',
  deleteConfirm: '确定要注销您的账户吗？此操作不可恢复。',
  warning: '警告',
  confirm: '确定',
  cancel: '取消',
  deleteSuccess: '账户注销成功',
  deleteFailed: '账户注销失败',
};

const navItems: NavItem[] = [
  { routeName: 'HomeIndex', label: text.home, icon: House },
  { routeName: 'HomeOrderDetail', label: text.orderDetail, icon: Document },
  { routeName: 'HomeMockData', label: text.mockData, icon: Histogram },
  { routeName: 'HomeResourceSource', label: text.resourceSource, icon: Box },
  { routeName: 'HomeCart', label: text.cart, icon: Grid },
  { routeName: 'HomeMyOrders', label: text.myOrders, icon: DataLine },
];

const router = useRouter();
const route = useRoute();
const userInfo = ref<any>(null);
const dropdownVisible = ref(false);
const collapsed = ref(false);
const resetPasswordVisible = ref(false);
const mainNavRef = ref<HTMLElement | null>(null);

const activeRouteName = computed(() => String(route.name || 'HomeIndex'));
const homeScopeRouteNames = new Set<string>([
  'HomeIndex',
  'HomePlatform',
  'HomePlatformDate',
  'HomeCategory',
  'HomeCategoryInfo',
  'HomeProductSales',
  'HomeProvince',
  'HomeProvinceInsight',
  'HomeAllOrderDetail',
]);
function isActive(routeName: NavItem['routeName']) {
  if (routeName === 'HomeIndex') {
    return homeScopeRouteNames.has(activeRouteName.value);
  }
  return activeRouteName.value === routeName;
}

async function fetchUserInfo() {
  try {
    const info = await authApi.getUserInfo();
    userInfo.value = info.data.data;
  } catch (error) {
    ElMessage.error(text.fetchUserInfoFailed);
    console.error(`${text.fetchUserInfoFailed}:`, error);
  }
}

function goNav(routeName: NavItem['routeName']) {
  dropdownVisible.value = false;
  if (activeRouteName.value === routeName) return;
  void router.push({ name: routeName });
}

function logout() {
  dropdownVisible.value = false;
  localStorage.removeItem('token');
  void router.push('/login');
  ElMessage.success(text.logoutSuccess);
}

function deleteAccount() {
  dropdownVisible.value = false;
  ElMessageBox.confirm(text.deleteConfirm, text.warning, {
    confirmButtonText: text.confirm,
    cancelButtonText: text.cancel,
    type: 'warning',
  })
    .then(async () => {
      try {
        await authApi.deleteUser();
        localStorage.removeItem('token');
        void router.push('/login');
        ElMessage.success(text.deleteSuccess);
      } catch {
        ElMessage.error(text.deleteFailed);
      }
    })
    .catch(() => {});
}

function toggleSidebar() {
  collapsed.value = !collapsed.value;
}

function toggleDropdown() {
  dropdownVisible.value = !dropdownVisible.value;
}

function openUserInfo() {
  dropdownVisible.value = false;
  void router.push('/UserInfo');
}

function openResetPassword() {
  dropdownVisible.value = false;
  resetPasswordVisible.value = true;
}

function scrollActiveNavIntoView() {
  if (typeof window === 'undefined') return;
  if (window.innerWidth > 768) return;

  requestAnimationFrame(() => {
    const navRoot = mainNavRef.value;
    if (!navRoot) return;
    const activeLink = navRoot.querySelector('.nav-link.active') as HTMLElement | null;
    if (!activeLink) return;
    activeLink.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
  });
}

function handleClickOutside(event: MouseEvent) {
  const dropdown = document.querySelector('.user-dropdown');
  if (dropdown && !dropdown.contains(event.target as Node)) {
    dropdownVisible.value = false;
  }
}

onMounted(() => {
  void fetchUserInfo();
  document.addEventListener('click', handleClickOutside);
  scrollActiveNavIntoView();
});

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside);
});

watch(activeRouteName, () => {
  scrollActiveNavIntoView();
});
</script>

<style lang="scss" scoped>
.home-page {
  --desktop-sidebar-width: 248px;
  --desktop-sidebar-collapsed-width: 36px;
  min-height: 100dvh;
  height: auto;
  overflow-x: hidden;
  overflow-y: visible;
  position: relative;
  display: flex;
  color: var(--theme-text-main);
  background:
    radial-gradient(82rem 58rem at -16% -18%, rgba(255, 255, 255, 0.7), rgba(255, 255, 255, 0)),
    radial-gradient(78rem 52rem at 118% 128%, rgba(0, 0, 0, 0.15), rgba(0, 0, 0, 0)),
    linear-gradient(158deg, #fbfbfb 0%, #ececec 48%, #d9d9d9 100%);
}

.home-page::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(
    120deg,
    rgba(255, 255, 255, 0.12) 0%,
    rgba(255, 255, 255, 0) 22%,
    rgba(0, 0, 0, 0.04) 78%,
    rgba(0, 0, 0, 0.08) 100%
  );
  mix-blend-mode: soft-light;
}

.content {
  order: 2;
  flex: 1;
  width: 100%;
  padding: 18px 20px 16px;
  text-align: left;
  min-width: 0;
  overflow: visible;
  position: relative;
  z-index: 1;
}

.content-stage {
  padding: 8px 0 10px;
}

.sidebar {
  order: 1;
  flex-direction: row-reverse;
  background:
    linear-gradient(176deg, rgba(9, 9, 9, 0.98), rgba(24, 24, 24, 0.96) 42%, rgba(17, 17, 17, 0.98) 100%),
    radial-gradient(42rem 28rem at 6% -14%, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0));
  width: var(--desktop-sidebar-width);
  padding: 22px 14px 18px 10px;
  display: flex;
  align-items: stretch;
  border-right: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow:
    inset -1px 0 0 rgba(255, 255, 255, 0.08),
    inset 0 30px 60px rgba(255, 255, 255, 0.03),
    14px 0 28px rgba(0, 0, 0, 0.24);
  transition: width 0.35s ease, padding 0.35s ease;
  overflow: hidden;
  z-index: 10;
  position: fixed;
  left: 0;
  top: 0;
  height: 100dvh;
  align-self: flex-start;
}

.sidebar.collapsed {
  width: var(--desktop-sidebar-collapsed-width);
  padding: 0 4px 0 0;
}

.toggle-btn {
  height: calc(100dvh - 26px);
  width: 16px;
  margin-left: 6px;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  background: rgba(255, 255, 255, 0.17);
  color: #f5f5f5;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.toggle-btn:hover {
  background: rgba(255, 255, 255, 0.33);
  transition: 0.25s;
}

.sidebar-inner {
  width: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  flex: 1;
}

.main-nav {
  flex: 1;
  display: flex;
  min-height: 0;
}

.main-nav ul {
  list-style: none;
  padding: 6px 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  width: 100%;
}

.nav-item {
  margin-bottom: 10px;
  width: 100%;
}

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: rgba(236, 236, 236, 0.84);
  text-decoration: none;
  padding: 10px 12px 10px 11px;
  border-radius: 11px;
  transition: all 0.25s ease;
  cursor: pointer;
  text-align: left;
  border: 1px solid rgba(255, 255, 255, 0.08);
  position: relative;
}

.nav-link::before {
  content: '';
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  border-radius: 3px;
  background: linear-gradient(180deg, #ffffff, #9f9f9f);
  transition: height 0.22s ease;
}

.nav-link:hover {
  color: #ffffff;
  border-color: rgba(255, 255, 255, 0.3);
  background: linear-gradient(136deg, rgba(255, 255, 255, 0.15), rgba(255, 255, 255, 0.06));
  box-shadow: 0 12px 24px rgba(0, 0, 0, 0.26);
}

.nav-link:hover::before,
.nav-link.active::before {
  height: 66%;
}

.nav-link.active {
  color: #0f0f0f;
  border-color: rgba(255, 255, 255, 0.66);
  background: linear-gradient(136deg, rgba(255, 255, 255, 0.96), rgba(226, 226, 226, 0.84));
  box-shadow:
    0 10px 22px rgba(0, 0, 0, 0.28),
    inset 0 0 0 1px rgba(255, 255, 255, 0.36);
}

.nav-icon {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.92);
  transition: color 0.2s ease;
}

.nav-link.active .nav-icon {
  color: #111111;
}

.nav-text {
  letter-spacing: 0.02em;
}

.sidebar-user-wrap {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.user-dropdown-container {
  position: relative;
  z-index: 1000;
  width: 100%;
}

.user-dropdown {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(0, 0, 0, 0.24);
  cursor: pointer;
  transition: all 0.22s ease;

  &:hover {
    border-color: rgba(255, 255, 255, 0.42);
    background: rgba(255, 255, 255, 0.16);
    box-shadow: 0 12px 24px rgba(0, 0, 0, 0.3);
  }
}

.user-icon {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.88);
  flex-shrink: 0;
}

.user-name {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.94);
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.82);
  transition: transform 0.22s ease;
  flex-shrink: 0;
}

.arrow.open {
  transform: rotate(180deg);
}

.dropdown-menu {
  position: absolute;
  bottom: calc(100% + 10px);
  left: 0;
  right: 0;
  width: auto;
  background: rgba(15, 15, 15, 0.98);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  box-shadow: 0 16px 30px rgba(0, 0, 0, 0.4);
  overflow: hidden;
  z-index: 1001;
}

.dropdown-item {
  padding: 11px 14px;
  font-size: 14px;
  color: rgba(245, 245, 245, 0.9);
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    background-color: rgba(255, 255, 255, 0.12);
    color: #ffffff;
  }

  &.danger {
    color: #ff8585;

    &:hover {
      background-color: rgba(255, 103, 103, 0.16);
      color: #ff9494;
    }
  }
}

.loading {
  font-size: 14px;
  color: rgba(245, 245, 245, 0.74);
  padding: 9px 12px;
  border-radius: 10px;
  border: 1px dashed rgba(255, 255, 255, 0.24);
  text-align: center;
}

.dialog-host {
  position: absolute;
  width: 0;
  height: 0;
  overflow: visible;
}

:deep(.el-button--primary) {
  border-radius: 10px;
  background: linear-gradient(135deg, #191919, #444444);
  border-color: rgba(20, 20, 20, 0.9);
}

:deep(.el-button--primary:hover) {
  background: linear-gradient(135deg, #101010, #616161);
  border-color: rgba(20, 20, 20, 0.95);
}

@media (max-width: 1100px) {
  .home-page {
    --desktop-sidebar-width: 208px;
  }

  .sidebar {
    width: var(--desktop-sidebar-width);
  }
}

@media (min-width: 769px) {
  .home-page {
    padding-left: var(--desktop-sidebar-width);
  }

  .home-page.desktop-collapsed {
    padding-left: var(--desktop-sidebar-collapsed-width);
  }
}

@media (max-width: 768px) {
  .home-page {
    padding-left: 0;
    flex-direction: column;
    height: auto;
    min-height: 100dvh;
    padding-bottom: calc(88px + env(safe-area-inset-bottom));
  }

  .sidebar {
    width: auto;
    padding: 8px 10px;
    border: 1px solid rgba(255, 255, 255, 0.2);
    border-right: 1px solid rgba(255, 255, 255, 0.2);
    border-bottom: 1px solid rgba(255, 255, 255, 0.2);
    border-radius: 16px;
    flex-direction: column;
    position: fixed;
    left: 8px;
    right: 8px;
    bottom: calc(8px + env(safe-area-inset-bottom));
    top: auto;
    height: auto;
    align-self: auto;
    backdrop-filter: blur(10px);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 14px 28px rgba(0, 0, 0, 0.36);
    z-index: 1200;
    overflow: visible;
  }

  .sidebar.collapsed {
    width: auto;
    padding: 8px 10px;
  }

  .toggle-btn {
    display: none;
  }

  .sidebar-inner {
    display: block;
  }

  .main-nav {
    width: 100%;
    overflow: visible;
  }

  .main-nav ul {
    flex-direction: row;
    flex-wrap: nowrap;
    gap: 6px;
    min-width: 0;
    width: 100%;
    padding: 0;
    align-items: stretch;
  }

  .sidebar-user-wrap {
    margin: 0;
    padding: 0;
    border: 0;
    position: absolute;
    right: 6px;
    bottom: calc(100% + 8px);
    z-index: 1300;
  }

  .nav-item {
    margin: 0;
    width: auto;
    flex: 1;
    min-width: 0;
  }

  .nav-link {
    justify-content: center;
    flex-direction: column;
    gap: 4px;
    text-align: center;
    padding: 7px 4px 6px;
    border-radius: 10px;
    white-space: nowrap;
    min-height: 52px;
  }

  .nav-link::before {
    display: none;
  }

  .nav-icon {
    font-size: 15px;
  }

  .nav-text {
    font-size: 11px;
    line-height: 1.2;
    letter-spacing: 0;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .user-dropdown-container {
    width: auto;
  }

  .user-dropdown {
    width: auto;
    min-width: 118px;
    max-width: min(64vw, 220px);
    padding: 7px 10px;
    border-radius: 999px;
    gap: 8px;
    background: rgba(28, 28, 28, 0.94);
    border-color: rgba(255, 255, 255, 0.24);
    box-shadow: 0 10px 22px rgba(0, 0, 0, 0.34);
  }

  .user-icon {
    font-size: 13px;
  }

  .user-name {
    font-size: 12px;
    max-width: calc(min(64vw, 220px) - 52px);
  }

  .arrow {
    font-size: 11px;
  }

  .dropdown-menu {
    left: auto;
    right: 0;
    width: 178px;
    bottom: calc(100% + 8px);
    border-radius: 10px;
  }

  .dropdown-item {
    padding: 10px 12px;
    font-size: 13px;
  }

  .loading {
    padding: 8px 10px;
    border-radius: 999px;
    font-size: 12px;
  }

  .content {
    padding: 10px 10px calc(92px + env(safe-area-inset-bottom));
  }

  .content-stage {
    padding: 4px 0 6px;
  }
}

@media (max-width: 560px) {
  .sidebar {
    left: 6px;
    right: 6px;
    bottom: calc(6px + env(safe-area-inset-bottom));
    padding: 7px 8px;
  }

  .sidebar.collapsed {
    padding: 7px 8px;
  }

  .nav-link {
    min-height: 50px;
    padding: 7px 2px 6px;
  }

  .nav-text {
    font-size: 10px;
  }

  .sidebar-user-wrap {
    right: 4px;
    bottom: calc(100% + 7px);
  }

  .user-dropdown {
    min-width: 108px;
    max-width: min(70vw, 190px);
    padding: 6px 9px;
  }

  .user-name {
    max-width: calc(min(70vw, 190px) - 50px);
  }
}
</style>


