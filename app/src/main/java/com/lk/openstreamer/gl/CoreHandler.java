/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.os.Handler;
import android.os.Message;

import com.lk.openstreamer.Constants;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.log.Logger;

import java.lang.ref.WeakReference;

/**
 * Custom message handler for main UI thread.
 * <p>
 * Used to handle camera preview "frame available" notifications, and implement the
 * blinking "recording" text.  Receives callback messages from the encoder thread.
 */
public class CoreHandler extends Handler implements CircularEncoder.Callback {

    private final WeakReference<IDrawable> weakDrawable;

    public CoreHandler(IDrawable iDrawable) {
        weakDrawable = new WeakReference<>(iDrawable);
    }

    // CircularEncoder.Callback, called on encoder thread
    @Override
    public void fileSaveComplete(int status) {
        sendMessage(obtainMessage(Constants.MSG_FILE_SAVE_COMPLETE, status, 0, null));
    }

    // CircularEncoder.Callback, called on encoder thread
    @Override
    public void bufferStatus(long totalTimeMsec) {
        sendMessage(obtainMessage(Constants.MSG_BUFFER_STATUS,
                (int) (totalTimeMsec >> 32), (int) totalTimeMsec));
    }

    @Override
    public void handleMessage(Message msg) {
        IDrawable activity = weakDrawable.get();
        if (activity == null) {
            Logger.d(Strings.GOT_MESSAGE_FOR_DEAD_ACTIVITY);
            return;
        }

        switch (msg.what) {
            case Constants.MSG_BLINK_TEXT: {
                sendEmptyMessageDelayed(Constants.MSG_BLINK_TEXT, 0);
                break;
            }
            case Constants.MSG_FRAME_AVAILABLE: {
                activity.drawFrame();
                break;
            }
            case Constants.MSG_FILE_SAVE_COMPLETE: {
                activity.fileSaveComplete(msg.arg1);
                break;
            }
            case Constants.MSG_BUFFER_STATUS: {
                long duration = (((long) msg.arg1) << 32) |
                        (((long) msg.arg2) & 0xffffffffL);
                activity.updateBufferStatus(duration);
                break;
            }
            default:
                throw new RuntimeException(Strings.UNKNOWN_MESSAGE + msg.what);
        }
    }
}
