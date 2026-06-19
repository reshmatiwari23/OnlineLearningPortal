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
 
const PROGRESS_INTERVAL_MS = 5000;
 
export default function VideoPlayer({ courseId, src, startAt = 0, onProgress }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const videoElRef   = useRef<HTMLVideoElement>(null);
  const playerRef    = useRef<Player | null>(null);
  const intervalRef  = useRef<ReturnType<typeof setInterval> | null>(null);
 
  const stopInterval = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
  }, []);
 
  useEffect(() => {
    // Guard: element must be in the DOM
    if (!videoElRef.current) return;
    // Guard: do not initialise twice
    if (playerRef.current) return;
    // Guard: must have a src
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
        const current  = Math.floor(player.currentTime() ?? 0);
        const duration = Math.floor(player.duration()    ?? 0);
        if (duration > 0) {
          progressService.update(courseId, {
            currentTimeSecs: current,
            durationSecs:    duration,
          })
          .then(p => { if (p && onProgress) onProgress(p.percentComplete); })
          .catch(() => {});
        }
      }, PROGRESS_INTERVAL_MS);
    });
 
    player.on('pause', stopInterval);
    player.on('ended', stopInterval);
 
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
    <div ref={containerRef} className={styles.wrapper}>
      <div data-vjs-player>
        {/* Use a real <video> element ref — avoids StrictMode double-mount issue */}
        <video
          ref={videoElRef}
          className="video-js vjs-big-play-centered"
          playsInline
        />
      </div>
    </div>
  );
}
 
 