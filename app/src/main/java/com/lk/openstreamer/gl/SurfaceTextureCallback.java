/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.graphics.SurfaceTexture;

import com.lk.openstreamer.Constants;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.log.Logger;


public class SurfaceTextureCallback implements SurfaceTexture.OnFrameAvailableListener {

    @SuppressWarnings("CanBeFinal")
    private CoreHandler handler;

    public SurfaceTextureCallback(CoreHandler mHandler) {
        this.handler = mHandler;
    }

    @Override   // SurfaceTexture.OnFrameAvailableListener; runs on arbitrary thread
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        Logger.d(Strings.FRAME_AVAILABLE);
        handler.sendEmptyMessage(Constants.MSG_FRAME_AVAILABLE);
    }
}