<template>
  <section class="home-overview-page">
    <ChartsBody
      :visible="true"
      @platform-click="onPlatformClick"
      @category-click="onCategoryClick"
      @province-click="onProvinceClick"
    />
  </section>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import ChartsBody from '@/components/HomeCharts/ChartsBody.vue';

const router = useRouter();

function onPlatformClick(platformId: number) {
  if (!Number.isFinite(platformId) || platformId <= 0) return;
  void router.push({
    name: 'HomePlatform',
    query: {
      platformId: String(platformId),
    },
  });
}

function onCategoryClick(categoryId: number, categoryName: string, platformId?: number) {
  const query: Record<string, string> = {};
  if (Number.isFinite(categoryId) && categoryId > 0) {
    query.categoryId = String(categoryId);
  }
  if (categoryName?.trim()) {
    query.categoryName = categoryName.trim();
  }
  if (Number.isFinite(platformId) && Number(platformId) > 0) {
    query.platformId = String(platformId);
  }
  void router.push({
    name: 'HomeCategory',
    query,
  });
}

function onProvinceClick(provinceId: number, provinceName: string, platformId?: number) {
  const query: Record<string, string> = {};
  if (Number.isFinite(provinceId) && provinceId > 0) {
    query.provinceId = String(provinceId);
  }
  if (provinceName?.trim()) {
    query.provinceName = provinceName.trim();
  }
  if (Number.isFinite(platformId) && Number(platformId) > 0) {
    query.platformId = String(platformId);
  }
  void router.push({
    name: 'HomeProvince',
    query,
  });
}
</script>

<style scoped>
.home-overview-page {
  display: flex;
  flex-direction: column;
  gap: 0;
  height: calc(100dvh - 68px);
  min-height: 660px;
  min-width: 0;
}

@media (max-width: 768px) {
  .home-overview-page {
    height: auto;
    min-height: 0;
  }
}
</style>
