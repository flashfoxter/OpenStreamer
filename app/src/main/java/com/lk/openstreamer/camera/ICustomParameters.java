/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.camera;

import java.util.List;

public interface ICustomParameters {
    CustomCameraSize getPreferredPreviewSizeForVideo();
    CustomCameraSize getPreviewSize();
    List<CustomCameraSize> getSupportedPreviewSizes();
    List<int[]> getSupportedPreviewFpsRange();
    void setPreviewSize(int width, int height);
    void setRecordingHint(boolean recordingHint);
    void getPreviewFpsRange(int[] range);
    void setPreviewFpsRange(int min, int max);
}
