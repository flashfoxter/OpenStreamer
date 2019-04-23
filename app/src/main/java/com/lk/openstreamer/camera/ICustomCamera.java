/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.camera;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import java.io.IOException;

/**
 * Camera1, Camera2 wrapper will be used later
 */
public interface ICustomCamera {
    ICustomParameters getParameters();

    void setDisplayOrientation(int orientation);

    @SuppressWarnings({"deprecation", "EmptyMethod"})
    @Deprecated
    void setParameters(Camera.Parameters parameters);

    @SuppressWarnings("EmptyMethod")
    void setParameters(ICustomParameters parameters);

    void stopPreview();

    void release();

    void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException;

    void setPreviewDisplay(SurfaceHolder holder) throws IOException;

    void startPreview();
}
