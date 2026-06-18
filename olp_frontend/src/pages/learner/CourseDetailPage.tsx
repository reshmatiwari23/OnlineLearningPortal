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
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m} min`;
}

export default function CourseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { user, isInstructor } = useAuth();

  const [course,   setCourse]   = useState<Course | null>(null);
  const [enrolled, setEnrolled] = useState(false);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [loading,  setLoading]  = useState(true);
  const [enrolling, setEnrolling] = useState(false);

  useEffect(() => {
    if (!id) return;
    Promise.all([
      courseService.getById(id),
      user ? enrollmentService.isEnrolled(id) : Promise.resolve(false),
      user ? progressService.get(id).catch(() => null) : Promise.resolve(null),
    ]).then(([c, e, p]) => {
      setCourse(c);
      setEnrolled(e);
      setProgress(p);
    }).finally(() => setLoading(false));
  }, [id, user]);

  const handleEnrol = async () => {
    if (!id) return;
    setEnrolling(true);
    try {
      await enrollmentService.enrol(id);
      setEnrolled(true);
    } finally {
      setEnrolling(false);
    }
  };

  const handleProgress = useCallback((percent: number) => {
    setProgress(prev => prev ? { ...prev, percentComplete: percent } : null);
  }, []);

  if (loading) return <div className={styles.center}><div className="spinner" /></div>;
  if (!course) return <div className={styles.center}><p>Course not found.</p></div>;

  const canWatch = enrolled || isInstructor;

  return (
    <div className={styles.page}>
      <div className="container">
        {canWatch && course.videoUrl && (
          <div style={{ marginBottom: 32 }}>
            <VideoPlayer
              courseId={course.id}
              src={course.videoUrl}
              startAt={progress?.currentTimeSecs ?? 0}
              onProgress={handleProgress}
            />
          </div>
        )}

        <div className={styles.detailLayout}>
          {/* Main content */}
          <div>
            <h1 style={{ fontSize: 26, fontWeight: 700, color: 'var(--navy)', marginBottom: 8 }}>
              {course.title}
            </h1>
            <p style={{ color: 'var(--text-muted)', fontSize: 15, marginBottom: 20 }}>
              {course.description}
            </p>

            {course.aiSummary && (
              <div style={{
                background: 'var(--teal-light)',
                border: '1px solid var(--teal)',
                borderRadius: 'var(--radius-lg)',
                padding: 20,
                marginBottom: 20,
              }}>
                <h3 style={{ fontSize: 15, fontWeight: 600, color: 'var(--teal)', marginBottom: 12 }}>
                  🤖 AI Course Summary
                </h3>
                <p style={{ fontSize: 14, marginBottom: 12 }}>{course.aiSummary.summary}</p>
                <div className={styles.objectives}>
                  <h3>Learning objectives</h3>
                  <ul>{course.aiSummary.objectives.map((o, i) => <li key={i}>{o}</li>)}</ul>
                </div>
                <p style={{ fontSize: 13, marginTop: 12, color: 'var(--text-muted)' }}>
                  Key takeaway: <strong>{course.aiSummary.keyTakeaway}</strong>
                </p>
              </div>
            )}
          </div>

          {/* Sidebar */}
          <div className={styles.sidebar}>
            <h2 style={{ fontSize: 17, fontWeight: 600, marginBottom: 4 }}>
              {course.title}
            </h2>
            <p style={{ fontSize: 13, color: 'var(--text-muted)' }}>
              by {course.instructorName}
            </p>

            <div className={styles.metaList}>
              {course.videoDuration > 0 && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Duration</span>
                  <span>{formatDuration(course.videoDuration)}</span>
                </div>
              )}
              {course.aiSummary && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>Level</span>
                  <span className={`badge badge-${
                    course.aiSummary.difficulty === 'beginner'     ? 'green' :
                    course.aiSummary.difficulty === 'intermediate' ? 'amber' : 'red'
                  }`}>{course.aiSummary.difficulty}</span>
                </div>
              )}
            </div>

            {progress && (
              <div className={styles.progressBig}>
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

            {!isInstructor && (
              enrolled ? (
                <Button full variant="secondary" disabled>
                  ✅ Enrolled
                </Button>
              ) : (
                <Button full loading={enrolling} onClick={handleEnrol}>
                  Enrol now — it's free
                </Button>
              )
            )}
          </div>
        </div>
      </div>

      {/* AI Chat drawer — visible when enrolled */}
      {canWatch && course.kbIngested && <AiChat courseId={course.id} />}
    </div>
  );
}
