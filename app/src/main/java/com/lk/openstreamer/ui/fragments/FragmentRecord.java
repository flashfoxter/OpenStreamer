package com.lk.openstreamer.ui.fragments;

import android.annotation.SuppressLint;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.lk.openstreamer.Constants;
import com.lk.openstreamer.R;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.camera.CustomCamera;
import com.lk.openstreamer.camera.CustomCameraSize;
import com.lk.openstreamer.camera.ICustomCamera;
import com.lk.openstreamer.camera.ICustomParameters;
import com.lk.openstreamer.databinding.FragmentRecordBinding;
import com.lk.openstreamer.gl.CameraUtils;
import com.lk.openstreamer.gl.CircularEncoder;
import com.lk.openstreamer.gl.CoreHandler;
import com.lk.openstreamer.gl.IDrawable;
import com.lk.openstreamer.gl.InputCamera;
import com.lk.openstreamer.gl.SurfaceHolderCallback;
import com.lk.openstreamer.gl.SurfaceTextureCallback;
import com.lk.openstreamer.gl.program.FullFrameRect;
import com.lk.openstreamer.gl.program.Sprite3d;
import com.lk.openstreamer.gles.Drawable2d;
import com.lk.openstreamer.gles.EglCore;
import com.lk.openstreamer.gles.GlUtil;
import com.lk.openstreamer.gles.Texture2dProgram;
import com.lk.openstreamer.gles.WindowSurface;
import com.lk.openstreamer.helpers.Ui;
import com.lk.openstreamer.log.Logger;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;


