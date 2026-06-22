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

  // Fixed: Use fetch directly instead of axios to avoid header conflicts with presigned URLs
  async uploadVideoToS3(
    uploadUrl: string,
    file: File,
    onProgress?: (percent: number) => void
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const xhr = new XMLHttpRequest();

      xhr.upload.addEventListener('progress', (e) => {
        if (onProgress && e.lengthComputable) {
          const percent = Math.round((e.loaded / e.total) * 100);
          onProgress(percent);
        }
      });

      xhr.addEventListener('load', () => {
        if (xhr.status >= 200 && xhr.status < 300) {
          resolve();
        } else {
          reject(new Error(`Upload failed with status ${xhr.status}: ${xhr.responseText}`));
        }
      });

      xhr.addEventListener('error', () => {
        reject(new Error('Network error during upload'));
      });

      xhr.addEventListener('abort', () => {
        reject(new Error('Upload aborted'));
      });

      xhr.open('PUT', uploadUrl);
      xhr.setRequestHeader('Content-Type', file.type || 'video/mp4');
      xhr.send(file);
    });
  },

  async updateUploadStatus(id: string, status: string, durationSecs?: number): Promise<Course> {
    const params = new URLSearchParams({ status });
    if (durationSecs) params.append('durationSecs', durationSecs.toString());
    const res = await api.patch<ApiResponse<Course>>(
      `/api/courses/${id}/upload-status?${params}`
    );
    return res.data.data;
  },
};
