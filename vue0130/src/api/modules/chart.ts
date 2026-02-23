import request from "../request";
import type { AxiosResponse } from "axios";

export interface ConsumeOrderDTO {
    coId: number; // 订单id
    coOrderNo: string; // 订单号
    pBrand: string; // 品牌名称
    productName: string; // 商品名称
    pfName: string; // 平台名称
    amount: number; // 订单金额
    coRemark: string; // 订单备注
    coCreatedAt: string; // 订单创建时间
    cId: number; // 城市id
    cName: string; // 城市名
    cCode: string; // 城市编号
    prId: number; // 省id
    prName: string; // 省名
    prType: number; // 省类型
    userId: number; // 用户id
    userEmail: string; // 用户邮箱
    userName: string // 用户名
    uPhone: string; // 用户手机号
    uStatus: boolean; // 用户状态 0/1 禁用/启用
}

export interface ApiResponse<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export interface CategoryPieItem {
  platform_id: number;
  platform_name: string;
  category: string;
  cnt: number;
}

export interface ProvinceOrderCountRow {
  province_id: number;
  province_name: string;
  order_total: number;
}


export async function getBarByPlatform() {
  const res = (await request.get<ApiResponse<number[]>>("/show/showBarByPlatform"));
  console.log("已经发送请求,获取到Barres:",res.data)
  return res.data;
}

export function getCategoryPie(platformId: number):Promise<AxiosResponse<ApiResponse<CategoryPieItem[]>>> {
  return request.get<ApiResponse<CategoryPieItem[]>>("/show/showLineByPlatform",{
      params: { platformId } 
  });
}

export function getSmoothByOriginal(): Promise<AxiosResponse<ApiResponse<ProvinceOrderCountRow[]>>>{
  return request.get("/show/showSmoothByOriginal");
}

export async function fetchPlatformData(platformId: number) {
  const res = await request.get<ApiResponse<ConsumeOrderDTO[]>>("/show/platformData",{
      params: { platformId },
    });

    if(res.data.code!=200) console.log(res.data.msg || "暂未获取到数据");
    return res.data.data;
};


/** 封装一层，直接拿到 data，顺便做 code 校验 */
// export async function fetchPlatformData(platformId: number) {
//   const res = (await getPlatformData(platformId));
//   // 这里的 code 按你后端约定改：200 / 0 / 1 ...
//   if (res.data.code !== 200) {
//     throw new Error(res.data.msg || "获取省份排行失败");
//   }
//   return res.data.data;
// }