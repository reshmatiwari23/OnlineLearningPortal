import { useEffect, useRef } from 'react';
import videojs from 'video.js';
import type Player from 'video.js/dist/types/player';
import { progressService } from '../../services/enrollmentService';
import styles from './VideoPlayer.module.css';

interface Props {
  courseId: string;
  src: string;         // CloudFront video URL
  startAt?: number;    // resume from last position (seconds)
  onProgress?: (percent: number) => void;
}

// Report progress every 5 seconds — matches the backend design
const PROGRESS_INTERVAL_MS = 5000;

export default function VideoPlayer({ courseId, src, startAt = 0, onProgress }: Props) {
  const videoRef    = useRef<HTMLDivElement>(null);
  const playerRef   = useRef<Player | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!videoRef.current || playerRef.current) return;

    // Initialise Video.js player
    const player = videojs(videoRef.current, {
      controls:    true,
      responsive:  true,
      fluid:       true,
      playbackRates: [0.75, 1, 1.25, 1.5, 2],
      sources: [{ src, type: 'video/mp4' }],
    });

    playerRef.current = player;

    // Seek to last position when metadata is loaded
    player.on('loadedmetadata', () => {
      if (startAt > 0) player.currentTime(startAt);
    });

    // Send progress to API every 5 seconds while playing
    player.on('play', () => {
      intervalRef.current = setInterval(() => {
        const current  = Math.floor(player.currentTime() ?? 0);
        const duration = Math.floor(player.duration()    ?? 0);
        if (duration > 0) {
          progressService.update(courseId, {
            currentTimeSecs: current,
            durationSecs:    duration,
          }).then((p) => onProgress?.(p.percentComplete));
        }
      }, PROGRESS_INTERVAL_MS);
    });

    // Stop interval on pause or ended
    const stopInterval = () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
    player.on('pause', stopInterval);
    player.on('ended', stopInterval);

    return () => {
      stopInterval();
      if (playerRef.current && !playerRef.current.isDisposed()) {
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  }, [courseId, src, startAt, onProgress]);

  return (
    <div className={styles.wrapper}>
      <div data-vjs-player>
        <div ref={videoRef} className="video-js vjs-big-play-centered" />
      </div>
    </div>
  );
}
