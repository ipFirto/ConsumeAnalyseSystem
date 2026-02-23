import request from '@/api/request';
import type { CategoryPieItem, ProvinceOrderCountRow } from '@/api/modules/chart';
import type { MqOrderRecord } from '@/api/modules/mq';

export interface DashboardApiResponse<T> {
  code: number;
  message: string;
  serverTime: string;
  cursor: string;
  data: T;
}

export interface DashboardEventPayload {
  platformIds?: number[];
  bar?: number[];
  smooth?: ProvinceOrderCountRow[];
  linePlatformId?: number;
  line?: CategoryPieItem[];
  lineByPlatform?: Record<string, CategoryPieItem[]>;
  recentOrders?: MqOrderRecord[];
  reason?: string;
  ts?: string;
}

export interface HomeSnapshotPayload extends DashboardEventPayload {
  generatedAt?: string;
  source?: string;
}

export interface DashboardSnapshotData {
  scope: string;
  snapshot: HomeSnapshotPayload;
}

export interface DashboardEvent {
  cursor: number;
  type: string;
  topic: string;
  op: string;
  ts: string;
  payload: DashboardEventPayload;
}

export interface DashboardStreamOptions {
  topics?: string;
  token?: string;
  onEvent: (evt: DashboardEvent) => void;
  onOpen?: () => void;
  onError?: (err: Event) => void;
}

function parseEventPayload(raw: string): DashboardEvent | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (!parsed || typeof parsed !== 'object') return null;
    return parsed as DashboardEvent;
  } catch {
    return null;
  }
}

function buildStreamUrl(topics: string, token?: string) {
  // Reuse axios URL resolving so SSE path stays consistent with request.baseURL
  // (important when baseURL="/api" and Vite/Nginx proxy rewrites requests).
  const built = request.getUri({
    url: '/api/v1/dashboard/stream',
    params: {
      topics: topics || 'home',
      token: token || undefined,
    },
  });

  try {
    return new URL(built, window.location.origin).toString();
  } catch {
    const fallback = new URL('/api/v1/dashboard/stream', window.location.origin);
    fallback.searchParams.set('topics', topics || 'home');
    if (token) {
      fallback.searchParams.set('token', token);
    }
    return fallback.toString();
  }
}

export async function getDashboardSnapshot(params?: {
  scope?: string;
  platformId?: number;
}): Promise<DashboardApiResponse<DashboardSnapshotData>> {
  const res = await request.get<DashboardApiResponse<DashboardSnapshotData>>('/api/v1/dashboard/snapshot', {
    params: {
      scope: params?.scope || 'home',
      platformId: params?.platformId,
    },
  });
  return res.data;
}

export function createDashboardStream(options: DashboardStreamOptions): EventSource {
  const source = new EventSource(buildStreamUrl(options.topics || 'home', options.token));
  const handle = (evt: MessageEvent<string>) => {
    const parsed = parseEventPayload(evt.data);
    if (!parsed) return;
    options.onEvent(parsed);
  };

  source.addEventListener('patch', handle as EventListener);
  source.addEventListener('hello', handle as EventListener);
  source.addEventListener('heartbeat', handle as EventListener);
  source.onopen = () => options.onOpen?.();
  source.onerror = (err) => options.onError?.(err);

  return source;
}
