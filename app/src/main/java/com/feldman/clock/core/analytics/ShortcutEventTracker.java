/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.analytics;

import android.content.Context;
import android.content.pm.ShortcutManager;
import android.os.Build;
import android.util.ArraySet;

import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;

import com.feldman.clock.R;


import java.util.Set;

@RequiresApi(Build.VERSION_CODES.N_MR1)
public final class ShortcutEventTracker implements EventTracker {

    private final Context mContext;
    private final ShortcutManager mShortcutManager;
    private final Set<String> shortcuts = new ArraySet<>(5);

    public ShortcutEventTracker(Context context) {
        mContext = context;
        mShortcutManager = context.getSystemService(ShortcutManager.class);
        shortcuts.add(getShortcutId(context, R.string.category_alarm, R.string.action_create));
        shortcuts.add(getShortcutId(context, R.string.category_timer, R.string.action_create));
        shortcuts.add(getShortcutId(context, R.string.category_stopwatch, R.string.action_pause));
        shortcuts.add(getShortcutId(context, R.string.category_stopwatch, R.string.action_start));
        shortcuts.add(getShortcutId(context, R.string.category_standby, R.string.action_show));
    }

    @Override
    public void sendEvent(@StringRes int category, @StringRes int action, @StringRes int label) {
        final String shortcutId = getShortcutId(mContext, category, action);
        if (shortcuts.contains(shortcutId)) {
            mShortcutManager.reportShortcutUsed(shortcutId);
        }
    }

    private static String getShortcutId(Context context, @StringRes int category, @StringRes int action) {
        if (category == R.string.category_stopwatch) {
            return context.getString(category);
        } else {
            return context.getString(category) + "_" + context.getString(action);
        }
    }
}
