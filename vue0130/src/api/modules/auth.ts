import request, { type ApiResult } from '@/api/request';

// 登录 API
export const login = async (params: {
  userEmail: string;
  password: string;
}): Promise<ApiResult> => {
  return request.post('/user/login', params);
};

// 注册 API
export const register = async (params: {
  userEmail: string;
  userName: string;
  password: string;
  validateCode: string;
}): Promise<ApiResult> => {
  return request.post('/user/register', params);
};

// 判断邮箱是否可注册 API（200: 可注册；其他: 已注册或不可用）
export const judgeIfExist = async (email: string): Promise<ApiResult> => {
  return request.post(`/user/judgeIfExist?email=${encodeURIComponent(email)}`);
};

// 发送验证码 API
export const sendCode = async (email: string): Promise<ApiResult> => {
  return request.post(`/user/sendEmailCode?email=${encodeURIComponent(email)}`);
};

// 找回密码 API
export const forgetPassword = async (params: {
  userEmail: string;
  newPassword: string;
  validateCode: string;
}): Promise<ApiResult> => {
  return request.post('/user/forget', params);
};

// 获取用户信息 API
export const getUserInfo = async (): Promise<ApiResult> => {
  return request.post('/user/userInfo');
};

// 修改密码 API
export const updatePassword = async (params: {
  userEmail: string;
  password: string;
  validateCode: string;
}): Promise<ApiResult> => {
  return request.patch('/user/updatePassword', params);
};

// 更新用户信息 API
export const updateUserInfo = async (params: {
  userName: string;
  phone?: string;
  userEmail?: string;
}): Promise<ApiResult> => {
  return request.patch('/user/updateUserInfo', params);
};

// 删除用户 API
export const deleteUser = async (): Promise<ApiResult> => {
  return request.post('/user/deleteUser');
};

// 验证 token 有效性
export const validateToken = async (): Promise<boolean> => {
  try {
    const res = await request.get('/user/validateToken');
    return res.data.code === 200;
  } catch {
    return false;
  }
};
