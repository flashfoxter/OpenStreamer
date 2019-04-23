/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.helpers;

import com.lk.openstreamer.log.Logger;
import com.lk.openstreamer.ui.fragments.BaseFragment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Provider {

    private static final String SET_VIEW_MODEL_METHOD_NAME = "setViewModel";
    private static final String VIEW_MODEL_CLASS_NAME_POSTFIX = "ViewModel";
    public static final String INIT_VIEW_MODEL_METHOD_NAME = "initViewModel";
    @SuppressWarnings("WeakerAccess")
    public static final String VIEW_MODEL_FIELD_NAME = "viewModel";

    public static <T extends BaseFragment> T getFragment(Class<? extends T> c) {
        T fragment = null;
        try {
            fragment = c.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        // Separate creating fragment and viewModel to prevent exception ClassNotFoundException for viewModel (see log for this)
        for (Method m : c.getMethods()) {
            if (m.getName().equals(INIT_VIEW_MODEL_METHOD_NAME)) {
                try {
                    m.invoke(fragment);
                    return fragment;
                } catch (Exception e) {
                    Logger.e(e.getMessage());
                    throw new IllegalStateException(e);
                }
            }
        }
        boolean hasViewModel = false;
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().equals(VIEW_MODEL_FIELD_NAME)) {
                hasViewModel = true;
                break;
            }
        }
        if (hasViewModel) {
            try {
                Class<?> viewModelClass = Class.forName(c.getName() + VIEW_MODEL_CLASS_NAME_POSTFIX);
                if (viewModelClass != null) {
                    c.getDeclaredMethod(SET_VIEW_MODEL_METHOD_NAME, viewModelClass).invoke(fragment, viewModelClass.newInstance());
                }

            } catch (Exception e) {
                Logger.e(e.getMessage());
                throw new IllegalStateException(e);
            }
        }
        return fragment;
    }
}
