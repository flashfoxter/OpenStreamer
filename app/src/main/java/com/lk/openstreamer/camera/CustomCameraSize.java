/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.camera;

import android.hardware.Camera;

public class CustomCameraSize {
    /**
     * Sets the dimensions for pictures.
     *
     * @param w the photo width (pixels)
     * @param h the photo height (pixels)
     */
    public CustomCameraSize(int w, int h) {
        width = w;
        height = h;
    }

    /**
     * width of the picture
     */
    @SuppressWarnings("CanBeFinal")
    public int width;

    @Override
    public int hashCode() {
        return width * 32713 + height;
    }

    /**
     * Compares {@code obj} to this size.
     *
     * @param obj the object to compare this size with.
     * @return {@code true} if the width and height of {@code obj} is the
     *         same as those of this size. {@code false} otherwise.
     */
    @SuppressWarnings("deprecation")
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Camera.Size)) {
            return false;
        }
        Camera.Size s = (Camera.Size) obj;
        return width == s.width && height == s.height;
    }
    /** height of the picture */
    public int height;
}
