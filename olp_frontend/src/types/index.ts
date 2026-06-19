// ── API wrapper ────────────────────────────────────────────────
export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data: T;
  timestamp: string;
}
 
// ── Auth ───────────────────────────────────────────────────────
export interface AuthResponse {
  token: string;
  userId: string;
  email: string;
  name: string;
  role: 'user' | 'instructor';
  expiresIn: number;
}
 
export interface SignupRequest {
  email: string;
  password: string;
  name: string;
  role: 'user' | 'instructor';
}
 
export interface LoginRequest {
  email: string;
  password: string;
}
 
// ── User ───────────────────────────────────────────────────────
export interface User {
  userId: string;
  email: string;
  name: string;
  role: 'user' | 'instructor';
  token: string;
}
 
// ── Course ─────────────────────────────────────────────────────
// Accept both uppercase (from Java enum) and lowercase
export type UploadStatus =
  | 'NONE' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED'
  | 'none' | 'pending' | 'processing' | 'ready' | 'failed';
 
export interface Course {
  id: string;
  title: string;
  description?: string;
  instructorId: string;
  instructorName: string;
  videoUrl?: string;
  videoDuration: number;
  thumbnailUrl?: string;
  uploadStatus: UploadStatus;
  aiSummary?: string;
  kbIngested: boolean;
  isPublished: boolean;
  createdAt: string;
  updatedAt: string;
}
 
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
}
 
export interface CreateCourseRequest {
  title: string;
  description?: string;
}
 
export interface UpdateCourseRequest {
  title?: string;
  description?: string;
  isPublished?: boolean;
}
 
export interface UploadUrlResponse {
  uploadUrl: string;
  s3Key: string;
  expiresInSeconds: number;
}
 
// ── Enrollment ─────────────────────────────────────────────────
export interface Enrollment {
  id: string;
  courseId: string;
  userId: string;
  enrolledAt: string;
  completedAt?: string;
  completed: boolean;
}
 
// ── Progress ───────────────────────────────────────────────────
export interface Progress {
  userId: string;
  courseId: string;
  currentTimeSecs: number;
  durationSecs: number;
  percentComplete: number;
  completed: boolean;
  lastUpdatedAt: string;
  completedAt?: string;
  source: 'redis' | 'database';
}
 
export interface UpdateProgressRequest {
  currentTimeSecs: number;
  durationSecs: number;
}
 
// ── AI ─────────────────────────────────────────────────────────
export interface Citation {
  chunkId: string;
  timestampSeconds?: number;
  similarityScore: number;
  excerpt: string;
}
 
export interface Recommendation {
  courseId: string;
  courseTitle: string;
  instructorName: string;
  aiReason: string;
  similarityScore: number;
}
 
 