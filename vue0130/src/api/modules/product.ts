import request from '@/api/request';

export interface ProductRecord {
  id: number;
  platform_id: number;
  brand: string;
  product_name: string;
  platform_name: string;
  category: string;
  amount: number;
  stock: number;
  status: number;
  created_at: string;
  updated_at: string;
}

export interface ProductPlatformOption {
  id: number;
  name: string;
}

export interface ProductApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export interface ProductListParams {
  platformId: number;
  productId?: number;
  status?: number;
  limit?: number;
  offset?: number;
}

export interface ProductDeleteParams {
  productId: number;
  status?: number;
}

export interface ProductUpdateParams {
  productId: number;
  productName?: string;
  brand?: string;
  platformName?: string;
  category?: string;
}

export interface ProductInsertParams {
  platformId: number;
  brand: string;
  productName: string;
  amount: number;
  category: string;
}

function asString(raw: unknown) {
  return String(raw ?? '').trim();
}

function asNumber(raw: unknown, fallback = 0) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function asStatus(raw: unknown) {
  if (typeof raw === 'boolean') return raw ? 1 : 0;
  const value = Number(raw);
  if (!Number.isFinite(value)) return 1;
  return value === 0 ? 0 : 1;
}

function asBoolean(raw: unknown) {
  if (typeof raw === 'boolean') return raw;
  const text = asString(raw).toLowerCase();
  if (text === 'true') return true;
  if (text === 'false') return false;
  return asNumber(raw) > 0;
}

function normalizeProduct(raw: any): ProductRecord {
  return {
    id: asNumber(raw?.id),
    platform_id: asNumber(raw?.platform_id ?? raw?.platformId),
    brand: asString(raw?.brand),
    product_name: asString(raw?.product_name ?? raw?.productName),
    platform_name: asString(raw?.platform_name ?? raw?.platformName),
    category: asString(raw?.category),
    amount: asNumber(raw?.amount),
    stock: asNumber(raw?.stock),
    status: asStatus(raw?.status),
    created_at: asString(raw?.created_at ?? raw?.createdAt),
    updated_at: asString(raw?.updated_at ?? raw?.updatedAt),
  };
}

function normalizeProductList(raw: unknown): ProductRecord[] {
  if (!Array.isArray(raw)) return [];
  return raw.map((item) => normalizeProduct(item)).filter((item) => item.id > 0);
}

export async function getProductList(params: ProductListParams): Promise<ProductApiResult<ProductRecord[]>> {
  const res = await request.get<ProductApiResult<unknown>>('/product/list', {
    params: {
      platformId: params.platformId,
      productId: params.productId,
      status: params.status ?? 1,
      limit: params.limit ?? 200,
      offset: params.offset ?? 0,
    },
  });
  const payload = res.data;
  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: normalizeProductList(payload?.data),
  };
}

export async function getProductListByPlatform(params: {
  platformId: number;
  status?: number;
  limit?: number;
  offset?: number;
}): Promise<ProductApiResult<ProductRecord[]>> {
  const res = await request.get<ProductApiResult<unknown>>('/product/listByPlatform', {
    params: {
      platformId: params.platformId,
      status: params.status ?? 1,
      limit: params.limit ?? 5000,
      offset: params.offset ?? 0,
    },
  });
  const payload = res.data;
  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: normalizeProductList(payload?.data),
  };
}

export async function getProductOne(productId: number): Promise<ProductApiResult<ProductRecord | null>> {
  const res = await request.get<unknown>('/product/one', {
    params: { productId },
  });

  const rawPayload = res.data as any;
  const hasResultWrapper = rawPayload && typeof rawPayload === 'object' && 'code' in rawPayload;
  const code = hasResultWrapper ? Number(rawPayload?.code ?? 0) : 200;
  const msg = hasResultWrapper ? String(rawPayload?.msg ?? '') : '';
  const rawData = hasResultWrapper ? rawPayload?.data : rawPayload;

  const list = normalizeProductList(rawData);
  if (list.length > 0) {
    return {
      code,
      msg,
      data: list[0] ?? null,
    };
  }

  if (rawData && typeof rawData === 'object') {
    return {
      code,
      msg,
      data: normalizeProduct(rawData),
    };
  }

  return {
    code,
    msg,
    data: null,
  };
}

export async function getProductCategories(platformId: number): Promise<ProductApiResult<string[]>> {
  const res = await request.get<ProductApiResult<unknown>>('/product/showCategory', {
    params: { platformId },
  });
  const payload = res.data;
  const data = Array.isArray(payload?.data)
    ? payload.data
        .map((item: any) => asString(item?.category ?? item?.name ?? item))
        .filter((item: string) => Boolean(item))
    : [];

  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: Array.from(new Set(data)).sort(),
  };
}

export async function getProductPlatforms(): Promise<ProductApiResult<ProductPlatformOption[]>> {
  const res = await request.get<ProductApiResult<unknown>>('/product/showPlatform');
  const payload = res.data;
  const data = Array.isArray(payload?.data)
    ? payload.data
        .map((item: any) => ({
          id: asNumber(item?.id),
          name: asString(item?.name),
        }))
        .filter((item: ProductPlatformOption) => item.id > 0 && Boolean(item.name))
    : [];

  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data,
  };
}

export async function deleteProduct(params: ProductDeleteParams): Promise<ProductApiResult<number>> {
  const res = await request.post<ProductApiResult<unknown>>('/product/delete', null, {
    params: {
      productId: params.productId,
      status: params.status ?? 0,
    },
  });
  const payload = res.data;
  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: asNumber(payload?.data),
  };
}

export async function updateProduct(params: ProductUpdateParams): Promise<ProductApiResult<number>> {
  const res = await request.post<ProductApiResult<unknown>>('/product/update', null, {
    params: {
      productId: params.productId,
      productName: params.productName,
      brand: params.brand,
      platformName: params.platformName,
      category: params.category,
    },
  });
  const payload = res.data;
  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: asNumber(payload?.data),
  };
}

export async function insertProduct(params: ProductInsertParams): Promise<ProductApiResult<boolean>> {
  const res = await request.post<ProductApiResult<unknown>>('/product/insert', null, {
    params: {
      platformId: params.platformId,
      brand: params.brand,
      productName: params.productName,
      amount: params.amount,
      category: params.category,
    },
  });
  const payload = res.data;
  return {
    code: Number(payload?.code ?? 0),
    msg: String(payload?.msg ?? ''),
    data: asBoolean(payload?.data),
  };
}
