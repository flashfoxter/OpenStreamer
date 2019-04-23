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
public class CustomCamera implements ICustomCamera {

    private static Camera camera;
    @SuppressWarnings("CanBeFinal")
    private CustomParameters customParameters;

    @SuppressWarnings("WeakerAccess")
    public CustomCamera (Camera camera) {
        CustomCamera.camera = camera;
        customParameters = new CustomParameters(camera.getParameters());
    }

    @Override
    public ICustomParameters getParameters() {
        return customParameters;
    }

    @Override
    public void setDisplayOrientation(int orientation) {
        camera.setDisplayOrientation(orientation);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setParameters(Camera.Parameters parameters) {

    }

    @Override
    public void setParameters(ICustomParameters parameters) {

    }

    @Override
    public void stopPreview() {
        camera.startPreview();
    }

    @Override
    public void release() {
        camera.release();
    }

    @Override
    public void setPreviewTexture(SurfaceTexture surfaceTexture) throws IOException {
        camera.setPreviewTexture(surfaceTexture);
    }

    @Override
    public void setPreviewDisplay(SurfaceHolder holder) throws IOException {
        camera.setPreviewDisplay(holder);
    }

    @Override
    public void startPreview() {
        camera.startPreview();
    }

    // TODO: 2019-04-21 refactor: remove static
    public static ICustomCamera open(int cameraId) {
        camera = Camera.open(cameraId);
        return new CustomCamera(camera);
    }

    // TODO: 2019-04-21 refactor: remove static
    public static ICustomCamera open() {
        throw new UnsupportedOperationException();
    }

}
