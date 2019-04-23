/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.camera;

@SuppressWarnings("WeakerAccess")
public class CustomCameraInfo {
    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;
    public int facing;
    public int orientation;
    public boolean canDisableShutterSound;
}
