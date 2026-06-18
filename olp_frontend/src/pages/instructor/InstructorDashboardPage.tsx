import { useState, useEffect, useRef } from 'react';
import { courseService } from '../../services/courseService';
import CourseCard from '../../components/course/CourseCard';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import type { Course } from '../../types';
import styles from './Instructor.module.css';

export default function InstructorDashboardPage() {
  const [courses,  setCourses]  = useState<Course[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [showCreate, setShowCreate] = useState(false);

  // Create form state
  const [title, setTitle]       = useState('');
  const [desc,  setDesc]        = useState('');
  const [creating, setCreating] = useState(false);

  // Upload state
  const [uploadCourseId, setUploadCourseId] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus,   setUploadStatus]   = useState<'idle' | 'uploading' | 'done' | 'error'>('idle');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const fetchCourses = () =>
    courseService.getMyCourses()
      .then(p => setCourses(p.content))
      .finally(() => setLoading(false));

  useEffect(() => { fetchCourses(); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);
    try {
      const course = await courseService.create({ title, description: desc });
      setCourses(prev => [course, ...prev]);
      setShowCreate(false);
      setTitle(''); setDesc('');
    } finally {
      setCreating(false);
    }
  };

  const handleFileSelect = async (courseId: string, file: File) => {
    if (!file.type.startsWith('video/')) {
      alert('Please select a video file (MP4, MOV, AVI)');
      return;
    }
    if (file.size > 10 * 1024 * 1024 * 1024) {
      alert('File must be under 10 GB');
      return;
    }

    setUploadCourseId(courseId);
    setUploadStatus('uploading');
    setUploadProgress(0);

    try {
      // Step 1: Get S3 presigned URL
      const { uploadUrl } = await courseService.getUploadUrl(courseId, file.name);

      // Step 2: Upload directly to S3
      await courseService.uploadVideoToS3(uploadUrl, file, (pct) => {
        setUploadProgress(pct);
      });

      setUploadStatus('done');
      // Refresh the course to show updated status
      setTimeout(() => fetchCourses(), 2000);
    } catch {
      setUploadStatus('error');
    }
  };

  return (
    <div className={styles.page}>
      <div className="container">
        <div className={styles.pageHeader}>
          <div>
            <h1>My Courses</h1>
            <p>Manage and publish your courses</p>
          </div>
          <Button onClick={() => setShowCreate(true)}>+ Create course</Button>
        </div>

        {loading ? (
          <div className={styles.center}><div className="spinner" /></div>
        ) : courses.length === 0 ? (
          <div className={styles.empty}>
            <p style={{ fontSize: 40, marginBottom: 12 }}>🎓</p>
            <p>You haven't created any courses yet.</p>
            <Button style={{ marginTop: 16 }} onClick={() => setShowCreate(true)}>
              Create your first course
            </Button>
          </div>
        ) : (
          <div className={styles.grid}>
            {courses.map(c => (
              <div key={c.id}>
                <CourseCard course={c} showStatus />
                <div style={{ marginTop: 8, display: 'flex', gap: 8 }}>
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      setUploadCourseId(c.id);
                      fileInputRef.current?.click();
                    }}
                  >
                    📤 Upload video
                  </Button>
                </div>

                {/* Upload progress for this course */}
                {uploadCourseId === c.id && uploadStatus === 'uploading' && (
                  <div style={{ marginTop: 8 }}>
                    <div className={styles.uploadProgress}>
                      <div className={styles.uploadProgressFill} style={{ width: `${uploadProgress}%` }} />
                    </div>
                    <p className={styles.statusMsg}>{uploadProgress}% uploaded…</p>
                  </div>
                )}
                {uploadCourseId === c.id && uploadStatus === 'done' && (
                  <p className={`${styles.statusMsg} ${styles.success}`}>
                    ✅ Upload complete — AI pipeline running
                  </p>
                )}
                {uploadCourseId === c.id && uploadStatus === 'error' && (
                  <p className={`${styles.statusMsg} ${styles.error}`}>
                    ❌ Upload failed. Please try again.
                  </p>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Hidden file input */}
        <input
          type="file"
          accept="video/*"
          ref={fileInputRef}
          style={{ display: 'none' }}
          onChange={e => {
            const file = e.target.files?.[0];
            if (file && uploadCourseId) handleFileSelect(uploadCourseId, file);
            e.target.value = '';
          }}
        />
      </div>

      {/* Create course modal */}
      {showCreate && (
        <div className={styles.overlay} onClick={() => setShowCreate(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <h2>Create a new course</h2>
            <form className={styles.modalForm} onSubmit={handleCreate}>
              <Input
                label="Course title"
                value={title}
                onChange={e => setTitle(e.target.value)}
                placeholder="e.g. Spring Boot for Beginners"
                required autoFocus
              />
              <div>
                <label style={{ fontSize: 13, fontWeight: 500, display: 'block', marginBottom: 5 }}>
                  Description (optional)
                </label>
                <textarea
                  value={desc}
                  onChange={e => setDesc(e.target.value)}
                  placeholder="What will learners gain from this course?"
                  rows={3}
                  style={{
                    width: '100%', padding: '9px 12px',
                    border: '1px solid var(--border)', borderRadius: 'var(--radius-md)',
                    fontSize: 14, resize: 'vertical', fontFamily: 'var(--font)',
                  }}
                />
              </div>
              <div className={styles.modalActions}>
                <Button type="button" variant="secondary" onClick={() => setShowCreate(false)}>
                  Cancel
                </Button>
                <Button type="submit" loading={creating}>
                  Create course
                </Button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
