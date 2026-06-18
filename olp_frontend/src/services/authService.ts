import api from './api';
import type { ApiResponse, AuthResponse, LoginRequest, SignupRequest } from '../types';

export const authService = {

  async signup(data: SignupRequest): Promise<AuthResponse> {
    const res = await api.post<ApiResponse<AuthResponse>>('/api/auth/signup', data);
    return res.data.data;
  },

  async login(data: LoginRequest): Promise<AuthResponse> {
    const res = await api.post<ApiResponse<AuthResponse>>('/api/auth/login', data);
    return res.data.data;
  },
};
