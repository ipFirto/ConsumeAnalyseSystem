import request from '@/api/request';

export interface CategoryOrderRecord {
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

export interface CategoryApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export async function getCategoryData(
  platformId: number,
  categoryName: string
): Promise<CategoryApiResult<CategoryOrderRecord[]>> {
  const safeCategory = encodeURIComponent(categoryName);
  const res = await request.get<CategoryApiResult<CategoryOrderRecord[]>>(
    `/category/${platformId}/${safeCategory}`
  );
  return res.data;
}
