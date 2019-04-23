package com.lk.openstreamer.ui.fragments;

import android.databinding.DataBindingUtil;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import com.lk.openstreamer.R;
import com.lk.openstreamer.Strings;
import com.lk.openstreamer.databinding.FragmentPlayBinding;
import com.lk.openstreamer.gl.MiscUtils;
import com.lk.openstreamer.gl.MoviePlayer;
import com.lk.openstreamer.gl.SpeedControlCallback;
import com.lk.openstreamer.helpers.Ui;
import com.lk.openstreamer.log.Logger;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;


@SuppressWarnings("unused")
public class FragmentPlay extends BaseFragment implements AdapterView.OnItemSelectedListener,
        MoviePlayer.PlayerFeedback {

    private final Object stopper = new Object();   // used to signal stop
    private String[] movieFiles;
    private int selectedMovie;
    private boolean showStopLabel;
    private MoviePlayer.PlayTask playTask;
    private boolean surfaceTextureReady = false;

    @SuppressWarnings("FieldCanBeLocal")
    private FragmentPlayBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        b = DataBindingUtil.inflate(inflater, R.layout.fragment_play, container, false);
        b.movieTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                Logger.d(MessageFormat.format(Strings.SURFACE_TEXTURE_READY + "({0},{1})", width, height));
                surfaceTextureReady = true;
                updateControls();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                surfaceTextureReady = false;
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
        b.playStopButton.setOnClickListener(v -> clickPlayStop());
        movieFiles = MiscUtils.getFiles(Ui.getActivity().getFilesDir(), Strings.MP_4);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(Ui.getActivity(),
                android.R.layout.simple_spinner_item, movieFiles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        b.playMovieFileSpinner.setAdapter(adapter);
        b.playMovieFileSpinner.setOnItemSelectedListener(this);
        updateControls();
        return b.getRoot();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (playTask != null) {
            stopPlayback();
            playTask.waitForStop();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Spinner spinner = (Spinner) parent;
        selectedMovie = spinner.getSelectedItemPosition();
        Logger.d(Strings.ON_ITEM_SELECTED + selectedMovie + " '" + movieFiles[selectedMovie] + "'");
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public void clickPlayStop() {
        if (showStopLabel) {
            Logger.d(Strings.STOPPING_MOVIE);
            stopPlayback();
            // Don't update the controls here -- let the task thread do it after the movie has
            // actually stopped.
            //showStopLabel = false;
            //updateControls();
        } else {
            if (playTask != null) {
                Logger.w("movie already playing");
                return;
            }
            Logger.d(Strings.STARTING_MOVIE);
            SpeedControlCallback callback = new SpeedControlCallback();
            if (b.locked60fpsCheckbox.isChecked()) {
                // TODO: consider changing this to be "free running" mode
                callback.setFixedPlaybackRate(60);
            }
            SurfaceTexture st = b.movieTextureView.getSurfaceTexture();
            Surface surface = new Surface(st);
            MoviePlayer player;
            try {
                player = new MoviePlayer(
                        new File(Ui.getActivity().getFilesDir(), movieFiles[selectedMovie]), surface, callback);
            } catch (IOException ioe) {
                // Unable to play movie
                Logger.e(ioe);
                surface.release();
                return;
            }
            adjustAspectRatio(player.getVideoWidth(), player.getVideoHeight());
            playTask = new MoviePlayer.PlayTask(player, this);
            if (b.loopPlaybackCheckbox.isChecked()) {
                playTask.setLoopMode(true);
            }
            showStopLabel = true;
            updateControls();
            playTask.execute();
        }
    }

    private void stopPlayback() {
        if (playTask != null) {
            playTask.requestStop();
        }
    }

    @Override
    public void playbackStopped() {
        Logger.d(Strings.PLAYBACK_STOPPED);
        showStopLabel = false;
        playTask = null;
        updateControls();
    }

    private void adjustAspectRatio(int videoWidth, int videoHeight) {
        int viewWidth = b.movieTextureView.getWidth();
        int viewHeight = b.movieTextureView.getHeight();
        double aspectRatio = (double) videoHeight / videoWidth;

        int newWidth, newHeight;
        if (viewHeight > (int) (viewWidth * aspectRatio)) {
            // limited by narrow width; restrict height
            newWidth = viewWidth;
            newHeight = (int) (viewWidth * aspectRatio);
        } else {
            // limited by short height; restrict width
            newWidth = (int) (viewHeight / aspectRatio);
            newHeight = viewHeight;
        }
        int xoff = (viewWidth - newWidth) / 2;
        int yoff = (viewHeight - newHeight) / 2;
        Logger.v("video=" + videoWidth + "x" + videoHeight +
                " view=" + viewWidth + "x" + viewHeight +
                " newView=" + newWidth + "x" + newHeight +
                " off=" + xoff + "," + yoff);

        Matrix txform = new Matrix();
        b.movieTextureView.getTransform(txform);
        txform.setScale((float) newWidth / viewWidth, (float) newHeight / viewHeight);
        txform.postTranslate(xoff, yoff);
        b.movieTextureView.setTransform(txform);
    }

    private void updateControls() {
        if (showStopLabel) {
            b.playStopButton.setText(R.string.stop_button_text);
        } else {
            b.playStopButton.setText(R.string.play_button_text);
        }
        b.playStopButton.setEnabled(surfaceTextureReady);
        // We don't support changes mid-play, so dim these.
        b.locked60fpsCheckbox.setEnabled(!showStopLabel);
        b.loopPlaybackCheckbox.setEnabled(!showStopLabel);
    }
}
