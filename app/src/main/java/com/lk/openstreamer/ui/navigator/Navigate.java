/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.ui.navigator;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;

import com.lk.openstreamer.R;
import com.lk.openstreamer.enums.Screens;
import com.lk.openstreamer.helpers.Provider;
import com.lk.openstreamer.helpers.Ui;
import com.lk.openstreamer.ui.activities.BaseActivity;
import com.lk.openstreamer.ui.fragments.BaseFragment;
import com.lk.openstreamer.ui.fragments.FragmentPlay;
import com.lk.openstreamer.ui.fragments.FragmentRecord;
import com.lk.openstreamer.ui.fragments.FragmentStream;

public class Navigate {

    public static void popTransaction(Fragment fragment, int popCount) {
        FragmentManager fm = Ui.getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.remove(fragment);
        transaction.commit();
        for (int i = 0; i < popCount; i++) {
            fm.popBackStack();
        }
    }

    public static void back() {
        Ui.getFragmentManager().popBackStack();
    }

    private static void clearBackStack() {
        FragmentManager fm = Ui.getFragmentManager();
        for (int i = 0; i < fm.getBackStackEntryCount(); ++i) {
            fm.popBackStack();
        }
    }

    public static BaseFragment getCurrentFragment() {
        return (BaseFragment) Ui.getFragmentManager().findFragmentById(R.id.fragment_container);
    }

    public static void toFragment(BaseFragment fromFragment, Class<? extends BaseFragment> toFragmentClass) {
        toFragment(fromFragment, Provider.getFragment(toFragmentClass));
    }

    @SuppressWarnings("WeakerAccess")
    public static void toFragment(BaseFragment fromFragment, BaseFragment toFragment) {
        FragmentTransaction fragmentTransaction = Ui.getFragmentManager().beginTransaction();
        if (fromFragment != null) {
            fragmentTransaction.addToBackStack(fromFragment.getClass().getName());
        }
        fragmentTransaction.replace(Ui.getActivity().findViewById(R.id.fragment_container).getId(), toFragment);
        fragmentTransaction.commit();
    }

    public static void toScreen(Screens screen) {
        clearBackStack();

        BaseActivity baseActivity = Ui.getActivity();
        baseActivity.getBottomNavigationView().setOnNavigationItemSelectedListener(null);
        Ui.run(() -> {
            // Uncomment if logic between nav_drawer and bottom will change
            // baseActivity.getDrawerNavigation().setCheckedItem(screen.getDrawerMenuId());
            if (screen.isBottom()) {
                baseActivity.getBottomNavigationView().getMenu().setGroupCheckable(0, true, true);
                baseActivity.getBottomNavigationView().setSelectedItemId(screen.getBottomMenuId());
            } else {
                baseActivity.getBottomNavigationView().getMenu().setGroupCheckable(0, false, true);
            }
            switch (screen) {

                //TABS SCREEN
                case REC:
                    toFragment(null, FragmentRecord.class);
                    break;
                case PLAY:
                    toFragment(null, FragmentPlay.class);
                    break;
                case STREAM:
                    toFragment(null, FragmentStream.class);
                    break;

                //OTHER SCREEN
                default:
                    throw new UnsupportedOperationException();
            }
            baseActivity.getBottomNavigationView().setOnNavigationItemSelectedListener(Ui.getMainActivity());
        });
    }

}
