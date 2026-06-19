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
 
const PROGRESS_INTERVAL_MS = 2000; // every 2 seconds — catches end of short videos
 
export default function VideoPlayer({ courseId, src, startAt = 0, onProgress }: Props) {
  const videoElRef  = useRef<HTMLVideoElement>(null);
  const playerRef   = useRef<Player | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
 
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
 
    try {
      const p = await progressService.update(courseId, {
        currentTimeSecs: current,
        durationSecs:    duration,
      });
      if (p && onProgress) onProgress(p.percentComplete);
    } catch {
      // Ignore progress update errors silently
    }
  }, [courseId, onProgress]);
 
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
      if (startAt > 0) player.currentTime(startAt);
    });
 
    player.on('play', () => {
      stopInterval();
      intervalRef.current = setInterval(() => {
        sendProgress(player);
      }, PROGRESS_INTERVAL_MS);
    });
 
    player.on('pause', () => {
      stopInterval();
      // Send progress on pause so position is saved
      sendProgress(player);
    });
 
    player.on('ended', async () => {
      stopInterval();
      // Force 100% on video end regardless of polling timing
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
 
 