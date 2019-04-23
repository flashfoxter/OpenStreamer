/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer;

public class Constants {
    public static final int VIDEO_WIDTH = 1280;  // dimensions for 720p video
    public static final int VIDEO_HEIGHT = 720;
    public static final int DESIRED_PREVIEW_FPS = 15;
    public static final int BIT_RATE = 6000000;
    public static final int MSG_BLINK_TEXT = 0;
    public static final int MSG_FRAME_AVAILABLE = 1;
    public static final int MSG_FILE_SAVE_COMPLETE = 2;
    public static final int MSG_BUFFER_STATUS = 3;

    public static final int FILTER_NONE = 0;
    public static final int FILTER_BLACK_WHITE = 1;
    public static final int FILTER_BLUR = 2;
    public static final int FILTER_SHARPEN = 3;
    public static final int FILTER_EDGE_DETECT = 4;
    public static final int FILTER_EMBOSS = 5;

    public static final int MSG_FRAME_AVAILABLE_SOON = 1;
    public static final int MSG_SAVE_VIDEO = 2;
    public static final int MSG_SHUTDOWN = 3;

    private static final int IFRAME_INTERVAL = 1;           // sync frame every second
}
