import { getPlatformList, type PlatformListItem } from '@/api/platform/platform';

export interface PlatformMeta {
  id: number;
  code: string;
  name: string;
  status: number;
}

export const DEFAULT_PLATFORM_LIST: PlatformMeta[] = [
  { id: 1, code: 'douyin', name: '抖音', status: 1 },
  { id: 2, code: 'tmall', name: '天猫', status: 1 },
  { id: 3, code: 'jd', name: '京东', status: 1 },
  { id: 4, code: 'pdd', name: '拼多多', status: 1 },
  { id: 5, code: 'dewu', name: '得物', status: 1 },
];

let platformCache: PlatformMeta[] | null = null;
let loadingPromise: Promise<PlatformMeta[]> | null = null;

function normalizePlatform(list: PlatformListItem[]): PlatformMeta[] {
  return list
    .map((item) => ({
      id: Number(item.id),
      code: String(item.code || ''),
      name: String(item.name || ''),
      status: Number(item.status ?? 1),
    }))
    .filter((item) => item.id > 0 && item.name);
}

export async function loadPlatformList(force = false): Promise<PlatformMeta[]> {
  if (!force && platformCache?.length) return platformCache;
  if (!force && loadingPromise) return loadingPromise;

  loadingPromise = (async () => {
    try {
      const res = await getPlatformList();
      if (res.code !== 200) throw new Error(res.msg || 'load platform list failed');
      const list = normalizePlatform(res.data || []);
      platformCache = list.length ? list : DEFAULT_PLATFORM_LIST;
      return platformCache;
    } catch {
      platformCache = DEFAULT_PLATFORM_LIST;
      return platformCache;
    } finally {
      loadingPromise = null;
    }
  })();

  return loadingPromise;
}

export function getPlatformMapById(list?: PlatformMeta[]) {
  const source = list?.length ? list : platformCache?.length ? platformCache : DEFAULT_PLATFORM_LIST;
  return new Map(source.map((item) => [item.id, item]));
}

export function getPlatformNameById(id?: number | null, list?: PlatformMeta[]): string {
  if (!id) return '';
  return getPlatformMapById(list).get(id)?.name ?? '';
}
