/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.analytics;

import android.content.Context;
import android.util.Log;

import androidx.annotation.StringRes;

public record LogEventTracker(Context mContext) implements EventTracker {

    @Override
    public void sendEvent(@StringRes int category, @StringRes int action, @StringRes int label) {
        //TODO
    }

    /**
     * @return Resource string represented by a given resource id, null if resId is invalid (0).
     */
    private String safeGetString(@StringRes int resId) {
        return resId == 0 ? null : mContext.getString(resId);
    }
}
