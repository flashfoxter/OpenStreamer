package com.lk.openstreamer.ui.navigator;

import android.support.v4.app.FragmentTransaction;

import com.lk.openstreamer.ui.activities.BaseActivity;
import com.lk.openstreamer.ui.fragments.BaseFragment;


public class Navigator implements INavigator{
    @Override
    public void setFragment(BaseFragment from, BaseFragment to, BaseActivity activity) {
        FragmentTransaction fragmentTransaction = activity.getSupportFragmentManager().beginTransaction();
        if (from != null) {
            fragmentTransaction.addToBackStack(from.getClass().getName());
        }
        fragmentTransaction.replace(activity.getFragmentContainer(), to);
        fragmentTransaction.commit();
    }
}
