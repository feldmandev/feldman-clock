package com.feldman.clock.ui.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.feldman.clock.app.ClockApplication
import androidx.core.content.edit

@Composable
fun rememberBooleanPreference(
    key: String,
    defaultValue: Boolean
): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = remember(context) {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    val backingState = remember(key, prefs) {
        mutableStateOf(prefs.getBoolean(key, defaultValue))
    }

    DisposableEffect(key, prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == key) {
                    backingState.value = sp.getBoolean(key, defaultValue)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(key, prefs) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = backingState.value
                set(newValue) {
                    backingState.value = newValue
                    prefs.edit { putBoolean(key, newValue) }
                }

            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}


@Composable
fun rememberStringPreference(
    key: String,
    defaultValue: String
): MutableState<String> {
    val context = LocalContext.current
    val prefs = remember(context) {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    val backingState = remember(key, prefs) {
        mutableStateOf(
            prefs.getString(key, defaultValue) ?: defaultValue
        )
    }

    DisposableEffect(key, prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == key) {
                    backingState.value =
                        sp.getString(key, defaultValue) ?: defaultValue
                }
            }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(key, prefs) {
        object : MutableState<String> {
            override var value: String
                get() = backingState.value
                set(newValue) {
                    backingState.value = newValue
                    prefs.edit { putString(key, newValue) }
                }

            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

/**
 * Int preference stored as a real Int, matching what [com.feldman.clock.settings.SettingsDAO]
 * reads with `getInt`. Use [rememberIntStringPreference] instead for the legacy keys that keep
 * their value as a String.
 */
@Composable
fun rememberIntPreference(
    key: String,
    defaultValue: Int
): MutableState<Int> {
    val context = LocalContext.current
    val prefs = remember(context) {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    val backingState = remember(key, prefs) {
        mutableIntStateOf(prefs.getInt(key, defaultValue))
    }

    DisposableEffect(key, prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == key) {
                    backingState.intValue = sp.getInt(key, defaultValue)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(key, prefs) {
        object : MutableState<Int> {
            override var value: Int
                get() = backingState.intValue
                set(newValue) {
                    backingState.intValue = newValue
                    prefs.edit { putInt(key, newValue) }
                }

            override fun component1() = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntStringPreference(
    key: String,
    defaultValue: Int
): MutableState<Int> {
    val context = LocalContext.current
    val prefs = remember(context) {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    val initialValue = remember(key, prefs) {
        try {
            prefs.getString(key, defaultValue.toString())
                ?.toIntOrNull()
                ?: defaultValue
        } catch (_: ClassCastException) {
            // Migration: old Int → String
            val intVal = prefs.getInt(key, defaultValue)
            prefs.edit { putString(key, intVal.toString()) }
            intVal
        }
    }

    val backingState = remember(key, prefs) {
        mutableIntStateOf(initialValue)
    }

    DisposableEffect(key, prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
                if (changedKey == key) {
                    val newValue = try {
                        sp.getString(key, defaultValue.toString())
                            ?.toIntOrNull()
                            ?: defaultValue
                    } catch (_: Exception) {
                        defaultValue
                    }

                    backingState.intValue = newValue
                }
            }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    return remember(key, prefs) {
        object : MutableState<Int> {
            override var value: Int
                get() = backingState.intValue
                set(newValue) {
                    backingState.intValue = newValue
                    prefs.edit {
                        putString(key, newValue.toString())
                    }
                }

            override fun component1() = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}
