/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.Surface;

import com.lk.openstreamer.Constants;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.log.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

import static com.lk.openstreamer.Constants.MSG_FRAME_AVAILABLE_SOON;
import static com.lk.openstreamer.Constants.MSG_SAVE_VIDEO;
import static com.lk.openstreamer.Constants.MSG_SHUTDOWN;
import static com.lk.openstreamer.Strings.ENCODER_HANDLER_HANDLE_MESSAGE_WEAK_REF_IS_NULL;
import static com.lk.openstreamer.Strings.ENCODER_HANDLER_WHAT;
import static com.lk.openstreamer.Strings.FORMAT;

public class CircularEncoder {

    private static final boolean VERBOSE = false;
    private static final int IFRAME_INTERVAL = 1; // sync frame every second

    private EncoderThread encoderThread;
    private Surface inputSurface;
    private MediaCodec encoder;

    public CircularEncoder(int width, int height, int bitRate, int frameRate, int desiredSpanSec,
                           Callback cb) throws IOException {
        // The goal is to size the buffer so that we can accumulate N seconds worth of video,
        // where N is passed in as "desiredSpanSec".  If the codec generates data at roughly
        // the requested bit rate, we can compute it as time * bitRate / bitsPerByte.
        //
        // Sync frames will appear every (frameRate * IFRAME_INTERVAL) frames.  If the frame
        // rate is higher or lower than expected, various calculations may not work out right.
        //
        // Since we have to start muxing from a sync frame, we want to ensure that there's
        // room for at least one full GOP in the buffer, preferrably two.
        if (desiredSpanSec < IFRAME_INTERVAL * 2) {
            throw new RuntimeException(Strings.REQUESTED_TIME_SPAN_IS_TOO_SHORT + desiredSpanSec +
                    " vs. " + (IFRAME_INTERVAL * 2));
        }
        CircularEncoderBuffer encBuffer = new CircularEncoderBuffer(bitRate, frameRate,
                desiredSpanSec);

        MediaFormat format = MediaFormat.createVideoFormat(Strings.MIME_TYPE, width, height);

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        if (VERBOSE) Logger.d(FORMAT + format);

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        encoder = MediaCodec.createEncoderByType(Strings.MIME_TYPE);
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        inputSurface = encoder.createInputSurface();
        encoder.start();

        // Start the encoder thread last.  That way we're sure it can see all of the state
        // we've initialized.
        encoderThread = new EncoderThread(encoder, encBuffer, cb);
        encoderThread.start();
        encoderThread.waitUntilReady();
    }

    /**
     * Returns the encoder's input surface.
     */
    public Surface getInputSurface() {
        return inputSurface;
    }

