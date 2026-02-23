import request from '@/api/request';

export interface MqApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export interface CartItemRecord {
  id: number;
  user_id: number;
  product_id: number;
  city_id: number;
  brand: string;
  category: string;
  platform_id: number;
  product_status: number;
  city_name: string;
  cart_item_status: number;
  amount: number;
  quantity: number;
  status: number;
  created_at: string;
  updated_at: string;
  product_name: string;
}

export interface AddToCartParams {
  pid: number;
  cityId: number;
}

export interface DeleteCartItemParams {
  productId: number;
  cityId?: number;
}

export interface OrderEventPayload {
  orderNo?: string;
  userId?: number | null;
  productId?: number;
  cityId?: number;
  quantity?: number;
  amount?: number;
  remark?: string;
  paymentMethod?: string;
  createdAt?: string;
  status?: number;
}

interface OrderSubmitBodyItem {
  userId: number | null;
  orderNo: string;
  productId: number;
  cityId: number;
  quantity: number;
  amount: number;
  remark: string;
  paymentMethod: string;
  createdAt: string;
}

export interface MqOrderRecord {
  co_id: number;
  co_order_no: string;
  user_id: number;
  product_id: number;
  city_id: number;
  quantity: number;
  product_name: string;
  brand: string;
  category: string;
  city_name: string;
  province_name: string;
  platform_name: string;
  amount: number;
  co_created_at: string;
  co_updated_at: string;
  co_remark: string;
  pay_deadline: string;
  pay_time: string;
  payment_method: string;
  payment_no: string;
  status: number;
}

function asNumber(raw: unknown, fallback = 0) {
  const value = Number(raw);
  return Number.isFinite(value) ? value : fallback;
}

function asString(raw: unknown) {
  return String(raw ?? '').trim();
}

function formatLocalDateTimeIso(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

function normalizeLocalDateTime(raw: unknown) {
  const text = asString(raw);
  if (!text) return formatLocalDateTimeIso(new Date());

  const normalized = text.replace(' ', 'T');
  const directMatch = normalized.match(/^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})/);
  if (directMatch) return `${directMatch[1]}T${directMatch[2]}`;

  const parsed = new Date(normalized);
  if (Number.isFinite(parsed.getTime())) return formatLocalDateTimeIso(parsed);
  return normalized;
}

function buildClientOrderNo(index: number) {
  return `FRONT-${Date.now()}-${index + 1}`;
}

function normalizeOrderSubmitPayload(
  orderEvents: OrderEventPayload[] | OrderEventPayload | undefined
): OrderSubmitBodyItem[] {
  const source = Array.isArray(orderEvents)
    ? orderEvents
    : orderEvents && typeof orderEvents === 'object'
      ? [orderEvents]
      : [];

  return source.map((item, index) => {
    const userIdRaw = Number(item?.userId);
    const userId = Number.isFinite(userIdRaw) && userIdRaw > 0 ? userIdRaw : null;

    return {
      userId,
      orderNo: asString(item?.orderNo) || buildClientOrderNo(index),
      productId: asNumber(item?.productId),
      cityId: asNumber(item?.cityId),
      quantity: Math.max(1, Math.floor(asNumber(item?.quantity, 1))),
      amount: asNumber(item?.amount),
      remark: asString(item?.remark),
      paymentMethod: asString(item?.paymentMethod) || 'MOCK',
      createdAt: normalizeLocalDateTime(item?.createdAt),
    };
  });
}

function normalizeCartItem(raw: any): CartItemRecord {
  const cartItemStatus = asNumber(raw?.cart_item_status ?? raw?.status, 1);
  return {
    id: asNumber(raw?.id),
    user_id: asNumber(raw?.user_id ?? raw?.userId),
    product_id: asNumber(raw?.product_id ?? raw?.productId),
    city_id: asNumber(raw?.city_id ?? raw?.cityId),
    brand: asString(raw?.brand),
    category: asString(raw?.category),
    platform_id: asNumber(raw?.platform_id ?? raw?.platformId),
    product_status: asNumber(raw?.product_status ?? raw?.productStatus, 1),
    city_name: asString(raw?.city_name ?? raw?.cityName),
    cart_item_status: cartItemStatus,
    amount: asNumber(raw?.amount),
    quantity: asNumber(raw?.quantity, 1),
    status: cartItemStatus,
    created_at: asString(raw?.created_at ?? raw?.createdAt),
    updated_at: asString(raw?.updated_at ?? raw?.updatedAt),
    product_name: asString(raw?.product_name ?? raw?.productName),
  };
}

function normalizeCartList(raw: unknown) {
  if (!Array.isArray(raw)) return [];
  return raw
    .map((item) => normalizeCartItem(item))
    .filter((item) => item.id > 0 && item.product_id > 0);
}

