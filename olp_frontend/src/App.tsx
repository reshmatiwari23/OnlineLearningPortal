import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import Navbar from './components/layout/Navbar';

// Pages
import LoginPage              from './pages/auth/LoginPage';
import SignupPage             from './pages/auth/SignupPage';
import CourseCataloguePage    from './pages/learner/CourseCataloguePage';
import CourseDetailPage       from './pages/learner/CourseDetailPage';
import MyLearningPage         from './pages/learner/MyLearningPage';
import InstructorDashboardPage from './pages/instructor/InstructorDashboardPage';

/**
 * Protects routes that require authentication.
 * Redirects to /login if not logged in.
 * Redirects instructors away from learner-only routes.
 */
function ProtectedRoute({
  children,
  requireInstructor = false,
}: {
  children: React.ReactNode;
  requireInstructor?: boolean;
}) {
  const { user, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
        <div className="spinner" style={{ width: 36, height: 36 }} />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requireInstructor && user.role !== 'instructor') {
    return <Navigate to="/courses" replace />;
  }

  return <>{children}</>;
}

export default function App() {
  const { user } = useAuth();

  return (
    <>
      {user && <Navbar />}

      <Routes>
        {/* Public routes */}
        <Route
          path="/login"
          element={user ? <Navigate to={user.role === 'instructor' ? '/instructor' : '/courses'} /> : <LoginPage />}
        />
        <Route
          path="/signup"
          element={user ? <Navigate to={user.role === 'instructor' ? '/instructor' : '/courses'} /> : <SignupPage />}
        />

        {/* Shared — any authenticated user */}
        <Route
          path="/courses"
          element={<ProtectedRoute><CourseCataloguePage /></ProtectedRoute>}
        />
        <Route
          path="/courses/:id"
          element={<ProtectedRoute><CourseDetailPage /></ProtectedRoute>}
        />

        {/* Learner only */}
        <Route
          path="/my-learning"
          element={<ProtectedRoute><MyLearningPage /></ProtectedRoute>}
        />

        {/* Instructor only */}
        <Route
          path="/instructor"
          element={
            <ProtectedRoute requireInstructor>
              <InstructorDashboardPage />
            </ProtectedRoute>
          }
        />

        {/* Default redirect */}
        <Route
          path="/"
          element={
            user
              ? <Navigate to={user.role === 'instructor' ? '/instructor' : '/courses'} />
              : <Navigate to="/login" />
          }
        />
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </>
  );
}
