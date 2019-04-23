package com.lk.openstreamer.ui.activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.view.MenuItem;

import com.lk.openstreamer.enums.Screens;
import com.lk.openstreamer.ui.navigator.Navigate;
import com.lk.openstreamer.helpers.Ui;
import com.lk.openstreamer.ui.fragments.BaseFragment;
import com.lk.openstreamer.ui.navigator.INavigator;

import javax.inject.Inject;

import dagger.android.support.DaggerAppCompatActivity;
import lombok.Getter;
import lombok.Setter;

import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.PowerManager.FULL_WAKE_LOCK;
import static android.os.PowerManager.ON_AFTER_RELEASE;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;

@SuppressLint("Registered")
public class BaseActivity extends DaggerAppCompatActivity implements BottomNavigationView.OnNavigationItemSelectedListener  {

    private static final String SHIFTING_FIELD_NAME = "mShiftingMode";

    @Getter
    @Setter
    private @IdRes int fragmentContainer;

    @Getter
    protected BottomNavigationView bottomNavigationView;

    @SuppressWarnings("WeakerAccess")
    @Inject
    public INavigator navigator;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.setActivity(this);
    }

    @SuppressWarnings("SameParameterValue")
    void setFragment(@Nullable BaseFragment from, BaseFragment to) {
        navigator.setFragment(from, to, this);
    }

    public void initBottomNavigation(int resId, BottomNavigationView bottomNavigationView) {
        this.bottomNavigationView = bottomNavigationView;
        bottomNavigationView.getMenu().clear();
        bottomNavigationView.inflateMenu(resId);
        bottomNavigationView.setOnNavigationItemSelectedListener(this);
        // TODO: 2019-04-22 enable or remove
        //bottomNavigationView.setBackgroundColor(Ui.getColorRuntime(R.color.white));
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Navigate.toScreen(Screens.fromBottomId(item.getItemId()));
        return true;
    }

    /**
     * Show the activity over the lockscreen and wake up the device. If you launched the app manually
     * both of these conditions are already true. If you deployed from the IDE, however, this will
     * save you from hundreds of power button presses and pattern swiping per day!
     */
    public static void riseAndShine(Activity activity) {

        KeyguardManager keyguardManager = (KeyguardManager) activity.getSystemService(KEYGUARD_SERVICE);
        assert keyguardManager != null;
        //noinspection deprecation,deprecation
        final KeyguardManager.KeyguardLock keyguardLock = keyguardManager.newKeyguardLock("Unlock!");
        keyguardLock.disableKeyguard();

        activity.getWindow().addFlags(FLAG_SHOW_WHEN_LOCKED);

        PowerManager powerManager = (PowerManager) activity.getSystemService(POWER_SERVICE);
        //noinspection deprecation
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(FULL_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP | ON_AFTER_RELEASE, "Wakeup!");
        wakeLock.acquire();
        wakeLock.release();

    }

}
