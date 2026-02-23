import axios from "axios";
import type { InternalAxiosRequestConfig, AxiosRequestConfig } from "axios";

// 统一后端返回结构
export interface ApiResult<T = any> {
  code: number;
  msg: string;
  data: T;
}

// 创建 axios 实例
const request = axios.create({
  baseURL: "/api",
  timeout: 10000,
  headers: {
    "Content-Type": "application/json",
  },
});

const PUBLIC_NO_AUTH_PATHS = [
  "/user/login",
  "/user/register",
  "/user/sendEmailCode",
  "/user/forget",
  "/user/judgeIfExist",
];

function shouldSkipAuth(config: InternalAxiosRequestConfig) {
  const rawUrl = String(config.url || "");
  const path = rawUrl.split("?")[0] || "";
  return PUBLIC_NO_AUTH_PATHS.some((prefix) => path.startsWith(prefix));
}

// 请求拦截器
request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    config.headers = config.headers ?? {};

    if (shouldSkipAuth(config)) {
      delete (config.headers as any).Authorization;
      return config;
    }

    const token = localStorage.getItem("token");
    if (token) {
      (config.headers as any).Authorization = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器：运行时返回 {code,msg,data}
request.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(error)
);

// 提供类型正确的请求方法
export const http = {
  get<T>(url: string, config?: AxiosRequestConfig) {
    return request.get<any, ApiResult<T>>(url, config);
  },
  post<T>(url: string, data?: any, config?: AxiosRequestConfig) {
    return request.post<any, ApiResult<T>>(url, data, config);
  },
  put<T>(url: string, data?: any, config?: AxiosRequestConfig) {
    return request.put<any, ApiResult<T>>(url, data, config);
  },
  delete<T>(url: string, config?: AxiosRequestConfig) {
    return request.delete<any, ApiResult<T>>(url, config);
  },
};

export default request;
