/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;


import com.lk.openstreamer.camera.CustomCameraSize;
import com.lk.openstreamer.camera.ICustomParameters;
import com.lk.openstreamer.log.Logger;

import java.util.List;

/**
 * Camera-related utility functions.
 */
public class CameraUtils {

    public static void choosePreviewSize(ICustomParameters parms, int width, int height) {
        // We should make sure that the requested MPEG size is less than the preferred
        // size, and has the same aspect ratio.
        CustomCameraSize ppsfv = parms.getPreferredPreviewSizeForVideo();
        if (ppsfv != null) {
            Logger.d("Camera preferred preview size for video is " +
                    ppsfv.width + "x" + ppsfv.height);
        }

        //for (Camera.Size size : parms.getSupportedPreviewSizes()) {
        //    Log.d(TAG, "supported: " + size.width + "x" + size.height);
        //}

        for (CustomCameraSize size : parms.getSupportedPreviewSizes()) {
            if (size.width == width && size.height == height) {
                parms.setPreviewSize(width, height);
                return;
            }
        }

        Logger.w("Unable to set preview size to " + width + "x" + height);
        if (ppsfv != null) {
            parms.setPreviewSize(ppsfv.width, ppsfv.height);
        }
        // else use whatever the default size is
    }


    public static int chooseFixedPreviewFps(ICustomParameters parms, int desiredThousandFps) {
        List<int[]> supported = parms.getSupportedPreviewFpsRange();

        for (int[] entry : supported) {
            //Log.d(TAG, "entry: " + entry[0] + " - " + entry[1]);
            if ((entry[0] == entry[1]) && (entry[0] == desiredThousandFps)) {
                parms.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }

        int[] tmp = new int[2];
        parms.getPreviewFpsRange(tmp);
        int guess;
        if (tmp[0] == tmp[1]) {
            guess = tmp[0];
        } else {
            guess = tmp[1] / 2;     // shrug
        }

        Logger.d("Couldn't find match for " + desiredThousandFps + ", using " + guess);
        return guess;
    }
}
