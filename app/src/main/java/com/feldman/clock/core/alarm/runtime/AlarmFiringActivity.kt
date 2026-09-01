package com.feldman.clock.alarm

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.feldman.clock.core.storage.provider.AlarmInstance
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.feldman.clock.core.alarm.AlarmChallenge
import com.feldman.clock.core.alarm.AlarmChallengePrefs
import com.feldman.clock.core.alarm.AlarmSnoozePolicy
import com.feldman.clock.ui.alarm.AlarmChallengeGate
import com.feldman.clock.ui.alarm.AlarmFiringScreen
import com.feldman.motion.MotionTheme

class AlarmFiringActivity : ComponentActivity() {

    private var alarmInstance: AlarmInstance? = null
    private var serviceBound = false
    private var connection: ServiceConnection? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                AlarmService.ALARM_DONE_ACTION -> finish()
                AlarmService.ALARM_SNOOZE_ACTION, AlarmService.ALARM_DISMISS_ACTION -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences(this)
        val autoOpen = prefs.getBoolean(com.feldman.clock.settings.PreferencesKeys.KEY_AUTO_OPEN_FIRING_SCREEN, com.feldman.clock.settings.PreferencesDefaultValues.DEFAULT_AUTO_OPEN_FIRING_SCREEN)

        if (autoOpen) {
            // Turn screen on and show over lock screen
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )

            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            keyguardManager?.requestDismissKeyguard(this, null)
        }


        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val instanceId = AlarmInstance.getId(intent.data!!)
        alarmInstance = AlarmInstance.getInstance(contentResolver, instanceId)

        if (alarmInstance == null) {
            finish()
            return
        }

        setContent {
            MotionTheme {
                val alarmId = alarmInstance?.mAlarmId
                val challenge = remember(alarmId) {
                    alarmId?.let { AlarmChallengePrefs.get(this@AlarmFiringActivity, it) }
                        ?: AlarmChallenge.NONE
                }
                val snoozePolicy = remember(alarmId) {
                    alarmId?.let { AlarmChallengePrefs.getSnoozePolicy(this@AlarmFiringActivity, it) }
                        ?: AlarmSnoozePolicy.NEVER
                }
                // Snoozing is unlimited, so an ungated Snooze button would let the user defer
                // past the challenge indefinitely. Past the alarm's allowance, snoozing has to
                // be earned too.
                val snoozesSoFar = remember(alarmInstance?.mId) {
                    alarmInstance?.mId
                        ?.let { AlarmChallengePrefs.snoozeCount(this@AlarmFiringActivity, it) }
                        ?: 0
                }
                val snoozeNeedsChallenge = challenge != AlarmChallenge.NONE &&
                    snoozesSoFar >= snoozePolicy.freeSnoozes

                // Which action the challenge, once solved, should carry out.
                var pendingAction by remember { mutableStateOf<PendingAction?>(null) }

                AlarmFiringScreen(
                    alarmLabel = alarmInstance?.mLabel ?: "",
                    onSnooze = {
                        if (snoozeNeedsChallenge) pendingAction = PendingAction.SNOOZE else snooze()
                    },
                    onDismiss = {
                        if (challenge == AlarmChallenge.NONE) dismiss()
                        else pendingAction = PendingAction.DISMISS
                    }
                )

                pendingAction?.let { action ->
                    AlarmChallengeGate(
                        challenge = challenge,
                        onSolved = {
                            when (action) {
                                PendingAction.SNOOZE -> snooze()
                                PendingAction.DISMISS -> dismiss()
                            }
                        },
                        onGiveUp = { pendingAction = null }
                    )
                }
            }
        }
        
        bindAlarmService()
        registerReceiver(receiver, IntentFilter(AlarmService.ALARM_DONE_ACTION).apply {
            addAction(AlarmService.ALARM_SNOOZE_ACTION)
            addAction(AlarmService.ALARM_DISMISS_ACTION)
        }, RECEIVER_NOT_EXPORTED)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(connection!!)
            serviceBound = false
        }
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            // connection might not be registered
        }
    }

    /** What a solved challenge should do, since both actions can be gated. */
    private enum class PendingAction { SNOOZE, DISMISS }

    private fun snooze() {
        val instance = alarmInstance ?: return
        AlarmChallengePrefs.recordSnooze(this, instance.mId)
        AlarmStateManager.setSnoozeState(this, instance, false)
        finish()
    }

    private fun dismiss() {
        val instance = alarmInstance ?: return
        // The tally is scoped to this firing, so it goes away with it.
        AlarmChallengePrefs.clearSnoozeCount(this, instance.mId)
        AlarmStateManager.deleteInstanceAndUpdateParent(this, instance)
        finish()
    }

    private fun bindAlarmService() {
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {}
            override fun onServiceDisconnected(name: ComponentName) {}
        }
        bindService(Intent(this, AlarmService::class.java), connection!!, BIND_AUTO_CREATE)
        serviceBound = true
    }
}
