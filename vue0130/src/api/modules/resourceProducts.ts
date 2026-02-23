import { getPlatformData, type PlatformRecord } from '@/api/platform/platform';
import { loadPlatformList, type PlatformMeta } from '@/constants/platform';
import { getProductListByPlatform, type ProductRecord } from '@/api/modules/product';

export interface ResourceProductItem {
  key: string;
  productId: number;
  productName: string;
  platformId: number;
  platformName: string;
  category: string;
  itemAmount: number;
  stockRemaining: number;
  salesCount: number;
  totalAmount: number;
  latestOrderAt: string;
}

export interface ResourceProductDataset {
  products: ResourceProductItem[];
  platforms: PlatformMeta[];
  categories: string[];
}

interface GetResourceProductDatasetOptions {
  forceRefresh?: boolean;
  cacheTtlMs?: number;
}

interface CacheSnapshot {
  expiresAt: number;
  value: ResourceProductDataset;
}

interface OrderProductStat {
  salesCount: number;
  totalAmount: number;
  latestOrderAt: string;
}

const DEFAULT_CACHE_TTL_MS = 2 * 60 * 1000;
const DEFAULT_CATEGORY = 'Uncategorized';

let datasetCache: CacheSnapshot | null = null;
let inFlight: Promise<ResourceProductDataset> | null = null;

function toSafeText(raw: unknown) {
  return String(raw ?? '').trim();
}

function toSafeNumber(raw: unknown) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : 0;
}

function toSafeTimestamp(raw: string) {
  const value = new Date(raw).getTime();
  return Number.isFinite(value) ? value : 0;
}

function getProductKey(platformId: number, productId: number, productName: string) {
  if (productId > 0) return `${platformId}::pid::${productId}`;
  return `${platformId}::name::${productName}`;
}

function toOrderProductId(row: PlatformRecord) {
  const maybeWithProductId = row as PlatformRecord & { product_id?: unknown; productId?: unknown };
  return Math.floor(toSafeNumber(maybeWithProductId.product_id ?? maybeWithProductId.productId));
}

async function getOrdersByPlatform(platformId: number): Promise<PlatformRecord[]> {
  try {
    const res = await getPlatformData(platformId);
    if (res.code !== 200 || !Array.isArray(res.data)) return [];
    return res.data;
  } catch {
    return [];
  }
}

async function getProductsByPlatform(platformId: number): Promise<ProductRecord[]> {
  try {
    const res = await getProductListByPlatform({
      platformId,
      status: 1,
      limit: 5000,
      offset: 0,
    });
    if (res.code !== 200 || !Array.isArray(res.data)) return [];
    return res.data;
  } catch {
    return [];
  }
}

function buildProductIdByNameMap(products: ProductRecord[]) {
  const map = new Map<string, number>();

  for (const item of products) {
    const id = toSafeNumber(item.id);
    const platformId = toSafeNumber(item.platform_id);
    const productName = toSafeText(item.product_name);
    if (id > 0 && platformId > 0 && productName) {
      map.set(`${platformId}::${productName}`, id);
    }
  }

  return map;
}

function buildOrderStats(
  platforms: PlatformMeta[],
  rowsByPlatform: Map<number, PlatformRecord[]>,
  productIdByName: Map<string, number>
) {
  const stats = new Map<string, OrderProductStat>();

  for (const platform of platforms) {
    const rows = rowsByPlatform.get(platform.id) || [];
    for (const row of rows) {
      const productName = toSafeText(row.product_name);
      if (!productName) continue;

      const rawProductId = toOrderProductId(row);
      const productId =
        rawProductId > 0 ? rawProductId : toSafeNumber(productIdByName.get(`${platform.id}::${productName}`));
      const amount = toSafeNumber(row.amount);
      const latestOrderAt = toSafeText(row.co_created_at);
      const key = getProductKey(platform.id, productId, productName);
      const current = stats.get(key);

      if (!current) {
        stats.set(key, {
          salesCount: 1,
          totalAmount: amount,
          latestOrderAt,
        });
        continue;
      }

      current.salesCount += 1;
      current.totalAmount += amount;

      if (toSafeTimestamp(latestOrderAt) > toSafeTimestamp(current.latestOrderAt)) {
        current.latestOrderAt = latestOrderAt;
      }
    }
  }

  return stats;
}

