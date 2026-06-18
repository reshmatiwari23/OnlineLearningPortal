import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import styles from './Navbar.module.css';

export default function Navbar() {
  const { user, logout, isInstructor } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <nav className={styles.nav}>
      <div className={`container ${styles.inner}`}>
        <NavLink to="/" className={styles.brand}>
          📚 OLP
        </NavLink>

        <div className={styles.links}>
          <NavLink
            to="/courses"
            className={({ isActive }) =>
              `${styles.link} ${isActive ? styles.active : ''}`
            }
          >
            Courses
          </NavLink>

          {isInstructor && (
            <NavLink
              to="/instructor"
              className={({ isActive }) =>
                `${styles.link} ${isActive ? styles.active : ''}`
              }
            >
              My Courses
            </NavLink>
          )}

          {!isInstructor && (
            <NavLink
              to="/my-learning"
              className={({ isActive }) =>
                `${styles.link} ${isActive ? styles.active : ''}`
              }
            >
              My Learning
            </NavLink>
          )}
        </div>

        {user && (
          <div className={styles.right}>
            <span className={styles.userName}>
              {user.name} · {user.role === 'instructor' ? 'Instructor' : 'Learner'}
            </span>
            <button className={styles.logoutBtn} onClick={handleLogout}>
              Sign out
            </button>
          </div>
        )}
      </div>
    </nav>
  );
}
