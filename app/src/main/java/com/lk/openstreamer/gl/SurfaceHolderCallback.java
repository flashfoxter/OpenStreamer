/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.view.SurfaceHolder;

import com.annimon.stream.function.Consumer;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.log.Logger;

@SuppressWarnings("FieldCanBeLocal")
public class SurfaceHolderCallback implements SurfaceHolder.Callback {

    private SurfaceTextureCallback surfaceTextureCallback;
    @SuppressWarnings("CanBeFinal")
    private Consumer<SurfaceHolder> surfaceHolderConsumer;

    public SurfaceHolderCallback(SurfaceTextureCallback surfaceTextureCallback, Consumer<SurfaceHolder> surfaceHolderConsumer) {
        this.surfaceTextureCallback = surfaceTextureCallback;
        this.surfaceHolderConsumer = surfaceHolderConsumer;
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceCreated(SurfaceHolder holder) {
        Logger.d(Strings.SURFACE_CREATED_HOLDER + holder);
        surfaceHolderConsumer.accept(holder);
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Logger.d(Strings.SURFACE_CHANGED_FMT + format + Strings.SIZE + width + "x" + height +
                Strings.HOLDER + holder);
    }

    @Override   // SurfaceHolder.Callback
    public void surfaceDestroyed(SurfaceHolder holder) {
        Logger.d(Strings.SURFACE_DESTROYED_HOLDER + holder);
    }

}
