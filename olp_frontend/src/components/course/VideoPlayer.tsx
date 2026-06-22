import { useEffect, useRef, useCallback } from 'react';
import videojs from 'video.js';
import type Player from 'video.js/dist/types/player';
import { progressService } from '../../services/enrollmentService';
import styles from './VideoPlayer.module.css';

interface Props {
  courseId: string;
  src: string;
  startAt?: number;
  onProgress?: (percent: number) => void;
}

// For short videos send progress every 5 seconds
// For long videos send every 15 seconds
const getInterval = (duration: number) => {
  if (duration <= 120) return 5000;   // <= 2 min → every 5s
  if (duration <= 600) return 10000;  // <= 10 min → every 10s
  return 15000;                        // > 10 min → every 15s
};

export default function VideoPlayer({ courseId, src, startAt = 0, onProgress }: Props) {
  const videoElRef  = useRef<HTMLVideoElement>(null);
  const playerRef   = useRef<Player | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const lastSentRef = useRef<number>(-1);
  const durationRef = useRef<number>(0);

  const stopInterval = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);

  const sendProgress = useCallback(async (player: Player, forceComplete = false) => {
    const current  = forceComplete
      ? Math.floor(player.duration() ?? 0)
      : Math.floor(player.currentTime() ?? 0);
    const duration = Math.floor(player.duration() ?? 0);

    if (duration <= 0) return;

    // Skip if position hasn't changed by at least 3 seconds
    if (!forceComplete && Math.abs(current - lastSentRef.current) < 3) return;

    lastSentRef.current = current;

    try {
      const p = await progressService.update(courseId, {
        currentTimeSecs: current,
        durationSecs:    duration,
      });
      console.log(`✅ Progress: ${p?.percentComplete}% (${current}s / ${duration}s)`);
      if (p && onProgress) onProgress(p.percentComplete);
    } catch (err) {
      console.warn('Progress update failed:', err);
    }
  }, [courseId, onProgress]);

  const startInterval = useCallback((player: Player) => {
    stopInterval();
    const duration = Math.floor(player.duration() ?? 0);
    durationRef.current = duration;
    const intervalMs = getInterval(duration);
    console.log(`⏱ Progress interval: ${intervalMs}ms for ${duration}s video`);
    intervalRef.current = setInterval(() => {
      sendProgress(player);
    }, intervalMs);
  }, [stopInterval, sendProgress]);

  useEffect(() => {
    if (!videoElRef.current) return;
    if (playerRef.current) return;
    if (!src) return;

    const player = videojs(videoElRef.current, {
      controls:      true,
      responsive:    true,
      fluid:         true,
      playbackRates: [0.75, 1, 1.25, 1.5, 2],
      sources:       [{ src, type: 'video/mp4' }],
    });

    playerRef.current = player;

    player.on('loadedmetadata', () => {
      // Restore position from last saved progress
      if (startAt > 0) {
        console.log(`▶ Resuming from ${startAt}s`);
        player.currentTime(startAt);
      }
    });

    player.on('play', () => {
      startInterval(player);
      // Send immediately on play so resume position is captured
      sendProgress(player);
    });

    player.on('pause', () => {
      stopInterval();
      sendProgress(player);
    });

    player.on('seeked', () => {
      // Always save on seek so resume position is updated
      sendProgress(player);
    });

    player.on('timeupdate', () => {
      // Extra safety — save every 20% of short videos
      const duration = Math.floor(player.duration() ?? 0);
      const current = Math.floor(player.currentTime() ?? 0);
      if (duration > 0 && duration <= 120) {
        const percent = (current / duration) * 100;
        const lastPercent = (lastSentRef.current / duration) * 100;
        // Save at 25%, 50%, 75% milestones
        if (Math.floor(percent / 25) > Math.floor(lastPercent / 25)) {
          sendProgress(player);
        }
      }
    });

    player.on('ended', async () => {
      stopInterval();
      await sendProgress(player, true);
      if (onProgress) onProgress(100);
    });

    player.on('error', () => {
      console.error('VideoPlayer error — src:', src);
    });

    return () => {
      stopInterval();
      if (playerRef.current && !playerRef.current.isDisposed()) {
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [src, courseId]);

  if (!src) return null;

  return (
    <div className={styles.wrapper}>
      <div data-vjs-player>
        <video
          ref={videoElRef}
          className="video-js vjs-big-play-centered"
          playsInline
        />
      </div>
    </div>
  );
}