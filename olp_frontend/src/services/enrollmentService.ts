import api from './api';
import type { ApiResponse, Enrollment, Progress, UpdateProgressRequest } from '../types';

export const enrollmentService = {

  async enrol(courseId: string): Promise<Enrollment> {
    const res = await api.post<ApiResponse<Enrollment>>(`/api/enrollment/${courseId}`);
    return res.data.data;
  },

  async unenrol(courseId: string): Promise<void> {
    await api.delete(`/api/enrollment/${courseId}`);
  },

  async getMyEnrollments(): Promise<Enrollment[]> {
    const res = await api.get<ApiResponse<Enrollment[]>>('/api/enrollment/my');
    return res.data.data;
  },

  async isEnrolled(courseId: string): Promise<boolean> {
    const res = await api.get<ApiResponse<boolean>>(`/api/enrollment/${courseId}/status`);
    return res.data.data;
  },

  async getCount(courseId: string): Promise<number> {
    const res = await api.get<ApiResponse<number>>(`/api/enrollment/${courseId}/count`);
    return res.data.data;
  },
};

export const progressService = {

  async update(courseId: string, data: UpdateProgressRequest): Promise<Progress> {
    const res = await api.post<ApiResponse<Progress>>(`/api/progress/${courseId}`, data);
    return res.data.data;
  },

  async get(courseId: string): Promise<Progress> {
    const res = await api.get<ApiResponse<Progress>>(`/api/progress/${courseId}`);
    return res.data.data;
  },

  async getAll(): Promise<Progress[]> {
    const res = await api.get<ApiResponse<Progress[]>>('/api/progress/my');
    return res.data.data;
  },
};
