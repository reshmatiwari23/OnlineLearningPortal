import axios from 'axios';

// In production, API_BASE is the API Gateway URL
// Locally, Vite proxy handles routing to each service
const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

const api = axios.create({
  baseURL: API_BASE,
  headers: { 'Content-Type': 'application/json' },
  timeout: 30000,
});

// Attach JWT token from localStorage to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('olp_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 globally — redirect to login
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
