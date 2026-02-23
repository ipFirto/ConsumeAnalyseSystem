import request from '@/api/request';

export interface PlatformListItem {
  id: number;
  code: string;
  name: string;
  status: number;
  created_at?: string;
}

export interface PlatformRecord {
  product_id?: number;
  category?: string;
  pf_name: string;
  amount: number;
  user_email: string;
  pr_type: number;
  co_order_no: string;
  user_name: string;
  co_remark: string;
  pr_id: number;
  co_id: number;
  p_brand: string;
  co_created_at: string;
  product_name: string;
  c_code: string;
  u_phone: string;
  user_id: number;
  pr_name: string;
  c_id: number;
  c_name: string;
  u_status: number;
}

export interface PlatformApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

interface GetPlatformDataOptions {
  forceRefresh?: boolean;
  cacheTtlMs?: number;
}

interface PlatformDataCacheEntry {
  expiresAt: number;
  payload: PlatformApiResult<PlatformRecord[]>;
}

const DEFAULT_PLATFORM_DATA_CACHE_TTL_MS = 2 * 60 * 1000;
const platformDataCache = new Map<number, PlatformDataCacheEntry>();
const platformDataInFlight = new Map<number, Promise<PlatformApiResult<PlatformRecord[]>>>();

function readPlatformDataCache(platformId: number) {
  const entry = platformDataCache.get(platformId);
  if (!entry) return null;
  if (Date.now() > entry.expiresAt) {
    platformDataCache.delete(platformId);
    return null;
  }
  return entry.payload;
}

export function clearPlatformDataCache(platformId?: number) {
  if (typeof platformId === 'number' && Number.isFinite(platformId) && platformId > 0) {
    platformDataCache.delete(platformId);
    platformDataInFlight.delete(platformId);
    return;
  }
  platformDataCache.clear();
  platformDataInFlight.clear();
}

export async function getPlatformData(
  platformId: number,
  options: GetPlatformDataOptions = {}
): Promise<PlatformApiResult<PlatformRecord[]>> {
  const id = Number(platformId);
  if (!Number.isFinite(id) || id <= 0) {
    throw new Error('invalid platformId');
  }

  const forceRefresh = options.forceRefresh === true;
  const ttlMs = Math.max(0, Number(options.cacheTtlMs ?? DEFAULT_PLATFORM_DATA_CACHE_TTL_MS));

  if (!forceRefresh && ttlMs > 0) {
    const cached = readPlatformDataCache(id);
    if (cached) return cached;
  }

  if (!forceRefresh) {
    const pending = platformDataInFlight.get(id);
    if (pending) return pending;
  }

  const req = request
    .get<PlatformApiResult<PlatformRecord[]>>(`/platform/${id}`)
    .then((res) => {
      const payload = res.data;
      if (ttlMs > 0 && payload?.code === 200) {
        platformDataCache.set(id, {
          expiresAt: Date.now() + ttlMs,
          payload,
        });
      }
      return payload;
    })
    .finally(() => {
      platformDataInFlight.delete(id);
    });

  platformDataInFlight.set(id, req);
  return req;
}

export async function getPlatformList(): Promise<PlatformApiResult<PlatformListItem[]>> {
  const res = await request.get<PlatformApiResult<PlatformListItem[]>>('/platform/list');
  return res.data;
}
