import { useState, useRef, useEffect, useCallback } from 'react';
import { useAuth } from '../../context/AuthContext';
import { aiService } from '../../services/aiService';
import type { Citation } from '../../types';
import styles from './AiChat.module.css';

interface Message {
  id: string;
  role: 'user' | 'ai';
  text: string;
  citations?: Citation[];
  streaming?: boolean;
}

interface Props {
  courseId: string;
}

export default function AiChat({ courseId }: Props) {
  const { user } = useAuth();
  const [open, setOpen]       = useState(false);
  const [messages, setMessages] = useState<Message[]>([
    {
      id: '0',
      role: 'ai',
      text: 'Hi! I am your AI course assistant. Ask me anything about this course content.',
    }
  ]);
  const [input, setInput]     = useState('');
  const [streaming, setStreaming] = useState(false);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const cleanupRef     = useRef<(() => void) | null>(null);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // Cleanup SSE connection on unmount
  useEffect(() => {
    return () => { cleanupRef.current?.(); };
  }, []);

  const sendMessage = useCallback(() => {
    const q = input.trim();
    if (!q || streaming || !user) return;

    setInput('');
    setStreaming(true);

    // Add user message
    const userMsgId = Date.now().toString();
    setMessages(prev => [...prev, { id: userMsgId, role: 'user', text: q }]);

    // Add placeholder AI message that will be filled by streaming tokens
    const aiMsgId = (Date.now() + 1).toString();
    setMessages(prev => [...prev, { id: aiMsgId, role: 'ai', text: '', streaming: true }]);

    // Start SSE stream
    const cleanup = aiService.streamChat(
      q,
      courseId,
      sessionId,
      user.token,
      // onToken — append each word to the AI message
      (token) => {
        setMessages(prev => prev.map(m =>
          m.id === aiMsgId ? { ...m, text: m.text + token } : m
        ));
      },
      // onCitations — attach citations to the AI message
      (citations) => {
        setMessages(prev => prev.map(m =>
          m.id === aiMsgId ? { ...m, citations } : m
        ));
      },
      // onDone — mark streaming as complete
      () => {
        setMessages(prev => prev.map(m =>
          m.id === aiMsgId ? { ...m, streaming: false } : m
        ));
        setStreaming(false);
        // Generate a session ID for multi-turn conversation
        if (!sessionId) setSessionId(Date.now().toString());
      },
      // onError
      (errMsg) => {
        setMessages(prev => prev.map(m =>
          m.id === aiMsgId ? { ...m, text: errMsg, streaming: false } : m
        ));
        setStreaming(false);
      }
    );

    cleanupRef.current = cleanup;
  }, [input, streaming, user, courseId, sessionId]);

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  };

  return (
    <>
      {/* Floating toggle button */}
      <button
        className={styles.toggle}
        onClick={() => setOpen(o => !o)}
        aria-label="Toggle AI course assistant"
      >
        🤖 Ask AI
      </button>

      {/* Sliding drawer */}
      <div className={`${styles.drawer} ${open ? styles.open : ''}`} role="dialog" aria-label="AI course assistant">
        <div className={styles.header}>
          <h3>🤖 AI Course Assistant</h3>
          <button className={styles.closeBtn} onClick={() => setOpen(false)} aria-label="Close">×</button>
        </div>

        <div className={styles.messages} aria-live="polite">
          {messages.map(msg => (
            <div key={msg.id} className={styles.msg}>
              {msg.role === 'user' ? (
                <div className={styles.msgUser}>{msg.text}</div>
              ) : (
                <div className={styles.msgAi}>
                  <div className={styles.msgAiInner}>
                    {msg.text}
                    {msg.streaming && <span className={styles.cursor} aria-hidden="true" />}
                  </div>
                  {msg.citations && msg.citations.length > 0 && (
                    <div className={styles.citations}>
                      <div className={styles.citationTitle}>Sources from this course:</div>
                      {msg.citations.map((c, i) => (
                        <span
                          key={i}
                          className={styles.citation}
                          title={c.excerpt}
                          onClick={() => {
                            // Future: seek video player to c.timestampSeconds
                            console.log('Seek to', c.timestampSeconds, 'seconds');
                          }}
                        >
                          📍 {c.timestampSeconds != null
                              ? `${Math.floor(c.timestampSeconds / 60)}:${String(c.timestampSeconds % 60).padStart(2,'0')}`
                              : `Source ${i + 1}`}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
          <div ref={messagesEndRef} />
        </div>

        <div className={styles.inputRow}>
          <textarea
            className={styles.input}
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Ask about this course… (Enter to send)"
            disabled={streaming}
            rows={1}
          />
          <button
            className={styles.sendBtn}
            onClick={sendMessage}
            disabled={streaming || !input.trim()}
            aria-label="Send message"
          >
            ➤
          </button>
        </div>
      </div>
    </>
  );
}
