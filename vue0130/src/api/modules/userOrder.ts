import request from '@/api/request';

export interface UserOrderRecord {
  co_id: number;
  co_order_no: string;
  user_name: string;
  user_email: string;
  u_phone: string;
  u_status: number;
  product_name: string;
  p_brand: string;
  amount: number;
  pf_name: string;
  pr_name: string;
  c_name: string;
  co_created_at: string;
  co_remark: string;
}

export interface ApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export async function getUserOrderList() {
  const res = await request.get<ApiResponse<unknown>>('/show/userOrder');
  return res.data;
}
