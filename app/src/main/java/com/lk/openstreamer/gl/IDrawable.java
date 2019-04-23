/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

public interface IDrawable {
    void drawFrame();
    void fileSaveComplete(int arg);

    @SuppressWarnings("unused")
    void updateBufferStatus(long arg);
}
