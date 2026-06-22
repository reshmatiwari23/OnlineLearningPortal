import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'react-router-dom';
import { courseService } from '../../services/courseService';
import { enrollmentService, progressService } from '../../services/enrollmentService';
import { useAuth } from '../../context/AuthContext';
import VideoPlayer from '../../components/course/VideoPlayer';
import AiChat from '../../components/ai/AiChat';
import Button from '../../components/common/Button';
import type { Course, Progress } from '../../types';
import styles from './Learner.module.css';
 
function formatDuration(s: number) {
  if (!s || s === 0) return '';
  const h = Math.floor(s / 3600);
  const m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m} min`;
}
 
// Case-insensitive check — Java returns READY, could also be ready
const isReady = (status?: string) =>
  status?.toUpperCase() === 'READY';
 
export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user, isInstructor } = useAuth();
 
  const [course,    setCourse]    = useState<Course | null>(null);
  const [enrolled,  setEnrolled]  = useState(false);
  const [progress,  setProgress]  = useState<Progress | null>(null);
  const [loading,   setLoading]   = useState(true);
  const [enrolling, setEnrolling] = useState(false);
  const [error,     setError]     = useState('');
 
  useEffect(() => {
    if (!id) return;
    setLoading(true);
 
    Promise.all([
      courseService.getById(id),
      user ? enrollmentService.isEnrolled(id).catch(() => false) : Promise.resolve(false),
      user ? progressService.get(id).catch(() => null) : Promise.resolve(null),
    ])
      .then(([c, e, p]) => {
        setCourse(c as Course);
        setEnrolled(e as boolean);
        setProgress(p as Progress | null);
      })
      .catch(() => setError('Failed to load course'))
      .finally(() => setLoading(false));
  }, [id, user]);
 
  const handleEnrol = async () => {
    if (!id) return;
    setEnrolling(true);
    setError('');
    try {
      await enrollmentService.enrol(id);
      setEnrolled(true);
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Enrolment failed. Please try again.';
      setError(msg);
    } finally {
      setEnrolling(false);
    }
  };
 
  const handleProgress = useCallback((percent: number) => {
    setProgress(prev => prev ? { ...prev, percentComplete: percent } : null);
  }, []);
 
  if (loading) return (
    <div className={styles.center} style={{ minHeight: '60vh' }}>
      <div className="spinner" style={{ width: 36, height: 36 }} />
    </div>
  );
 
  if (error && !course) return (
    <div className={styles.center} style={{ minHeight: '60vh' }}>
      <p style={{ color: 'var(--red)' }}>{error}</p>
    </div>
  );
 
  if (!course) return (
    <div className={styles.center} style={{ minHeight: '60vh' }}>
      <p>Course not found.</p>
    </div>
  );
 
  const canWatch = isInstructor || enrolled;
  const videoReady = isReady(course.uploadStatus) && !!course.videoUrl;
  const showPlayer = canWatch && videoReady;
 
  console.log('Course debug:', {
    title: course.title,
    uploadStatus: course.uploadStatus,
    videoUrl: course.videoUrl,
    isPublished: course.isPublished,
    canWatch,
    videoReady,
    showPlayer
  });
 
  return (
    <div className={styles.page}>
      <div className="container">
 
        {/* Video Player */}
        {showPlayer && (
          <div style={{ marginBottom: 32 }}>
            <VideoPlayer
              courseId={course.id}
              src={course.videoUrl!}
              startAt={progress?.currentTimeSecs ?? 0}
              onProgress={handleProgress}
            />
          </div>
        )}
 
        {/* Not enrolled yet — show enrol prompt */}
        {!isInstructor && !enrolled && (
          <div style={{
            background: 'var(--teal-light)',
            border: '1px solid var(--teal)',
            borderRadius: 'var(--radius-lg)',
            padding: 20, marginBottom: 32, textAlign: 'center'
          }}>
            <p style={{ fontWeight: 600, color: 'var(--teal)' }}>
              Enrol to watch this course
            </p>
          </div>
        )}
 
        {/* Enrolled but video not ready */}
        {canWatch && !videoReady && (
          <div style={{
            background: 'var(--amber-light)',
            border: '1px solid var(--amber)',
            borderRadius: 'var(--radius-lg)',
            padding: 20, marginBottom: 32, textAlign: 'center'
          }}>
            <p style={{ fontWeight: 600, color: 'var(--amber)' }}>
              {course.videoUrl
                ? `⏳ Video processing — status: ${course.uploadStatus}`
                : '📹 No video uploaded yet'}
            </p>
          </div>
        )}
 
        <div className={styles.detailLayout}>
 
          {/* Main content */}
          <div>
            <h1 style={{
              fontSize: 26, fontWeight: 700,
              color: 'var(--navy)', marginBottom: 8
            }}>
              {course.title}
            </h1>
 
            {course.description && (
              <p style={{
                color: 'var(--text-muted)', fontSize: 15,
                marginBottom: 20, lineHeight: 1.7
              }}>
                {course.description}
              </p>
            )}
 
            {/* AI Summary */}
            {course.aiSummary && (
              <div style={{
                background: 'var(--teal-light)',
                border: '1px solid var(--teal)',
                borderRadius: 'var(--radius-lg)',
                padding: 20, marginBottom: 20
              }}>
                <h3 style={{
                  fontSize: 15, fontWeight: 600,
                  color: 'var(--teal)', marginBottom: 12
                }}>
                  🤖 AI Course Summary
                </h3>
                {(() => {
                  try {
                    const s = typeof course.aiSummary === 'string'
                      ? JSON.parse(course.aiSummary)
                      : course.aiSummary;
                    return (
                      <>
                        {s.summary && <p style={{ fontSize: 14, marginBottom: 10 }}>{s.summary}</p>}
                        {s.objectives?.length > 0 && (
                          <ul style={{ paddingLeft: 20 }}>
                            {s.objectives.map((o: string, i: number) => (
                              <li key={i} style={{ fontSize: 14, color: 'var(--text-muted)', padding: '3px 0' }}>
                                {o}
                              </li>
                            ))}
                          </ul>
                        )}
                        {s.keyTakeaway && (
                          <p style={{ fontSize: 13, marginTop: 10, color: 'var(--text-muted)' }}>
                            Key takeaway: <strong>{s.keyTakeaway}</strong>
                          </p>
                        )}
                      </>
                    );
                  } catch {
                    return <p style={{ fontSize: 14 }}>{String(course.aiSummary)}</p>;
                  }
                })()}
              </div>
            )}
          </div>
 
          {/* Sidebar */}
          <div className={styles.sidebar}>
            <h2 style={{ fontSize: 17, fontWeight: 600, marginBottom: 4 }}>
              {course.title}
            </h2>
            <p style={{ fontSize: 13, color: 'var(--text-muted)', marginBottom: 16 }}>
              by {course.instructorName}
            </p>
 
            <div className={styles.metaList}>
              {course.videoDuration > 0 && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Duration</span>
                  <span>{formatDuration(course.videoDuration)}</span>
                </div>
              )}
              <div className={styles.metaItem}>
                <span className={styles.metaLabel}>Status</span>
                <span className={`badge badge-${
                  isReady(course.uploadStatus) ? 'green' :
                  course.uploadStatus?.toUpperCase() === 'PROCESSING' ? 'amber' : 'teal'
                }`}>
                  {course.uploadStatus}
                </span>
              </div>
              <div className={styles.metaItem}>
                <span className={styles.metaLabel}>Published</span>
                <span className={`badge badge-${course.isPublished ? 'green' : 'amber'}`}>
                  {course.isPublished ? 'Yes' : 'Draft'}
                </span>
              </div>
            </div>
 
            {/* Progress bar */}
            {progress && progress.percentComplete > 0 && (
              <div className={styles.progressBig} style={{ marginBottom: 16 }}>
                <div className={styles.progressBigBar}>
                  <div
                    className={styles.progressBigFill}
                    style={{ width: `${progress.percentComplete}%` }}
                  />
                </div>
                <div className={styles.progressBigText}>
                  {progress.percentComplete}% complete
                  {progress.completed && ' ✅'}
                </div>
              </div>
            )}
 
            {/* Enrol / enrolled button */}
            {!isInstructor && (
              enrolled ? (
                <Button full variant="secondary" disabled>
                  ✅ Enrolled
                </Button>
              ) : (
                <>
                  <Button full loading={enrolling} onClick={handleEnrol}>
                    Enrol now — it's free
                  </Button>
                  {error && (
                    <p style={{ color: 'var(--red)', fontSize: 13, marginTop: 8 }}>
                      {error}
                    </p>
                  )}
                </>
              )
            )}
 
            {isInstructor && (
              <div style={{
                background: 'var(--teal-light)', color: 'var(--teal)',
                padding: '8px 12px', borderRadius: 'var(--radius-md)',
                fontSize: 13, fontWeight: 500, textAlign: 'center'
              }}>
                👨‍🏫 You are the instructor
              </div>
            )}
          </div>
        </div>
      </div>
 
      {canWatch &&  <AiChat courseId={course.id} />}
    </div>
  );
}
 
 