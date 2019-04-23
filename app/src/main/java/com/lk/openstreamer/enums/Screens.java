/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.enums;

import android.support.annotation.IdRes;

import com.lk.openstreamer.R;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
public enum Screens {
    REC(R.id.bottom_tab_rec, ID.NONE),
    PLAY(R.id.bottom_tab_play, ID.NONE),
    STREAM(R.id.bottom_tab_stream, ID.NONE);

    @Getter
    @IdRes
    private int bottomMenuId;

    @Getter
    @IdRes
    private int drawerMenuId;

    public static Screens fromBottomId(@IdRes int menuId) {
        for (Screens screen : values()) {
            if (screen.getBottomMenuId() == menuId) {
                return screen;
            }
        }
        throw new IllegalStateException();
    }

    public static Screens fromDrawerId(@IdRes int menuId) {
        for (Screens screen : values()) {
            if (screen.getDrawerMenuId() == menuId) {
                return screen;
            }
        }
        throw new IllegalStateException();
    }

    public boolean isBottom() {
        return bottomMenuId != ID.NONE;
    }

    private static class ID {
        private static final int NONE = -1;
    }
}

