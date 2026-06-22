import { useState, useEffect, useRef } from 'react';
import api from '../../services/api';
import { courseService } from '../../services/courseService';
import CourseCard from '../../components/course/CourseCard';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import type { Course } from '../../types';
import styles from './Instructor.module.css';

const markCourseReady = (courseId: string, durationSecs: number) =>
  api.patch(`/api/courses/${courseId}/upload-status?status=ready&durationSecs=${durationSecs}`);

const publishCourse = (courseId: string, publish: boolean) =>
  api.put(`/api/courses/${courseId}`, { isPublished: publish });

export default function InstructorDashboardPage() {
  const [courses,    setCourses]    = useState<Course[]>([]);
  const [loading,    setLoading]    = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const [title,       setTitle]       = useState('');
  const [desc,        setDesc]        = useState('');
  const [creating,    setCreating]    = useState(false);
  const [createError, setCreateError] = useState('');

  const [uploadCourseId, setUploadCourseId] = useState<string | null>(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStatus,   setUploadStatus]   = useState<'idle'|'uploading'|'done'|'error'>('idle');
  const [uploadMsg,      setUploadMsg]      = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const isLocal = window.location.hostname === 'localhost';

  const fetchCourses = () =>
    courseService.getMyCourses()
      .then(p => setCourses(p.content))
      .catch(e => console.error('Failed to load courses:', e))
      .finally(() => setLoading(false));

  useEffect(() => { fetchCourses(); }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) { setCreateError('Title is required'); return; }
    setCreating(true);
    setCreateError('');
    try {
      const course = await courseService.create({
        title: title.trim(),
        description: desc.trim() || undefined
      });
      setCourses(prev => [course, ...prev]);
      setShowCreate(false);
      setTitle('');
      setDesc('');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Failed to create course. Please try again.';
      setCreateError(msg);
    } finally {
      setCreating(false);
    }
  };

  const handlePublish = async (course: Course) => {
    try {
      await publishCourse(course.id, !course.isPublished);
      fetchCourses();
    } catch (err) {
      console.error('Publish failed:', err);
    }
  };

  const handleDelete = async (course: Course) => {
    if (!confirm(`Are you sure you want to delete "${course.title}"? This cannot be undone.`)) return;
    setDeletingId(course.id);
    try {
      await courseService.remove(course.id);
      setCourses(prev => prev.filter(c => c.id !== course.id));
    } catch (err) {
      console.error('Delete failed:', err);
      alert('Failed to delete course. Please try again.');
    } finally {
      setDeletingId(null);
    }
  };

  const handleFileSelect = async (courseId: string, file: File) => {
    if (!file.type.startsWith('video/')) {
      alert('Please select a video file (MP4, MOV, AVI)');
      return;
    }

    setUploadCourseId(courseId);
    setUploadStatus('uploading');
    setUploadProgress(0);
    setUploadMsg('');

    try {
      if (isLocal) {
        setUploadMsg('Local mode — simulating upload...');
        for (let p = 10; p <= 90; p += 10) {
          await new Promise(r => setTimeout(r, 150));
          setUploadProgress(p);
        }
        const estimatedDuration = Math.max(60, Math.round(file.size / 35000));
        await markCourseReady(courseId, estimatedDuration);
        setUploadProgress(100);
        setUploadStatus('done');
        setUploadMsg(`✅ Simulated — course marked ready`);
        setTimeout(() => fetchCourses(), 500);
      } else {
        setUploadMsg('Getting upload URL...');
        const { uploadUrl } = await courseService.getUploadUrl(courseId, file.name);
        setUploadMsg('Uploading to S3...');
        await courseService.uploadVideoToS3(uploadUrl, file, (pct) => {
          setUploadProgress(pct);
          setUploadMsg(`Uploading... ${pct}%`);
        });
        setUploadProgress(100);
        setUploadStatus('done');
        setUploadMsg('✅ Upload complete — AI pipeline processing...');
        setTimeout(() => fetchCourses(), 2000);
      }
    } catch (err) {
      console.error('Upload error:', err);
      setUploadStatus('error');
      setUploadMsg('❌ Upload failed. Please try again.');
    }
  };

  return (
    <div className={styles.page}>
      <div className="container">
        <div className={styles.pageHeader}>
          <div>
            <h1>My Courses</h1>
            <p>Create, upload and publish your courses
              {isLocal && (
                <span style={{
                  marginLeft: 8, fontSize: 12,
                  background: '#FEF3C7', color: '#92400E',
                  padding: '2px 8px', borderRadius: 4
                }}>
                  Local mode
                </span>
              )}
            </p>
          </div>
          <Button onClick={() => { setShowCreate(true); setCreateError(''); }}>
            + Create course
          </Button>
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

                <div style={{ marginTop: 8, display: 'flex', gap: 8, flexWrap: 'wrap' }}>

                  {/* Upload video button */}
                  <Button
                    size="sm"
                    variant="secondary"
                    onClick={() => {
                      setUploadCourseId(c.id);
                      setUploadStatus('idle');
                      setUploadMsg('');
                      setUploadProgress(0);
                      fileInputRef.current?.click();
                    }}
                  >
                    📤 Upload video
                  </Button>

                  {/* Publish / Unpublish button */}
                  <Button
                    size="sm"
                    variant={c.isPublished ? 'secondary' : 'primary'}
                    onClick={() => handlePublish(c)}
                  >
                    {c.isPublished ? '📝 Unpublish' : '🚀 Publish'}
                  </Button>

                  {/* Delete button */}
                  <Button
                    size="sm"
                    variant="secondary"
                    loading={deletingId === c.id}
                    onClick={() => handleDelete(c)}
                    style={{ color: 'var(--red, #DC2626)', borderColor: 'var(--red, #DC2626)' }}
                  >
                    🗑️ Delete
                  </Button>

                  {/* Local only — mark ready without uploading */}
                  {isLocal && c.uploadStatus !== 'ready' && (
                    <Button
                      size="sm"
                      variant="secondary"
                      onClick={async () => {
                        await markCourseReady(c.id, 1800);
                        fetchCourses();
                      }}
                    >
                      ⚡ Mark ready
                    </Button>
                  )}
                </div>

                {uploadCourseId === c.id && uploadStatus === 'uploading' && (
                  <div style={{ marginTop: 8 }}>
                    <div className={styles.uploadProgress}>
                      <div className={styles.uploadProgressFill}
                           style={{ width: `${uploadProgress}%` }} />
                    </div>
                    <p className={styles.statusMsg}>{uploadMsg}</p>
                  </div>
                )}
                {uploadCourseId === c.id && uploadStatus === 'done' && (
                  <p className={`${styles.statusMsg} ${styles.success}`}>{uploadMsg}</p>
                )}
                {uploadCourseId === c.id && uploadStatus === 'error' && (
                  <p className={`${styles.statusMsg} ${styles.error}`}>{uploadMsg}</p>
                )}
              </div>
            ))}
          </div>
        )}

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
            {createError && (
              <div style={{
                background: '#FEE2E2', color: '#DC2626',
                padding: '10px 14px', borderRadius: 8,
                marginBottom: 16, fontSize: 13
              }}>
                {createError}
              </div>
            )}
            <form className={styles.modalForm} onSubmit={handleCreate}>
              <Input
                label="Course title"
                value={title}
                onChange={e => setTitle(e.target.value)}
                placeholder="e.g. Spring Boot for Beginners"
                required autoFocus
              />
              <div>
                <label style={{
                  fontSize: 13, fontWeight: 500,
                  display: 'block', marginBottom: 5
                }}>
                  Description (optional)
                </label>
                <textarea
                  value={desc}
                  onChange={e => setDesc(e.target.value)}
                  placeholder="What will learners gain from this course?"
                  rows={3}
                  style={{
                    width: '100%', padding: '9px 12px',
                    border: '1px solid var(--border)',
                    borderRadius: 'var(--radius-md)',
                    fontSize: 14, resize: 'vertical',
                    fontFamily: 'var(--font)',
                  }}
                />
              </div>
              <div className={styles.modalActions}>
                <Button type="button" variant="secondary"
                        onClick={() => setShowCreate(false)}>
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
