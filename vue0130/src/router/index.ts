import { createRouter, createWebHistory } from 'vue-router';
import Home from '../views/Home.vue';
import Login from '../views/Login.vue';
import UserInfo from '@/components/user/UserInfo.vue';
import * as authApi from '@/api/modules/auth';

import HomeOverview from '@/views/HomeOverview.vue';
import MockData from '@/views/MockData.vue';
import OrderDetail from '@/components/user/OrderDetail.vue';
import ResourceProducts from '@/views/ResourceProducts.vue';
import CartItems from '@/views/CartItems.vue';
import MyOrders from '@/views/MyOrders.vue';

import Platform from '@/components/platform/Platform.vue';
import Category from '@/components/category/Category.vue';
import Province from '@/components/province/Province.vue';
import PlatformDataInfo from '@/views/PlatformDataInfo.vue';
import AllOrderDetailPage from '@/views/AllOrderDetailPage.vue';
import CategoryInfo from '@/views/CategoryInfo.vue';
import ProductSalesInfo from '@/views/ProductSalesInfo.vue';
import ProvinceInsight from '@/views/ProvinceInsight.vue';

function toPositiveNumber(raw: unknown): number | null {
  const value = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function toOptionalString(raw: unknown): string | null {
  const value = Array.isArray(raw) ? raw[0] : raw;
  if (value == null) return null;
  const text = String(value).trim();
  return text || null;
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/home',
      component: Home,
      redirect: { name: 'HomeIndex' },
      children: [
        {
          path: 'index',
          name: 'HomeIndex',
          component: HomeOverview,
        },
        {
          path: 'order-detail',
          name: 'HomeOrderDetail',
          component: OrderDetail,
        },
        {
          path: 'mock-data',
          name: 'HomeMockData',
          component: MockData,
        },
        {
          path: 'resource-source',
          name: 'HomeResourceSource',
          component: ResourceProducts,
        },
        {
          path: 'cart',
          name: 'HomeCart',
          component: CartItems,
        },
        {
          path: 'my-orders',
          name: 'HomeMyOrders',
          component: MyOrders,
        },
        {
          path: 'platform',
          name: 'HomePlatform',
          component: Platform,
          props: (route) => ({
            platformId: toPositiveNumber(route.query.platformId),
          }),
        },
        {
          path: 'platform-date',
          name: 'HomePlatformDate',
          component: PlatformDataInfo,
        },
        {
          path: 'category',
          name: 'HomeCategory',
          component: Category,
          props: (route) => ({
            categoryId: toPositiveNumber(route.query.categoryId),
            categoryName: toOptionalString(route.query.categoryName),
            platformId: toPositiveNumber(route.query.platformId),
          }),
        },
        {
          path: 'category-info',
          name: 'HomeCategoryInfo',
          component: CategoryInfo,
        },
        {
          path: 'product-sales',
          name: 'HomeProductSales',
          component: ProductSalesInfo,
        },
        {
          path: 'province',
          name: 'HomeProvince',
          component: Province,
          props: (route) => ({
            provinceId: toPositiveNumber(route.query.provinceId),
            platformId: toPositiveNumber(route.query.platformId),
          }),
        },
        {
          path: 'province-insight',
          name: 'HomeProvinceInsight',
          component: ProvinceInsight,
        },
        {
          path: 'all-order-detail',
          name: 'HomeAllOrderDetail',
          component: AllOrderDetailPage,
        },
      ],
    },
    {
      path: '/home/allOrderDetail',
      redirect: (to) => ({
        name: 'HomeAllOrderDetail',
        query: to.query,
      }),
    },
    {
      path: '/allOrderDetail',
      redirect: (to) => ({
        name: 'HomeAllOrderDetail',
        query: to.query,
      }),
    },
    {
      path: '/login',
      name: 'Login',
      component: Login,
    },
    {
      path: '/UserInfo',
      name: 'UserInfo',
      component: UserInfo,
    },
    {
      path: '/province',
      redirect: (to) => ({
        name: 'HomeProvince',
        query: to.query,
      }),
    },
    {
      path: '/platform',
      redirect: (to) => ({
        name: 'HomePlatformDate',
        query: to.query,
      }),
    },
    {
      path: '/category',
      redirect: (to) => ({
        name: 'HomeCategory',
        query: to.query,
      }),
    },
    {
      path: '/categoryInfo',
      redirect: (to) => ({
        name: 'HomeCategoryInfo',
        query: to.query,
      }),
    },
    {
      path: '/productSalesInfo',
      redirect: (to) => ({
        name: 'HomeProductSales',
        query: to.query,
      }),
    },
    {
      path: '/provinceInsight',
      redirect: (to) => ({
        name: 'HomeProvinceInsight',
        query: to.query,
      }),
    },
    {
      path: '/resourceProducts',
      redirect: { name: 'HomeResourceSource' },
    },
    {
      path: '/cartItems',
      redirect: { name: 'HomeCart' },
    },
    {
      path: '/myOrders',
      redirect: { name: 'HomeMyOrders' },
    },
  ],
});

router.beforeEach(async (to) => {
  const token = localStorage.getItem('token');

  if (to.path === '/home' && to.query?.panel != null) {
    const panelRaw = Array.isArray(to.query.panel) ? to.query.panel[0] : to.query.panel;
    const panel = String(panelRaw || '').trim();

    if (panel === 'OrderDetail') return { name: 'HomeOrderDetail' };
    if (panel === 'Resources') return { name: 'HomeResourceSource' };
    if (panel === 'ChartsBody') return { name: 'HomeIndex' };
    if (panel === 'Platform') return { name: 'HomePlatform', query: { ...to.query } };
    if (panel === 'Category') return { name: 'HomeCategory', query: { ...to.query } };
    if (panel === 'Province') return { name: 'HomeProvince', query: { ...to.query } };
  }

  if (!token && to.path !== '/login') {
    return '/login';
  }

  if (token) {
    const ok = await authApi.validateToken();
    if (!ok) {
      localStorage.removeItem('token');
      return '/login';
    }
    if (to.path === '/login') return '/home';
  }

  return true;
});

export default router;