@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class FragmentRecord extends BaseFragment implements IDrawable {

    public static final String VIDEO_FILE_NAME = "text-on-video.mp4";
    @SuppressLint("SimpleDateFormat")
    private SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

    //File and encoder
    private File outputFile;
    private float secondsOfVideo;
    private CircularEncoder circEncoder;
    private boolean fileSaveInProgress;
    private int cameraPreviewThousandFps;
    private WindowSurface encoderSurface;
    private int frameNum;

    private final float[] mTexMatrix = new float[16];
    private InputCamera inputCamera;
    private float[] displayProjectionMatrix = new float[16];
    private EglCore eglCore;
    private ICustomCamera camera;
    private CoreHandler coreHandler;

    private int videoTextureId;

    private Texture2dProgram textProgram;
    private SurfaceTexture cameraTexture;  // receives the output from the camera preview
    private Sprite3d videoSprite;
    private Sprite3d textRect;

    // TODO: 2019-04-23 Refactor: Flipped now cause of Z-Ordering
    //Main
    private Texture2dProgram videoProgramMain;
    private SurfaceTextureCallback surfaceTextureCallbackMain;
    private SurfaceHolderCallback surfaceHolderCallbackMain;
    private WindowSurface displayMain;
    private SurfaceHolder surfaceHolderMain;
    private SurfaceView surfaceMain;
    private SurfaceHolder.Callback callbackMain;
    private SurfaceHolder holderMain;

    //Preview
    private Texture2dProgram videoProgramPreview;
    private SurfaceTextureCallback surfaceTextureCallbackPreview;
    private SurfaceHolderCallback surfaceHolderCallbackPreview;
    private WindowSurface windowSurfacePreview;
    private SurfaceHolder surfaceHolderPreview;
    private SurfaceView surfacePreview;
    private SurfaceHolder.Callback callbackPreview;
    private SurfaceHolder holderPreview;
    private FullFrameRect frameRectPreview;
    private SurfaceTexture cameraPreviewTexture;  // receives the output from the camera preview
    private int previewTextureId;

    @SuppressWarnings("FieldCanBeLocal")
    private FragmentRecordBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = DataBindingUtil.inflate(inflater, R.layout.fragment_record, container, false);
        b.captureButton.setOnClickListener(v -> clickCapture());
        super.onCreate(savedInstanceState);
        return b.getRoot();

    }

    @Override
    public void onStart() {
        super.onStart();
        inputCamera = InputCamera.useFrontCamera();

        initMainSurface();
        initPreviewSurface();
        coreHandler = new CoreHandler(this);

        outputFile = new File(Ui.getActivity().getFilesDir(), VIDEO_FILE_NAME);
        secondsOfVideo = 0.0f;
        updateControls();
    }

    public void clickCapture() {
        if (fileSaveInProgress) {
            Logger.w(Strings.HEY_FILE_SAVE_IS_ALREADY_IN_PROGRESS);
            return;
        }

        // The button is disabled in onCreate(), and not enabled until the encoder and output
        // surface is ready, so it shouldn't be possible to get here with a null circEncoder.
        fileSaveInProgress = true;
        updateControls();

        String str = getString(R.string.nowSaving);
        b.recordingText.setText(str);
        circEncoder.saveVideo(outputFile);
    }

    private void initMainSurface() {
        surfaceTextureCallbackMain = new SurfaceTextureCallback(coreHandler);
        surfaceHolderCallbackMain = new SurfaceHolderCallback(surfaceTextureCallbackMain, holder -> {
            Logger.d("surfaceCreated holder=" + holder);

            // Set up everything that requires an EGL context.
            //
            // We had to wait until we had a surface because you can't make an EGL context current
            // without one, and creating a temporary 1x1 pbuffer is a waste of time.
            //
            // The display surface that we use for the SurfaceView, and the encoder surface we
            // use for video, use the same EGL context.

            eglCore = new EglCore(null, EglCore.FLAG_RECORDABLE);

            displayMain = new WindowSurface(eglCore, holder.getSurface(), false);
            displayMain.makeCurrent();

            float screenAspect = ((float) displayMain.getWidth()) / displayMain.getHeight();

            float near = -1.0f, far = 1.0f,
                    right = inputCamera.getDisplayWidth() / 2,
                    top = right / screenAspect;

            Matrix.orthoM(displayProjectionMatrix, 0,
                    -right, right,
                    -top, top,
                    near, far);

            videoSprite = new Sprite3d(new Drawable2d(inputCamera.getDisplayWidth(), inputCamera.getDisplayHeight()));
            videoProgramMain = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT);

            videoTextureId = videoProgramMain.createTextureObject();
            videoSprite.setTextureId(videoTextureId);

            videoSprite.transform(new Sprite3d.Transformer()
                    .reset()
                    .translate(0, 0, 0)
                    .rotateAroundZ(0)
                    .scale(1, 1, 1)
                    .build());

            setTextSprite();

            cameraTexture = new SurfaceTexture(videoTextureId);
            cameraTexture.setOnFrameAvailableListener(surfaceTexture -> coreHandler.sendEmptyMessage(MainHandler.MSG_FRAME_AVAILABLE));

            startPreview();

        });
        surfaceMain = b.surfaceMain;
        holderMain = surfaceMain.getHolder();
        holderMain.addCallback(surfaceHolderCallbackMain);
    }

    private void startPreview() {

        try {
            camera.setPreviewTexture(cameraTexture);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        camera.startPreview();

        // TODO: adjust bit rate based on frame rate?
        // TODO: adjust video width/height based on what we're getting from the camera preview?
        //       (can we guarantee that camera preview size is compatible with AVC video encoder?)
        try {
            circEncoder = new CircularEncoder(Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT, Constants.BIT_RATE,
                    cameraPreviewThousandFps / 1000, 7, coreHandler);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
        encoderSurface = new WindowSurface(eglCore, circEncoder.getInputSurface(), true);

    }

    private void initPreviewSurface() {
        surfaceTextureCallbackPreview = new SurfaceTextureCallback(coreHandler);
        surfaceHolderCallbackPreview = new SurfaceHolderCallback(surfaceTextureCallbackPreview, holder -> {

            Logger.d("surfaceCreated holder=" + holder);
            windowSurfacePreview = new WindowSurface(eglCore, holder.getSurface(), false);
            windowSurfacePreview.makeCurrent();

            frameRectPreview = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
            previewTextureId = frameRectPreview.createTextureObject();

            cameraPreviewTexture = new SurfaceTexture(previewTextureId);
            cameraPreviewTexture.setOnFrameAvailableListener(surfaceTextureCallbackPreview);
        });
        surfacePreview = b.surfacePreview;
        holderPreview = surfacePreview.getHolder();
        holderPreview.addCallback(surfaceHolderCallbackPreview);
    }

    @Override
    public void onResume() {
        super.onResume();
        openCamera(inputCamera.getCameraWidth(), inputCamera.getCameraHeight(),
                inputCamera.getFps(), inputCamera.getDisplayOrientation());
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseCamera();

        if (circEncoder != null) {
            circEncoder.shutdown();
            circEncoder = null;
        }

        if (cameraTexture != null) {
            cameraTexture.release();
            cameraTexture = null;
        }
        if (displayMain != null) {
            displayMain.release();
            displayMain = null;
        }
        if (videoSprite != null) {
            videoSprite = null;
        }
        if (eglCore != null) {
            eglCore.release();
            eglCore = null;
        }
        Logger.d("onPause() done");
    }

    private void updateControls() {
        String str = getString(R.string.secondsOfVideo, secondsOfVideo);
        b.capturedVideoDescText.setText(str);
        boolean wantEnabled = (circEncoder != null) && !fileSaveInProgress;
        b.captureButton.setEnabled(true);
        // TODO: 2019-04-23 Add fsm for ui
/*
        if (button.isEnabled() != wantEnabled) {
            Logger.d(Strings.StringContinuousCaptureActivity.SETTING_ENABLED + " " + wantEnabled);
            button.setEnabled(wantEnabled);
        }
*/
    }

    /**
     * Opens a camera, and attempts to establish preview mode at the specified width and height.
     * <p>
     * Sets mCameraPreviewFps to the expected frame rate (which might actually be variable).
     */
    @SuppressWarnings("ConstantConditions")
    private void openCamera(int desiredWidth, int desiredHeight, int desiredFps, int orientation) {

        if (camera != null) {
            throw new RuntimeException("camera already initialized");
        }
        if (orientation != 0 && orientation != 90 && orientation != 180 && orientation != 270) {
            throw new RuntimeException("Orientation values must be in {0,90,180,270}");
        }

        camera = CustomCamera.open(inputCamera.getCameraIndex());

        if (camera == null) {
            throw new RuntimeException("Unable to open camera");
        }

        ICustomParameters parms = camera.getParameters();
        CameraUtils.choosePreviewSize(parms, desiredWidth, desiredHeight);

        // Try to set the frame rate to a constant value.
        cameraPreviewThousandFps = CameraUtils.chooseFixedPreviewFps(parms,
                desiredFps * 1000);

        // Give the camera a hint that we're recording video.  This can have a big
        // impact on frame rate.
        parms.setRecordingHint(true);
        camera.setDisplayOrientation(orientation);
        camera.setParameters(parms);

        CustomCameraSize cameraPreviewSize = parms.getPreviewSize();
        String previewFacts = cameraPreviewSize.width + "x" + cameraPreviewSize.height +
                " @" + (cameraPreviewThousandFps / 1000.0f) + "fps";
        Logger.i("Camera config: " + previewFacts);
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private void releaseCamera() {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
            Logger.d("releaseCamera -- done");
        }
    }

    private void setTextSprite() {
        Bitmap textBitmap = fromText(getCurrentTimeStamp(), 64, Color.RED);//BitmapFactory.decodeResource(getResources(), R.drawable.ic_logo_128);
        int logoHeight = textBitmap.getHeight();
        int logoWidth = textBitmap.getWidth();
        Logger.d("Video sprite : " + logoWidth + " " + logoHeight);
        textProgram = new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_2D);
        textRect = new Sprite3d(new Drawable2d(logoWidth, logoHeight));
        int textTextureId = textProgram.createTextureObject();
        textRect.setTextureId(textTextureId);
        textProgram.setBitmap(textBitmap, textTextureId);
        textBitmap.recycle();
        textRect.transform(new Sprite3d.Transformer()
                .translate(0, 0, 0)
                .scale(1.0f, -1.0f, 1.0f)
                .build());
    }

    public String getCurrentTimeStamp() {
        // TODO: 2019-04-20 refactor with setTime instead of new Date each time
        Date date = new Date();
        return simpleDateFormat.format(date);
    }

    public Bitmap fromText(String text, int textSize, int textColor) {
        Paint paint = new Paint();
        paint.setTextSize(textSize);
        paint.setColor(textColor);
        float baseline = -paint.ascent(); // ascent() is negative
        int width = (int) (paint.measureText(text) + 0.0f);
        int height = (int) (baseline + paint.descent() + 1.0f);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setHasAlpha(true);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.argb(0, 255, 255, 255));
        // canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        canvas.drawText(text, 0, baseline, paint);

        return bitmap;
    }

    /**
     * Draws a frame onto the SurfaceView and the encoder surface.
     * <p>
     * This will be called whenever we get a new preview frame from the camera.  This runs
     * on the UI thread, which ordinarily isn't a great idea -- you really want heavy work
     * to be on a different thread -- but we're really just throwing a few things at the GPU.
     * The upside is that we don't have to worry about managing state changes between threads.
     * <p>
     * If there was a pending frame available notification when we shut down, we might get
     * here after onPause().
     */
    public void drawFrame() {
        //Log.d(TAG, "drawFrame");
        if (eglCore == null) {
            Logger.d("Skipping drawFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        displayMain.makeCurrent();
        cameraTexture.updateTexImage();
        cameraTexture.getTransformMatrix(mTexMatrix);
        GLES20.glViewport(0, 0, surfaceMain.getWidth(), surfaceMain.getHeight());
        videoProgramMain.clearScreen();
        videoSprite.draw(videoProgramMain, displayProjectionMatrix, mTexMatrix);
        //todo Surface flipped now ! - here we draw timestamp
        textRect.draw(textProgram, displayProjectionMatrix, GlUtil.IDENTITY_MATRIX);
        displayMain.swapBuffers();

        //Preview
        windowSurfacePreview.makeCurrent();
        cameraTexture.updateTexImage();
        cameraTexture.getTransformMatrix(mTexMatrix);
        surfacePreview = b.surfacePreview;
        int viewWidthPreview = surfacePreview.getWidth();
        int viewHeightPreview = surfacePreview.getHeight();
        GLES20.glViewport(0, 0, viewWidthPreview, viewHeightPreview);
        frameRectPreview.drawFrame(videoTextureId, mTexMatrix);
        windowSurfacePreview.swapBuffers();

        // Send it to the video encoder.
        if (!fileSaveInProgress) {
            encoderSurface.makeCurrent();
            GLES20.glViewport(0, 0, Constants.VIDEO_WIDTH, Constants.VIDEO_HEIGHT);
            frameRectPreview.drawFrame(videoTextureId, mTexMatrix);
            circEncoder.frameAvailableSoon();
            encoderSurface.setPresentationTime(cameraTexture.getTimestamp());
            //todo Timestamp for encoder here - check it's optimal
            textRect.draw(textProgram, displayProjectionMatrix, GlUtil.IDENTITY_MATRIX);
            encoderSurface.swapBuffers();
        }
        frameNum++;
    }

    @Override
    public void fileSaveComplete(int status) {
        Logger.d(Strings.FILE_SAVE_COMPLETE + " " + status);
        if (!fileSaveInProgress) {
            throw new RuntimeException(Strings.WEIRD_GOT_FILE_SAVE_CMPLETE_WHEN_NOT_IN_PROGRESS);
        }
        fileSaveInProgress = false;
        updateControls();
        String str = getString(R.string.nowRecording);
        b.recordingText.setText(str);
        if (status == 0) {
            str = getString(R.string.recordingSucceeded);
        } else {
            str = getString(R.string.recordingFailed, status);
        }
        Toast toast = Toast.makeText(getActivity(), str, Toast.LENGTH_SHORT);
        toast.show();
    }

    @Override
    public void updateBufferStatus(long arg) {

    }

    /**
     * Custom message handler for main UI thread.
     * <p>
     * Used to handle camera preview "frame available" notifications
     */
    private static class MainHandler extends Handler {
        private static final int MSG_FRAME_AVAILABLE = 1;

        @SuppressWarnings("CanBeFinal")
        private WeakReference<IDrawable> mWeakActivity;

        private MainHandler(IDrawable activity) {
            mWeakActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            // TODO: 2019-04-23 Drawable is not so good naming in android case - rename
            IDrawable drawableEntity = mWeakActivity.get();
            if (drawableEntity == null) {
                Logger.d("Got message for dead drawableEntity");
                return;
            }

            //noinspection SwitchStatementWithTooFewBranches
            switch (msg.what) {
                case MSG_FRAME_AVAILABLE: {
                    drawableEntity.drawFrame();
                    break;
                }
                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }
}

