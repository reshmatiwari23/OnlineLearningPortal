import { useState, useEffect } from 'react';
import { enrollmentService, progressService } from '../../services/enrollmentService';
import { courseService } from '../../services/courseService';
import CourseCard from '../../components/course/CourseCard';
import type { Course, Progress } from '../../types';
import styles from './Learner.module.css';

export default function MyLearningPage() {
  const [courses,    setCourses]    = useState<Course[]>([]);
  const [progressMap, setProgressMap] = useState<Record<string, number>>({});
  const [loading,    setLoading]    = useState(true);

  useEffect(() => {
    Promise.all([
      enrollmentService.getMyEnrollments(),
      progressService.getAll(),
    ]).then(async ([enrollments, allProgress]) => {
      // Build progress map: courseId → percentComplete
      const pMap: Record<string, number> = {};
      allProgress.forEach((p: Progress) => { pMap[p.courseId] = p.percentComplete; });
      setProgressMap(pMap);

      // Fetch course details for each enrollment
      const courseDetails = await Promise.all(
        enrollments.map(e => courseService.getById(e.courseId).catch(() => null))
      );
      setCourses(courseDetails.filter((c): c is Course => c !== null));
    }).finally(() => setLoading(false));
  }, []);

  if (loading) return <div className={styles.center}><div className="spinner" /></div>;

  return (
    <div className={styles.page}>
      <div className="container">
        <div className={styles.pageHeader}>
          <h1>My Learning</h1>
          <p>Courses you are enrolled in</p>
        </div>

        {courses.length === 0 ? (
          <div className={styles.empty}>
            <p style={{ fontSize: 40, marginBottom: 12 }}>📚</p>
            <p>You haven't enrolled in any courses yet.</p>
            <p style={{ marginTop: 8 }}>
              <a href="/courses">Browse the catalogue</a> to get started.
            </p>
          </div>
        ) : (
          <div className={styles.grid}>
            {courses.map(c => (
              <CourseCard
                key={c.id}
                course={c}
                progress={progressMap[c.id] ?? 0}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
