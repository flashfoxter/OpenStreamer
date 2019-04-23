/*
 * Copyright (c) 2019. Levashkin Konstantin
 */

package com.lk.openstreamer.gl;

import android.app.Activity;
import android.content.Context;
import android.view.Display;
import android.view.WindowManager;

import com.lk.openstreamer.log.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Some handy utilities.
 */
@SuppressWarnings("ALL")
public class MiscUtils {

    private MiscUtils() {}

    /**
     * Obtains a list of files that live in the specified directory and match the glob pattern.
     */
    public static String[] getFiles(File dir, String glob) {
        String regex = globToRegex(glob);
        final Pattern pattern = Pattern.compile(regex);
        String[] result = dir.list((dir1, name) -> {
            Matcher matcher = pattern.matcher(name);
            return matcher.matches();
        });
        Arrays.sort(result);

        return result;
    }

    /**
     * Converts a filename globbing pattern to a regular expression.
     * <p>
     * The regex is suitable for use by Matcher.matches(), which matches the entire string, so
     * we don't specify leading '^' or trailing '$'.
     */
    private static String globToRegex(String glob) {
        // Quick, overly-simplistic implementation -- just want to handle something simple
        // like "*.mp4".
        //
        // See e.g. http://stackoverflow.com/questions/1247772/ for a more thorough treatment.
        StringBuilder regex = new StringBuilder(glob.length());
        //regex.append('^');
        for (char ch : glob.toCharArray()) {
            switch (ch) {
                case '*':
                    regex.append(".*");
                    break;
                case '?':
                    regex.append('.');
                    break;
                case '.':
                    regex.append("\\.");
                    break;
                default:
                    regex.append(ch);
                    break;
            }
        }
        //regex.append('$');
        return regex.toString();
    }

    /**
     * Obtains the approximate refresh time, in nanoseconds, of the default display associated
     * with the activity.
     * <p>
     * The actual refresh rate can vary slightly (e.g. 58-62fps on a 60fps device).
     */
    public static long getDisplayRefreshNsec(Activity activity) {
        Display display = ((WindowManager)
                activity.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        double displayFps = display.getRefreshRate();
        long refreshNs = Math.round(1000000000L / displayFps);
        Logger.d("refresh rate is " + displayFps + " fps --> " + refreshNs + " ns");
        return refreshNs;
    }
}
