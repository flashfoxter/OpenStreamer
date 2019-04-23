/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import com.lk.openstreamer.log.Logger;


public class SpeedControlCallback implements MoviePlayer.FrameCallback {

    private static final boolean CHECK_SLEEP_TIME = false;

    private static final long ONE_MILLION = 1000000L;
    private static final String WEIRD_VIDEO_TIMES_WENT_BACKWARD = "Weird, video times went backward";
    private static final String WARNING_CURRENT_FRAME_AND_PREVIOUS_FRAME_HAD_SAME_TIMESTAMP = "Warning: current frame and previous frame had same timestamp";
    private static final String INTER_FRAME_PAUSE_WAS = "Inter-frame pause was ";
    private static final String SEC_CAPPING_AT_5_SEC = "sec, capping at 5 sec";

    private long preventUnSec;
    private long preMonoUSec;
    private long fixedDurationUSec;
    private boolean loopReset;

    /**
     * Sets a fixed playback rate.  If set, this will ignore the presentation time stamp
     * in the video file.  Must be called before playback thread starts.
     */
    public void setFixedPlaybackRate(int fps) {
        fixedDurationUSec = ONE_MILLION / fps;
    }

    // runs on decode thread
    @Override
    public void preRender(long presentationTimeUsec) {
        // For the first frame, we grab the presentation time from the video
        // and the current monotonic clock time.  For subsequent frames, we
        // sleep for a bit to try to ensure that we're rendering frames at the
        // pace dictated by the video stream.
        //
        // If the frame rate is faster than vsync we should be dropping frames.  On
        // Android 4.4 this may not be happening.

        if (preMonoUSec == 0) {
            // Latch current values, then return immediately.
            preMonoUSec = System.nanoTime() / 1000;
            preventUnSec = presentationTimeUsec;
        } else {
            // Compute the desired time delta between the previous frame and this frame.
            long frameDelta;
            if (loopReset) {
                // We don't get an indication of how long the last frame should appear
                // on-screen, so we just throw a reasonable value in.  We could probably
                // do better by using a previous frame duration or some sort of average;
                // for now we just use 30fps.
                preventUnSec = presentationTimeUsec - ONE_MILLION / 30;
                loopReset = false;
            }
            if (fixedDurationUSec != 0) {
                // Caller requested a fixed frame rate.  Ignore PTS.
                frameDelta = fixedDurationUSec;
            } else {
                frameDelta = presentationTimeUsec - preventUnSec;
            }
            if (frameDelta < 0) {
                Logger.w(WEIRD_VIDEO_TIMES_WENT_BACKWARD);
                frameDelta = 0;
            } else if (frameDelta == 0) {
                // This suggests a possible bug in movie generation.
                Logger.i(WARNING_CURRENT_FRAME_AND_PREVIOUS_FRAME_HAD_SAME_TIMESTAMP);
            } else if (frameDelta > 10 * ONE_MILLION) {
                // Inter-frame times could be arbitrarily long.  For this player, we want
                // to alert the developer that their movie might have issues (maybe they
                // accidentally output timestamps in nsec rather than usec).
                Logger.i( INTER_FRAME_PAUSE_WAS + (frameDelta / ONE_MILLION) +
                        SEC_CAPPING_AT_5_SEC);
                frameDelta = 5 * ONE_MILLION;
            }

            long desiredUsec = preMonoUSec + frameDelta;  // when we want to wake up
            long nowUsec = System.nanoTime() / 1000;
            while (nowUsec < (desiredUsec - 100) /*&& mState == RUNNING*/) {
                // Sleep until it's time to wake up.  To be responsive to "stop" commands
                // we're going to wake up every half a second even if the sleep is supposed
                // to be longer (which should be rare).  The alternative would be
                // to interrupt the thread, but that requires more work.
                //
                // The precision of the sleep call varies widely from one device to another;
                // we may wake early or late.  Different devices will have a minimum possible
                // sleep time. If we're within 100us of the target time, we'll probably
                // overshoot if we try to sleep, so just go ahead and continue on.
                long sleepTimeUsec = desiredUsec - nowUsec;
                if (sleepTimeUsec > 500000) {
                    sleepTimeUsec = 500000;
                }
                try {
                    if (CHECK_SLEEP_TIME) {
                        long startNsec = System.nanoTime();
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                        long actualSleepNsec = System.nanoTime() - startNsec;
                        Logger.d( "sleep=" + sleepTimeUsec + " actual=" + (actualSleepNsec/1000) +
                                " diff=" + (Math.abs(actualSleepNsec / 1000 - sleepTimeUsec)) +
                                " (usec)");
                    } else {
                        Thread.sleep(sleepTimeUsec / 1000, (int) (sleepTimeUsec % 1000) * 1000);
                    }
                } catch (InterruptedException ie) {
                    Logger.e(ie);
                }
                nowUsec = System.nanoTime() / 1000;
            }

            // Advance times using calculated time values, not the post-sleep monotonic
            // clock time, to avoid drifting.
            preMonoUSec += frameDelta;
            preventUnSec += frameDelta;
        }
    }

    // runs on decode thread
    @Override public void postRender() {}

    @Override
    public void loopReset() {
        loopReset = true;
    }
}