function buildDataset(
  platforms: PlatformMeta[],
  productsByPlatform: Map<number, ProductRecord[]>,
  orderStats: Map<string, OrderProductStat>
) {
  const categorySet = new Set<string>();
  const products: ResourceProductItem[] = [];

  for (const platform of platforms) {
    const source = productsByPlatform.get(platform.id) || [];

    for (const row of source) {
      const productId = toSafeNumber(row.id);
      const productName = toSafeText(row.product_name);
      if (productId <= 0 || !productName) continue;

      const key = getProductKey(platform.id, productId, productName);
      const stat = orderStats.get(key);
      const category = toSafeText(row.category) || DEFAULT_CATEGORY;

      categorySet.add(category);
      products.push({
        key,
        productId,
        productName,
        platformId: platform.id,
        platformName: toSafeText(row.platform_name) || platform.name,
        category,
        itemAmount: toSafeNumber(row.amount),
        stockRemaining: Math.max(0, Math.floor(toSafeNumber(row.stock))),
        salesCount: stat?.salesCount ?? 0,
        totalAmount: stat?.totalAmount ?? 0,
        latestOrderAt: stat?.latestOrderAt ?? '',
      });
    }
  }

  products.sort((a, b) => {
    if (b.salesCount !== a.salesCount) return b.salesCount - a.salesCount;
    if (b.totalAmount !== a.totalAmount) return b.totalAmount - a.totalAmount;
    const timestampGap = toSafeTimestamp(b.latestOrderAt) - toSafeTimestamp(a.latestOrderAt);
    if (timestampGap !== 0) return timestampGap;
    return a.productName.localeCompare(b.productName, 'zh-CN');
  });

  const categories = Array.from(categorySet).sort((a, b) => a.localeCompare(b, 'zh-CN'));
  return { products, categories };
}

export async function getResourceProductDataset(
  options: GetResourceProductDatasetOptions = {}
): Promise<ResourceProductDataset> {
  const forceRefresh = options.forceRefresh === true;
  const ttlMs = Math.max(0, Number(options.cacheTtlMs ?? DEFAULT_CACHE_TTL_MS));

  if (!forceRefresh && datasetCache && Date.now() < datasetCache.expiresAt) {
    return datasetCache.value;
  }

  if (!forceRefresh && inFlight) {
    return inFlight;
  }

  inFlight = (async () => {
    const platforms = await loadPlatformList(forceRefresh);
    const dataList = await Promise.all(platforms.map((item) => getOrdersByPlatform(item.id)));
    const productListByPlatform = await Promise.all(platforms.map((item) => getProductsByPlatform(item.id)));
    const rowsByPlatform = new Map<number, PlatformRecord[]>();
    const productsByPlatform = new Map<number, ProductRecord[]>();
    const productIdByName = new Map<string, number>();

    platforms.forEach((item, index) => {
      const orderRows = dataList[index] || [];
      const productRows = productListByPlatform[index] || [];

      rowsByPlatform.set(item.id, orderRows);
      productsByPlatform.set(item.id, productRows);

      const onePlatformMap = buildProductIdByNameMap(productRows);
      onePlatformMap.forEach((id, key) => {
        productIdByName.set(key, id);
      });
    });

    const orderStats = buildOrderStats(platforms, rowsByPlatform, productIdByName);
    const summary = buildDataset(platforms, productsByPlatform, orderStats);
    const dataset: ResourceProductDataset = {
      products: summary.products,
      platforms,
      categories: summary.categories,
    };

    if (ttlMs > 0) {
      datasetCache = {
        expiresAt: Date.now() + ttlMs,
        value: dataset,
      };
    }

    return dataset;
  })().finally(() => {
    inFlight = null;
  });

  return inFlight;
}