    /**
     * Shuts down the encoder thread, and releases encoder resources.
     * <p>
     * Does not return until the encoder thread has stopped.
     */
    public void shutdown() {
        if (VERBOSE) Logger.d(Strings.RELEASING_ENCODER_OBJECTS);

        Handler handler = encoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(MSG_SHUTDOWN));
        try {
            encoderThread.join();
        } catch (InterruptedException ie) {
            Logger.w(Strings.ENCODER_THREAD_JOIN_WAS_INTERRUPTED);
        }

        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    @SuppressWarnings("unused")
    public void frameAvailableSoon() {
        Handler handler = encoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(MSG_FRAME_AVAILABLE_SOON));
    }

    @SuppressWarnings("unused")
    public void saveVideo(File outputFile) {
        Handler handler = encoderThread.getHandler();
        handler.sendMessage(handler.obtainMessage(MSG_SAVE_VIDEO, outputFile));
    }

    public interface Callback {
        void fileSaveComplete(int status);

        void bufferStatus(long totalTimeMsec);
    }

    private static class EncoderThread extends Thread {
        private final MediaCodec encoder;
        private final MediaCodec.BufferInfo bufferInfo;
        private final CircularEncoderBuffer encBuffer;
        private final CircularEncoder.Callback callback;
        private final Object lock = new Object();
        private MediaFormat encoderFormat;
        private EncoderHandler handler;
        private int frameNUm;
        private volatile boolean ready = false;

        EncoderThread(MediaCodec mediaCodec, CircularEncoderBuffer encBuffer,
                      CircularEncoder.Callback callback) {
            encoder = mediaCodec;
            this.encBuffer = encBuffer;
            this.callback = callback;

            bufferInfo = new MediaCodec.BufferInfo();
        }

        /**
         * Thread entry point.
         * <p>
         * Prepares the Looper, Handler, and signals anybody watching that we're ready to go.
         */
        @Override
        public void run() {
            Looper.prepare();
            handler = new EncoderHandler(this);    // must create on encoder thread
            Logger.d(Strings.ENCODER_THREAD_READY);
            synchronized (lock) {
                ready = true;
                lock.notify();    // signal waitUntilReady()
            }

            Looper.loop();

            synchronized (lock) {
                ready = false;
                handler = null;
            }
            Logger.d(Strings.LOOPER_QUIT);
        }

        /**
         * Waits until the encoder thread is ready to receive messages.
         * <p>
         * Call from non-encoder thread.
         */
        void waitUntilReady() {
            synchronized (lock) {
                while (!ready) {
                    try {
                        lock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        /**
         * Returns the Handler used to send messages to the encoder thread.
         */
        EncoderHandler getHandler() {
            synchronized (lock) {
                // Confirm ready state.
                if (!ready) {
                    throw new RuntimeException("not ready");
                }
            }
            return handler;
        }

        /**
         * Drains all pending output from the decoder, and adds it to the circular buffer.
         */
        void drainEncoder() {
            final int TIMEOUT_USEC = 0;     // no timeout -- check for buffers, bail if none

            @SuppressWarnings("deprecation") ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            while (true) {
                int encoderStatus = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    break;
                } else //noinspection deprecation
                    if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        //noinspection deprecation
                        encoderOutputBuffers = encoder.getOutputBuffers();
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // Should happen before receiving buffers, and should only happen once.
                        // The MediaFormat contains the csd-0 and csd-1 keys, which we'll need
                        // for MediaMuxer.  It's unclear what else MediaMuxer might want, so
                        // rather than extract the codec-specific data and reconstruct a new
                        // MediaFormat later, we just grab it here and keep it around.
                        encoderFormat = encoder.getOutputFormat();
                        Logger.d(Strings.ENCODER_OUTPUT_FORMAT_CHANGED + encoderFormat);
                    } else if (encoderStatus < 0) {
                        Logger.w(Strings.UNEXPECTED_RESULT_FROM_ENCODER_DEQUEUE_OUTPUT_BUFFER +
                                encoderStatus);
                        // let's ignore it
                    } else {
                        ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                        if (encodedData == null) {
                            throw new RuntimeException(Strings.ENCODER_OUTPUT_BUFFER + encoderStatus +
                                    " was null");
                        }

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // The codec config data was pulled out when we got the
                            // INFO_OUTPUT_FORMAT_CHANGED status.  The MediaMuxer won't accept
                            // a single big blob -- it wants separate csd-0/csd-1 chunks --
                            // so simply saving this off won't work.
                            if (VERBOSE) Logger.d(Strings.IGNORING_BUFFER_FLAG_CODEC_CONFIG);
                            bufferInfo.size = 0;
                        }

                        if (bufferInfo.size != 0) {
                            // adjust the ByteBuffer values to match BufferInfo (not needed?)
                            encodedData.position(bufferInfo.offset);
                            encodedData.limit(bufferInfo.offset + bufferInfo.size);

                            encBuffer.add(encodedData, bufferInfo.flags,
                                    bufferInfo.presentationTimeUs);

                            if (VERBOSE) {
                                Logger.d("sent " + bufferInfo.size + " bytes to muxer, ts=" +
                                        bufferInfo.presentationTimeUs);
                            }
                        }

                        encoder.releaseOutputBuffer(encoderStatus, false);

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Logger.w(Strings.REACHED_END_OF_STREAM_UNEXPECTEDLY);
                            break;      // out of while
                        }
                    }
            }
        }

        /**
         * Drains the encoder output.
         * <p>
         * See notes for {@link CircularEncoder#frameAvailableSoon()}.
         */
        void frameAvailableSoon() {
            if (VERBOSE) Logger.d(Strings.FRAME_AVAILABLE_SOON);
            drainEncoder();

            frameNUm++;
            if ((frameNUm % 10) == 0) {        // TODO: should base off frame rate or clock?
                callback.bufferStatus(encBuffer.computeTimeSpanUsec());
            }
        }

        /**
         * Saves the encoder output to a .mp4 file.
         * <p>
         * We'll drain the encoder to get any lingering data, but we're not going to shut
         * the encoder down or use other tricks to try to "flush" the encoder.  This may
         * mean we miss the last couple of submitted frames if they're still working their
         * way through.
         * <p>
         * We may want to reset the buffer after this -- if they hit "capture" again right
         * away they'll end up saving video with a gap where we paused to write the file.
         */
        void saveVideo(File outputFile) {
            if (VERBOSE) Logger.d(Strings.SAVE_VIDEO + " " + outputFile);

            int index = encBuffer.getFirstIndex();
            if (index < 0) {
                Logger.w(Strings.UNABLE_TO_GET_FIRST_INDEX);
                callback.fileSaveComplete(1);
                return;
            }

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            MediaMuxer muxer = null;
            @SuppressWarnings("UnusedAssignment") int result = -1;
            try {
                muxer = new MediaMuxer(outputFile.getPath(),
                        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                int videoTrack = muxer.addTrack(encoderFormat);
                muxer.start();

                do {
                    ByteBuffer buf = encBuffer.getChunk(index, info);
                    if (VERBOSE) {
                        Logger.d("SAVE " + index + " flags=0x" + Integer.toHexString(info.flags));
                    }
                    muxer.writeSampleData(videoTrack, buf, info);
                    index = encBuffer.getNextIndex(index);
                } while (index >= 0);
                result = 0;
            } catch (IOException ioe) {
                Logger.w(Strings.MUXER_FAILED);
                result = 2;
            } finally {
                if (muxer != null) {
                    muxer.stop();
                    muxer.release();
                }
            }

            if (VERBOSE) {
                Logger.d(Strings.MUXER_STOPPED_RESULT + result);
            }
            callback.fileSaveComplete(result);
        }

        /**
         * Tells the Looper to quit.
         */
        void shutdown() {
            if (VERBOSE) Logger.d(Strings.SHUTDOWN);
            //noinspection ConstantConditions
            Looper.myLooper().quit();
        }

        /**
         * Handler for EncoderThread.  Used for messages sent from the UI thread (or whatever
         * is driving the encoder) to the encoder thread.
         * <p>
         * The object is created on the encoder thread.
         */
        private static class EncoderHandler extends Handler {

            // This shouldn't need to be a weak ref, since we'll go away when the Looper quits,
            // but no real harm in it.
            private final WeakReference<EncoderThread> mWeakEncoderThread;

            /**
             * Constructor.  Instantiate object from encoder thread.
             */
            EncoderHandler(EncoderThread et) {
                mWeakEncoderThread = new WeakReference<>(et);
            }

            @Override  // runs on encoder thread
            public void handleMessage(Message msg) {
                int what = msg.what;
                if (VERBOSE) {
                    Logger.v(ENCODER_HANDLER_WHAT + what);
                }

                EncoderThread encoderThread = mWeakEncoderThread.get();
                if (encoderThread == null) {
                    Logger.w(ENCODER_HANDLER_HANDLE_MESSAGE_WEAK_REF_IS_NULL);
                    return;
                }

                switch (what) {
                    case Constants.MSG_FRAME_AVAILABLE_SOON:
                        encoderThread.frameAvailableSoon();
                        break;
                    case Constants.MSG_SAVE_VIDEO:
                        encoderThread.saveVideo((File) msg.obj);
                        break;
                    case Constants.MSG_SHUTDOWN:
                        encoderThread.shutdown();
                        break;
                    default:
                        throw new RuntimeException("unknown message " + what);
                }
            }
        }
    }
}
