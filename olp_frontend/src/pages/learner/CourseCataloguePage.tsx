import { useState, useEffect } from 'react';
import { courseService } from '../../services/courseService';
import CourseCard from '../../components/course/CourseCard';
import Button from '../../components/common/Button';
import type { Course } from '../../types';
import styles from './Learner.module.css';

export default function CourseCataloguePage() {
  const [courses,  setCourses]  = useState<Course[]>([]);
  const [page,     setPage]     = useState(0);
  const [hasMore,  setHasMore]  = useState(true);
  const [loading,  setLoading]  = useState(true);
  const [search,   setSearch]   = useState('');
  const [query,    setQuery]    = useState('');

  useEffect(() => {
    setLoading(true);
    const fetch = query
      ? courseService.search(query, 0)
      : courseService.getAll(0);

    fetch.then(p => {
      setCourses(p.content);
      setHasMore(!p.last);
      setPage(0);
    }).finally(() => setLoading(false));
  }, [query]);

  const loadMore = () => {
    const nextPage = page + 1;
    const fetch = query
      ? courseService.search(query, nextPage)
      : courseService.getAll(nextPage);

    fetch.then(p => {
      setCourses(prev => [...prev, ...p.content]);
      setHasMore(!p.last);
      setPage(nextPage);
    });
  };

  return (
    <div className={styles.page}>
      <div className="container">
        <div className={styles.pageHeader}>
          <h1>Course Catalogue</h1>
          <p>Discover courses built by our instructors</p>
        </div>

        <div className={styles.searchRow}>
          <input
            className={styles.searchInput}
            value={search}
            onChange={e => setSearch(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && setQuery(search)}
            placeholder="Search courses…"
          />
          <Button onClick={() => setQuery(search)} size="sm">Search</Button>
          {query && (
            <Button variant="secondary" size="sm" onClick={() => { setQuery(''); setSearch(''); }}>
              Clear
            </Button>
          )}
        </div>

        {loading ? (
          <div className={styles.center}><div className="spinner" /></div>
        ) : courses.length === 0 ? (
          <div className={styles.empty}>
            <p>No courses found{query ? ` for "${query}"` : ''}.</p>
          </div>
        ) : (
          <>
            <div className={styles.grid}>
              {courses.map(c => <CourseCard key={c.id} course={c} />)}
            </div>
            {hasMore && (
              <div className={styles.center} style={{ marginTop: 32 }}>
                <Button variant="secondary" onClick={loadMore}>Load more</Button>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
