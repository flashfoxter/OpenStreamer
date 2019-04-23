package com.lk.openstreamer.ui.navigator;

import android.support.annotation.Nullable;

import com.lk.openstreamer.ui.activities.BaseActivity;
import com.lk.openstreamer.ui.fragments.BaseFragment;


public interface INavigator {
    void setFragment(@Nullable BaseFragment fromFragment, BaseFragment toFragment, BaseActivity activity);
}