function normalizeOrderRow(raw: any): MqOrderRecord {
  const orderStatus = asNumber(
    raw?.co_status ?? raw?.order_status ?? raw?.status ?? raw?.orderStatus,
    1
  );

  return {
    co_id: asNumber(raw?.co_id ?? raw?.coId ?? raw?.order_id ?? raw?.orderId ?? raw?.id),
    co_order_no: asString(raw?.co_order_no ?? raw?.coOrderNo ?? raw?.order_no ?? raw?.orderNo),
    user_id: asNumber(raw?.user_id ?? raw?.userId),
    product_id: asNumber(raw?.product_id ?? raw?.productId ?? raw?.pr_id ?? raw?.prId),
    city_id: asNumber(raw?.city_id ?? raw?.cityId ?? raw?.c_id ?? raw?.cId),
    quantity: Math.max(1, Math.floor(asNumber(raw?.quantity ?? raw?.co_quantity ?? 1))),
    product_name: asString(raw?.product_name ?? raw?.productName),
    brand: asString(raw?.p_brand ?? raw?.brand),
    category: asString(raw?.category ?? raw?.p_brand),
    city_name: asString(raw?.city_name ?? raw?.cityName ?? raw?.c_name ?? raw?.cName),
    province_name: asString(raw?.province_name ?? raw?.provinceName ?? raw?.pr_name ?? raw?.prName),
    platform_name: asString(raw?.platform_name ?? raw?.platformName ?? raw?.pf_name ?? raw?.pfName),
    amount: asNumber(raw?.amount),
    co_created_at: asString(raw?.co_created_at ?? raw?.coCreatedAt ?? raw?.created_at ?? raw?.createdAt),
    co_updated_at: asString(raw?.co_updated_at ?? raw?.coUpdatedAt ?? raw?.updated_at ?? raw?.updatedAt),
    co_remark: asString(raw?.co_remark ?? raw?.coRemark ?? raw?.remark),
    pay_deadline: asString(raw?.pay_deadline ?? raw?.payDeadline),
    pay_time: asString(raw?.pay_time ?? raw?.payTime),
    payment_method: asString(raw?.payment_method ?? raw?.paymentMethod),
    payment_no: asString(raw?.payment_no ?? raw?.paymentNo),
    status: orderStatus,
  };
}

function normalizeOrderList(raw: unknown): MqOrderRecord[] {
  const list = Array.isArray(raw)
    ? raw
    : raw && typeof raw === 'object'
      ? ((raw as any).list || (raw as any).records || (raw as any).rows || [])
      : [];

  if (!Array.isArray(list)) return [];
  return list
    .map((item) => normalizeOrderRow(item))
    .filter((item) => item.co_id > 0 || Boolean(item.co_order_no));
}

function normalizeResult<T = unknown>(payload: any, data: T): MqApiResult<T> {
  return {
    code: asNumber(payload?.code),
    msg: asString(payload?.msg),
    data,
  };
}

export async function getMqItemList(): Promise<MqApiResult<CartItemRecord[]>> {
  const res = await request.get<MqApiResult<unknown>>('/mq/itemList');
  const payload = res.data;
  return normalizeResult(payload, normalizeCartList(payload?.data));
}

export async function addToCart(params: AddToCartParams): Promise<MqApiResult<string>> {
  const payloadBody = {
    pid: asNumber(params.pid),
    cityId: asNumber(params.cityId),
  };

  const res = await request.post<MqApiResult<unknown>>('/mq/toItem', payloadBody);
  const payload = res.data;
  return normalizeResult(payload, asString(payload?.data));
}

export async function deleteCartItem(
  params: DeleteCartItemParams
): Promise<MqApiResult<string>> {
  const res = await request.get<MqApiResult<unknown>>('/mq/deleteItem', {
    params: {
      productId: asNumber(params.productId),
      cityId:
        params.cityId == null || Number.isNaN(Number(params.cityId))
          ? undefined
          : asNumber(params.cityId),
    },
  });
  const payload = res.data;
  return normalizeResult(payload, asString(payload?.data));
}

export async function orderSubmit(
  orderEvents: OrderEventPayload[] | OrderEventPayload
): Promise<MqApiResult<unknown>> {
  const safePayload = normalizeOrderSubmitPayload(orderEvents);

  const res = await request.post<MqApiResult<unknown>>('/mq/orderSubmit', safePayload);
  const payload = res.data;
  return normalizeResult(payload, payload?.data);
}

export async function paySubmit(
  orders: OrderEventPayload[]
): Promise<MqApiResult<unknown>> {
  const res = await request.post<MqApiResult<unknown>>('/mq/paySubmit', orders || []);
  const payload = res.data;
  return normalizeResult(payload, payload?.data);
}

export async function cancelOrder(orderNo: string): Promise<MqApiResult<unknown>> {
  const res = await request.post<MqApiResult<unknown>>('/mq/cancelOrder', null, {
    params: {
      orderNo: asString(orderNo),
    },
  });
  const payload = res.data;
  return normalizeResult(payload, payload?.data);
}

export async function getOrderList(): Promise<MqApiResult<MqOrderRecord[]>> {
  const res = await request.get<MqApiResult<unknown>>('/mq/selectAllOrders');
  const payload = res.data;
  return normalizeResult(payload, normalizeOrderList(payload?.data));
}

export interface RecentOrderListParams {
  limit?: number;
  status?: number;
}

export async function getRecentOrderList(
  params: RecentOrderListParams = {}
): Promise<MqApiResult<MqOrderRecord[]>> {
  const limit = Number(params.limit ?? 30);
  const status = Number(params.status ?? 0);

  const res = await request.get<MqApiResult<unknown>>('/mq/selectRecentOrders', {
    params: {
      limit: Number.isFinite(limit) && limit > 0 ? limit : 30,
      status: Number.isFinite(status) && status > 0 ? status : undefined,
    },
  });

  const payload = res.data;
  return normalizeResult(payload, normalizeOrderList(payload?.data));
}
