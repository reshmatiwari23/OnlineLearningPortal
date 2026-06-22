import type { Citation, Recommendation } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export const aiService = {

  streamChat(
    question: string,
    courseId: string,
    sessionId: string | null,
    token: string,
    onToken: (text: string) => void,
    onCitations: (citations: Citation[]) => void,
    onDone: () => void,
    onError: (msg: string) => void,
  ): () => void {
    const controller = new AbortController();

    // Get user info from localStorage for headers
    const userStr = localStorage.getItem('olp_user');
    let userId = '';
    let userRole = 'user';
    if (userStr) {
      try {
        const u = JSON.parse(userStr);
        userId = u.userId ?? '';
        userRole = u.role ?? 'user';
      } catch {}
    }

    fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Accept': 'text/event-stream',
        'x-user-id': userId,
        'x-user-role': userRole,
      },
      body: JSON.stringify({ question, courseId, sessionId }),
      signal: controller.signal,
    }).then(async (response) => {
      if (!response.ok) {
        onError('AI service unavailable. Please try again.');
        return;
      }

      const reader = response.body?.getReader();
      if (!reader) { onError('Stream unavailable.'); return; }

      const decoder = new TextDecoder();
      let buffer = '';
      let lastEvent = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) { onDone(); break; }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            lastEvent = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            if (lastEvent === 'error' || data === 'Error occurred') {
              onError('AI service unavailable. Please try again.');
            } else if (lastEvent === 'citations') {
              try {
                const citations: Citation[] = JSON.parse(data);
                onCitations(citations);
              } catch { /* ignore */ }
            } else if (lastEvent === 'token' || lastEvent === '') {
              if (data && data !== '[]') {
                // Check if it's citations JSON
                if (data.startsWith('[') && data.includes('chunkId')) {
                  try {
                    const citations: Citation[] = JSON.parse(data);
                    onCitations(citations);
                  } catch { /* ignore */ }
                } else {
                  onToken(data + ' ');
                }
              }
            }
            lastEvent = '';
          }
        }
      }
    }).catch((err) => {
      if (err.name !== 'AbortError') {
        onError('Connection lost. Please try again.');
      }
    });

    return () => controller.abort();
  },

  async getRecommendations(
    enrolledTopics: string[],
    token: string
  ): Promise<Recommendation[]> {
    const params = new URLSearchParams();
    enrolledTopics.forEach(t => params.append('enrolledTopics', t));
    params.append('candidates', '[]');

    const res = await fetch(`${API_BASE}/ai/recommend?${params}`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    const json = await res.json();
    return json.data ?? [];
  },
};