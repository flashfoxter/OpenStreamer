package com.lk.openstreamer.di;


import com.lk.openstreamer.ui.activities.BaseActivity;
import com.lk.openstreamer.MainActivity;

import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
abstract class ActivityContributorModule {

    @ContributesAndroidInjector
    abstract BaseActivity contributeBaseActivity();

    @ContributesAndroidInjector
    abstract MainActivity contributeMainActivity();

}
