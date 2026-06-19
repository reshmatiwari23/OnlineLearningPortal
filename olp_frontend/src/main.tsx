import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import App from './App';
import './styles/global.css';
 
ReactDOM.createRoot(document.getElementById('root')!).render(
  // StrictMode removed — it causes Video.js to mount twice in development
  // which triggers "element not in DOM" warning and breaks the player
  <BrowserRouter>
    <AuthProvider>
      <App />
    </AuthProvider>
  </BrowserRouter>
);
 
 