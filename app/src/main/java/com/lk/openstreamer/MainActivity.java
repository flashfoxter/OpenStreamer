/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer;

import android.os.Bundle;
import android.support.annotation.Nullable;

import com.lk.openstreamer.ui.navigator.Navigate;
import com.lk.openstreamer.ui.activities.BaseActivity;
import com.lk.openstreamer.ui.fragments.FragmentRecord;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initBottomNavigation(R.menu.menu_bottom, findViewById(R.id.bottom_navigation_view));
        Navigate.toFragment(null, FragmentRecord.class);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (BuildConfig.DEBUG) {
            BaseActivity.riseAndShine(this);
        }
    }
}