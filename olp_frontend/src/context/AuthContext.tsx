import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import type { User, AuthResponse } from '../types';

interface AuthContextValue {
  user: User | null;
  isLoading: boolean;
  login: (auth: AuthResponse) => void;
  logout: () => void;
  isInstructor: boolean;
  isLearner: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Restore session from localStorage on app startup
  useEffect(() => {
    try {
      const stored = localStorage.getItem('olp_user');
      const token  = localStorage.getItem('olp_token');
      if (stored && token) {
        const parsed: User = JSON.parse(stored);
        setUser({ ...parsed, token });
      }
    } catch {
      localStorage.removeItem('olp_user');
      localStorage.removeItem('olp_token');
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback((auth: AuthResponse) => {
    const u: User = {
      userId: auth.userId,
      email:  auth.email,
      name:   auth.name,
      role:   auth.role,
      token:  auth.token,
    };
    localStorage.setItem('olp_token', auth.token);
    localStorage.setItem('olp_user',  JSON.stringify(u));
    setUser(u);
  }, []);

  const logout = useCallback(() => {
    localStorage.removeItem('olp_token');
    localStorage.removeItem('olp_user');
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{
      user,
      isLoading,
      login,
      logout,
      isInstructor: user?.role === 'instructor',
      isLearner:    user?.role === 'user',
    }}>
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
