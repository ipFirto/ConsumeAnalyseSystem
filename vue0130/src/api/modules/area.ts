import request from '@/api/request';

export interface AreaProvinceOption {
  id: number;
  name: string;
}

export interface AreaCityOption {
  id: number;
  name: string;
  province_id: number;
}

export interface AreaOptionsData {
  provinces: AreaProvinceOption[];
  cities: AreaCityOption[];
}

export interface AreaApiResult<T = unknown> {
  code: number;
  msg: string;
  data: T;
}

export async function getAreaOptions() {
  const res = await request.get<AreaApiResult<AreaOptionsData>>('/area/options');
  return res.data;
}
