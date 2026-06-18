import api from './api';
import type {
  ApiResponse, Course, CreateCourseRequest, UpdateCourseRequest,
  PageResponse, UploadUrlResponse,
} from '../types';

export const courseService = {

  async getAll(page = 0, size = 12): Promise<PageResponse<Course>> {
    const res = await api.get<ApiResponse<PageResponse<Course>>>(
      `/api/courses?page=${page}&size=${size}`
    );
    return res.data.data;
  },

  async getById(id: string): Promise<Course> {
    const res = await api.get<ApiResponse<Course>>(`/api/courses/${id}`);
    return res.data.data;
  },

  async search(keyword: string, page = 0, size = 12): Promise<PageResponse<Course>> {
    const res = await api.get<ApiResponse<PageResponse<Course>>>(
      `/api/courses/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
    );
    return res.data.data;
  },

  async getMyCourses(page = 0, size = 12): Promise<PageResponse<Course>> {
    const res = await api.get<ApiResponse<PageResponse<Course>>>(
      `/api/courses/my?page=${page}&size=${size}`
    );
    return res.data.data;
  },

  async create(data: CreateCourseRequest): Promise<Course> {
    const res = await api.post<ApiResponse<Course>>('/api/courses', data);
    return res.data.data;
  },

  async update(id: string, data: UpdateCourseRequest): Promise<Course> {
    const res = await api.put<ApiResponse<Course>>(`/api/courses/${id}`, data);
    return res.data.data;
  },

  async remove(id: string): Promise<void> {
    await api.delete(`/api/courses/${id}`);
  },

  async getUploadUrl(id: string, fileName: string): Promise<UploadUrlResponse> {
    const res = await api.post<ApiResponse<UploadUrlResponse>>(
      `/api/courses/${id}/upload-url?fileName=${encodeURIComponent(fileName)}`
    );
    return res.data.data;
  },

  // Upload video directly to S3 using the presigned URL
  async uploadVideoToS3(
    uploadUrl: string,
    file: File,
    onProgress?: (percent: number) => void
  ): Promise<void> {
    await api.put(uploadUrl, file, {
      headers: { 'Content-Type': 'video/mp4' },
      baseURL: '',   // bypass the Axios baseURL — this goes directly to S3
      onUploadProgress: (e) => {
        if (onProgress && e.total) {
          onProgress(Math.round((e.loaded * 100) / e.total));
        }
      },
    });
  },
};
