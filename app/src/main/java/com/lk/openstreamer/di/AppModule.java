package com.lk.openstreamer.di;

import android.app.Application;
import android.content.Context;

import com.lk.openstreamer.ui.navigator.INavigator;
import com.lk.openstreamer.ui.navigator.Navigator;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class AppModule {

    @Binds
    public abstract Context bindContext(Application application);

    @Provides
    static INavigator provideNavigator() {
        return new Navigator();
    }

}