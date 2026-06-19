import axios from 'axios';
 
const API_BASE = import.meta.env.VITE_API_BASE_URL || '';
 
const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});
 
/**
 * Request interceptor — attaches JWT and user identity headers.
 *
 * On AWS: API Gateway validates JWT and injects x-user-id, x-user-role.
 * Locally: We inject them from localStorage so backend controllers work.
 */
api.interceptors.request.use((config) => {
  const token   = localStorage.getItem('olp_token');
  const userStr = localStorage.getItem('olp_user');
 
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
 
  if (userStr) {
    try {
      const user = JSON.parse(userStr);
      if (user?.userId) {
        config.headers['x-user-id']   = user.userId;
        config.headers['x-user-role'] = user.role;
        config.headers['x-user-name'] = user.name ?? '';
      }
    } catch {
      // localStorage parse error — clear corrupt data
      localStorage.removeItem('olp_user');
    }
  }
 
  return config;
});
 
// Handle 401 — redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('olp_token');
      localStorage.removeItem('olp_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
 
export default api;
 
 