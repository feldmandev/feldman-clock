/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.app.integration;

import com.feldman.clock.R;
import com.feldman.clock.app.MainActivity;



import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.feldman.clock.core.analytics.Events;
import com.feldman.clock.stopwatch.StopwatchService;

public class HandleShortcuts extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Intent intent = getIntent();

        try {
            final String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case StopwatchService.ACTION_PAUSE_STOPWATCH -> {
                        Events.sendStopwatchEvent(R.string.action_pause, R.string.label_shortcut);

                        // Open DeskClock positioned on the stopwatch tab.
                        startActivity(new Intent(this, MainActivity.class)
                                .setAction(StopwatchService.ACTION_PAUSE_STOPWATCH));
                        setResult(RESULT_OK);
                    }
                    case StopwatchService.ACTION_START_STOPWATCH -> {
                        Events.sendStopwatchEvent(R.string.action_start, R.string.label_shortcut);

                        // Open DeskClock positioned on the stopwatch tab.
                        startActivity(new Intent(this, MainActivity.class)
                                .setAction(StopwatchService.ACTION_START_STOPWATCH));
                        setResult(RESULT_OK);
                    }
                    default -> throw new IllegalArgumentException("Unsupported action: " + action);
                }
            }
        } catch (Exception e) {
            setResult(RESULT_CANCELED);
        } finally {
            finish();
        }
    }
}
