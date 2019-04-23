/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.camera;

import android.hardware.Camera;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;

import java.util.List;

@SuppressWarnings("WeakerAccess")
public class CustomParameters implements ICustomParameters {

    @SuppressWarnings("CanBeFinal")
    private Camera.Parameters parameters;

    public CustomParameters(Camera.Parameters parameters) {
        this.parameters = parameters;
    }

    @Override
    public CustomCameraSize getPreferredPreviewSizeForVideo() {
        // TODO: 2019-04-21 safe unwrap option
        Optional<CustomCameraSize> preferredSize = Stream.of(parameters.getPreferredPreviewSizeForVideo()).map(x -> new CustomCameraSize(x.width, x.height)).findFirst();
        return preferredSize.get();
    }

    @Override
    public CustomCameraSize getPreviewSize() {
        Optional<CustomCameraSize> previewSize = Stream.of(parameters.getPreviewSize()).map(x -> new CustomCameraSize(x.width, x.height)).findFirst();
        // TODO: 2019-04-21 safe unwrap option
        return previewSize.get();
    }

    @SuppressWarnings({"UnnecessaryLocalVariable", "deprecation"})
    @Override
    public List<CustomCameraSize> getSupportedPreviewSizes() {
        List<Camera.Size> supportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<CustomCameraSize> customCameraSizes = Stream.of(supportedPreviewSizes).map(x -> new CustomCameraSize(x.width, x.height)).toList();
        return customCameraSizes;
    }

    @Override
    public List<int[]> getSupportedPreviewFpsRange() {
        return parameters.getSupportedPreviewFpsRange();
    }

    @Override
    public void setPreviewSize(int width, int height) {
        parameters.setPreviewSize(width, height);
    }

    @Override
    public void setRecordingHint(boolean recordingHint) {
        parameters.setRecordingHint(recordingHint);
    }

    @Override
    public void getPreviewFpsRange(int[] range) {
        parameters.getPreviewFpsRange(range);
    }

    @Override
    public void setPreviewFpsRange(int min, int max) {
        parameters.setPreviewFpsRange(min, max);
    }
}
