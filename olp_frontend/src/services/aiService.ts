import type { Citation, Recommendation } from '../types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || '';

export const aiService = {

  /**
   * Open an SSE connection to /ai/chat.
   * Calls onToken for each streamed token,
   * calls onCitations when the full citations array arrives,
   * calls onDone when the stream completes.
   *
   * Returns a cleanup function — call it to close the connection.
   */
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
    // SSE requires a GET or a POST with EventSource
    // Since EventSource only supports GET, we use fetch with ReadableStream
    const controller = new AbortController();

    fetch(`${API_BASE}/ai/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Accept': 'text/event-stream',
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

      while (true) {
        const { done, value } = await reader.read();
        if (done) { onDone(); break; }

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const data = line.slice(5).trim();
            // Check for event type on the preceding line
          } else if (line.startsWith('event:citations')) {
            // next data line will be citations JSON
          } else if (line.startsWith('event:token')) {
            // next data line will be a token
          } else if (line.startsWith('data:') && line.includes('"chunkId"')) {
            try {
              const citations: Citation[] = JSON.parse(line.slice(5).trim());
              onCitations(citations);
            } catch { /* not citations JSON */ }
          } else if (line.startsWith('data:') && !line.includes('{')) {
            onToken(line.slice(5));
          }
        }
      }
    }).catch((err) => {
      if (err.name !== 'AbortError') {
        onError('Connection lost. Please try again.');
      }
    });

    // Return cleanup function
    return () => controller.abort();
  },

  async getRecommendations(
    enrolledTopics: string[],
    token: string
  ): Promise<Recommendation[]> {
    const params = new URLSearchParams();
    enrolledTopics.forEach(t => params.append('enrolledTopics', t));
    params.append('candidates', '[]'); // simplified — real app passes course list

    const res = await fetch(`${API_BASE}/ai/recommend?${params}`, {
      headers: { 'Authorization': `Bearer ${token}` },
    });
    const json = await res.json();
    return json.data ?? [];
  },
};
