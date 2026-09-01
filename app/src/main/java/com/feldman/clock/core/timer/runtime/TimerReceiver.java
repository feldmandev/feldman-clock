/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.timer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.feldman.clock.core.data.DataModel;
import com.feldman.clock.core.data.Timer;

/**
 * This broadcast receiver exists to handle timer expiry scheduled in 4.2.1 and prior. It must exist
 * for at least one release cycle before removal to honor these old scheduled timers after upgrading
 * beyond 4.2.1. After 4.2.1, all timer expiration is directed to TimerService.
 */
public class TimerReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        if ("times_up".equals(intent.getAction())) {
            final int timerId = intent.getIntExtra("timer.intent.extra", -1);
            final Timer timer = DataModel.getDataModel().getTimer(timerId);
            context.startService(TimerService.createTimerExpiredIntent(context, timer));
        }
    }
}
