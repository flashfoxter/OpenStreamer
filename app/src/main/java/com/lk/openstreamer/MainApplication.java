/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer;

import com.lk.openstreamer.di.AppComponent;
import com.lk.openstreamer.di.DaggerAppComponent;

import dagger.android.AndroidInjector;
import dagger.android.DaggerApplication;

/**
 * Need to be changed to MultiDexApplication on app growing
 */
public class MainApplication extends DaggerApplication {

    @SuppressWarnings("UnnecessaryLocalVariable")
    @Override
    protected AndroidInjector<? extends DaggerApplication> applicationInjector() {
        AppComponent appComponent = DaggerAppComponent.builder().application(this).build();
        return appComponent;
    }

}
