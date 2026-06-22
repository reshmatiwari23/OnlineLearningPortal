import { useNavigate } from 'react-router-dom';
import type { Course } from '../../types';
import styles from './CourseCard.module.css';

interface Props {
  course: Course;
  progress?: number;
  showStatus?: boolean;
}

function formatDuration(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  return `${m} min`;
}

function getAiSummaryText(aiSummary: string | undefined): string {
  if (!aiSummary) return '';
  try {
    const s = typeof aiSummary === 'string' ? JSON.parse(aiSummary) : aiSummary;
    if (s?.difficulty && s?.summary) {
      return `${s.difficulty} · ${String(s.summary).slice(0, 80)}…`;
    }
    return '';
  } catch {
    return '';
  }
}

export default function CourseCard({ course, progress, showStatus }: Props) {
  const navigate = useNavigate();
  const summaryText = getAiSummaryText(course.aiSummary);

  return (
    <div
      className={styles.card}
      onClick={() => navigate(`/courses/${course.id}`)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && navigate(`/courses/${course.id}`)}
      aria-label={`Open ${course.title}`}
    >
      <div className={styles.thumb}>
        {course.thumbnailUrl
          ? <img src={course.thumbnailUrl} alt={course.title} />
          : <span>📹</span>}
      </div>

      <div className={styles.body}>
        <div className={styles.title}>{course.title}</div>
        <div className={styles.instructor}>by {course.instructorName}</div>

        {showStatus && (
          <span className={`badge badge-${
            course.uploadStatus?.toUpperCase() === 'READY'      ? 'green' :
            course.uploadStatus?.toUpperCase() === 'PROCESSING' ? 'amber' :
            course.uploadStatus?.toUpperCase() === 'FAILED'     ? 'red'   : 'teal'
          }`}>
            {course.uploadStatus}
          </span>
        )}

        {summaryText && (
          <p style={{ fontSize: 12, color: 'var(--text-muted)', marginTop: 6 }}>
            {summaryText}
          </p>
        )}

        <div className={styles.footer}>
          <span className={styles.duration}>
            {course.videoDuration ? formatDuration(course.videoDuration) : 'No video yet'}
          </span>
          {course.isPublished
            ? <span className="badge badge-green">Published</span>
            : <span className="badge badge-amber">Draft</span>}
        </div>

        {typeof progress === 'number' && (
          <div className={styles.progress}>
            <div className={styles.progressBar}>
              <div className={styles.progressFill} style={{ width: `${progress}%` }} />
            </div>
            <div className={styles.progressText}>{progress}% complete</div>
          </div>
        )}
      </div>
    </div>
  );
}