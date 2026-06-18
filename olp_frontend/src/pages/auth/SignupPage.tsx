import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { authService } from '../../services/authService';
import Button from '../../components/common/Button';
import Input from '../../components/common/Input';
import styles from './Auth.module.css';

export default function SignupPage() {
  const { login } = useAuth();
  const navigate  = useNavigate();

  const [name,     setName]     = useState('');
  const [email,    setEmail]    = useState('');
  const [password, setPassword] = useState('');
  const [role,     setRole]     = useState<'user' | 'instructor'>('user');
  const [error,    setError]    = useState('');
  const [loading,  setLoading]  = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password.length < 8) { setError('Password must be at least 8 characters'); return; }
    setError('');
    setLoading(true);
    try {
      const auth = await authService.signup({ name, email, password, role });
      login(auth);
      navigate(role === 'instructor' ? '/instructor' : '/courses');
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message ?? 'Could not create account. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <div className={styles.logo}>
          <h1>📚 Online Learning Portal</h1>
          <p>Create your account</p>
        </div>

        {error && <div className={styles.error}>{error}</div>}

        <form className={styles.form} onSubmit={handleSubmit}>
          <Input
            label="Full name"
            type="text"
            value={name}
            onChange={e => setName(e.target.value)}
            placeholder="Reshma Tiwari"
            required autoFocus
          />
          <Input
            label="Email address"
            type="email"
            value={email}
            onChange={e => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />
          <Input
            label="Password (min 8 characters)"
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="••••••••"
            required
          />

          <div>
            <p style={{ fontSize: 13, fontWeight: 500, marginBottom: 8 }}>I am a:</p>
            <div className={styles.roleRow}>
              <button
                type="button"
                className={`${styles.roleBtn} ${role === 'user' ? styles.selected : ''}`}
                onClick={() => setRole('user')}
              >
                🎓 Learner
              </button>
              <button
                type="button"
                className={`${styles.roleBtn} ${role === 'instructor' ? styles.selected : ''}`}
                onClick={() => setRole('instructor')}
              >
                🏫 Instructor
              </button>
            </div>
          </div>

          <Button type="submit" full loading={loading}>
            Create account
          </Button>
        </form>

        <div className={styles.footer}>
          Already have an account?{' '}
          <Link to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
