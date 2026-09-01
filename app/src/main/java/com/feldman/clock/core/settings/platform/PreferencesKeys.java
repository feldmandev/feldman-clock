// SPDX-License-Identifier: GPL-3.0-only

package com.feldman.clock.settings;

public class PreferencesKeys {

    // **************
    // ** SETTINGS **
    // **************

    // Interface
    public static final String KEY_THEME = "key_theme";
    public static final String KEY_DARK_MODE = "key_dark_mode";
    public static final String KEY_ACCENT_COLOR = "key_accent_color";
    public static final String KEY_AUTO_NIGHT_ACCENT_COLOR = "key_auto_night_accent_color";
    public static final String KEY_NIGHT_ACCENT_COLOR = "key_night_accent_color";
    public static final String KEY_CARD_BACKGROUND = "key_card_background";
    public static final String KEY_CARD_BORDER = "key_card_border";
    public static final String KEY_CUSTOM_LANGUAGE_CODE = "key_custom_language_code";
    public static final String KEY_TAB_TO_DISPLAY = "key_tab_to_display";
    public static final String KEY_VIBRATIONS = "key_vibrations";
    public static final String KEY_FADE_TRANSITIONS = "key_fade_transitions";

    // Clock
    public static final String KEY_DISPLAY_CLOCK_SECONDS = "key_display_clock_seconds";
    public static final String KEY_SORT_CITIES = "key_sort_cities";
    public static final String KEY_AUTO_HOME_CLOCK = "key_automatic_home_clock";
    public static final String KEY_HOME_TIME_ZONE = "key_home_time_zone";
    public static final String KEY_TIME_FORMAT = "key_time_format";
    public static final String KEY_DATE_FORMAT = "key_date_format";

    // Alarm
    public static final String KEY_DEFAULT_ALARM_RINGTONE = "key_default_alarm_ringtone";
    public static final String KEY_ENABLE_PER_ALARM_AUTO_SILENCE = "key_enable_per_alarm_auto_silence";
    public static final String KEY_AUTO_SILENCE_DURATION = "key_auto_silence_duration";
    public static final String KEY_ALARM_SNOOZE_DURATION = "key_alarm_snooze_duration";
    public static final String KEY_ENABLE_PER_ALARM_MISSED_REPEAT_LIMIT = "key_enable_per_alarm_missed_repeat_limit";
    public static final String KEY_MISSED_ALARM_REPEAT_LIMIT = "key_missed_alarm_repeat_limit";
    public static final String KEY_ENABLE_PER_ALARM_VOLUME_CRESCENDO_DURATION = "key_enable_per_alarm_volume_crescendo_duration";
    public static final String KEY_ALARM_VOLUME_CRESCENDO_DURATION = "key_alarm_volume_crescendo_duration";
    public static final String KEY_ENABLE_PER_ALARM_VOLUME = "key_enable_per_alarm_volume";
    public static final String KEY_ADVANCED_AUDIO_PLAYBACK = "key_advanced_audio_playback";
    public static final String KEY_AUTO_ROUTING_TO_BLUETOOTH_DEVICE = "key_auto_routing_to_bluetooth_device";
    public static final String KEY_SYSTEM_MEDIA_VOLUME = "key_system_media_volume";
    public static final String KEY_BLUETOOTH_VOLUME = "key_bluetooth_volume";
    public static final String KEY_VOLUME_BUTTONS = "key_volume_button_setting";
    public static final String KEY_POWER_BUTTON = "key_power_button";
    public static final String KEY_FLIP_ACTION = "key_flip_action";
    public static final String KEY_SHAKE_ACTION = "key_shake_action";
    public static final String KEY_SHAKE_INTENSITY = "key_shake_intensity";
    public static final String KEY_SORT_ALARM = "key_sort_alarm";
    public static final String KEY_DISPLAY_ENABLED_ALARMS_FIRST = "key_display_enabled_alarms_first";
    public static final String KEY_WEEK_START = "key_week_start";
    public static final String KEY_DISPLAY_DISMISS_BUTTON = "key_display_dismiss_button";
    public static final String KEY_ALARM_NOTIFICATION_REMINDER_TIME = "key_alarm_notification_reminder_time";
    public static final String KEY_SHOW_UPCOMING_ALARM_NOTIFICATION = "key_show_upcoming_alarm_notification";
    public static final String KEY_SHOW_MISSED_ALARM_NOTIFICATION = "key_show_missed_alarm_notification";
    public static final String KEY_VIBRATION_PATTERN = "key_vibration_pattern";
    public static final String KEY_VIBRATION_START_DELAY = "key_vibration_start_delay";
    public static final String KEY_ENABLE_ALARM_VIBRATIONS_BY_DEFAULT = "key_enable_alarm_vibrations_by_default";
    public static final String KEY_ENABLE_SNOOZED_OR_DISMISSED_ALARM_VIBRATIONS = "key_enable_snoozed_or_dismissed_alarm_vibrations";
    public static final String KEY_TURN_ON_BACK_FLASH_FOR_TRIGGERED_ALARM = "key_turn_on_back_flash_for_triggered_alarm";
    public static final String KEY_AUTO_OPEN_FIRING_SCREEN = "key_auto_open_firing_screen";
    public static final String KEY_ENABLE_DELETE_OCCASIONAL_ALARM_BY_DEFAULT = "key_enable_delete_occasional_alarm_by_default";
    public static final String KEY_MATERIAL_TIME_PICKER_STYLE = "key_material_time_picker_style";

    // Timer
    public static final String KEY_TIMER_RINGTONE = "key_timer_ringtone";
    public static final String KEY_TIMER_AUTO_SILENCE_DURATION = "key_timer_auto_silence_duration";
    public static final String KEY_TIMER_VOLUME_CRESCENDO_DURATION = "key_timer_volume_crescendo_duration";
    public static final String KEY_TIMER_VIBRATE = "key_timer_vibrate";
    public static final String KEY_TIMER_FLIP_ACTION = "key_timer_flip_action";
    public static final String KEY_TIMER_SHAKE_ACTION = "key_timer_shake_action";
    public static final String KEY_TIMER_SHAKE_INTENSITY = "key_timer_shake_intensity";
    public static final String KEY_SORT_TIMER = "key_sort_timer";
    public static final String KEY_TIMER_ADD_TIME_BUTTON_VALUE = "key_timer_add_time_button_value";

    // Standby
    public static final String KEY_STANDBY_CLOCK_STYLE = "key_standby_clock_style";
    public static final String KEY_DISPLAY_STANDBY_CLOCK_SECONDS = "key_display_standby_clock_seconds"; //show seconds
    public static final String KEY_STANDBY_CLOCK_COLOR_PICKER = "key_standby_clock_color_picker";
    public static final String KEY_STANDBY_HOUR_COLOR_PICKER = "key_standby_hour_color_picker";
    public static final String KEY_STANDBY_MINUTE_COLOR_PICKER = "key_standby_minute_color_picker";
    public static final String KEY_STANDBY_SPLIT_COLOR_ORDER_MIGRATED = "key_standby_split_color_order_migrated";
    public static final String KEY_STANDBY_DATE_COLOR_PICKER = "key_standby_date_color_picker";
    public static final String KEY_STANDBY_SHOW_DATE = "key_standby_show_date";
    public static final String KEY_STANDBY_DATE_FORMAT = "key_standby_date_format";
    public static final String KEY_STANDBY_DATE_SIZE_PERCENT = "key_standby_date_size_percent";
    public static final String KEY_STANDBY_DATE_SPACING_DP = "key_standby_date_spacing_dp";
    public static final String KEY_STANDBY_SHOW_NEXT_ALARM = "key_standby_show_next_alarm";
    public static final String KEY_STANDBY_NEXT_ALARM_COLOR_PICKER = "key_standby_next_alarm_color_picker";
    public static final String KEY_STANDBY_BRIGHTNESS = "key_standby_brightness";
    public static final String KEY_STANDBY_DIGITAL_CLOCK_IN_BOLD = "key_standby_digital_clock_in_bold";
    public static final String KEY_STANDBY_DIGITAL_CLOCK_IN_ITALIC = "key_standby_digital_clock_in_italic";
    public static final String KEY_STANDBY_DATE_IN_BOLD = "key_standby_date_in_bold";
    public static final String KEY_STANDBY_DATE_IN_ITALIC = "key_standby_date_in_italic";
    public static final String KEY_STANDBY_NEXT_ALARM_IN_BOLD = "key_standby_next_alarm_in_bold";
    public static final String KEY_STANDBY_NEXT_ALARM_IN_ITALIC = "key_standby_next_alarm_in_italic";
    public static final String KEY_STANDBY_ROTATION_MODE = "key_standby_rotation_mode";
    // Home-card surface in the standby dashboard: 0 = lightly grayed fill, 1 = outlined.
    public static final String KEY_STANDBY_CARD_STYLE = "key_standby_card_style";
    public static final String KEY_STANDBY_SCALE_PERCENT = "key_standby_scale_percent";
    public static final String KEY_STANDBY_CLOCK_WEIGHT_PERCENT = "key_standby_clock_weight_percent";
    public static final String KEY_STANDBY_CLOCK_WIDTH_PERCENT = "key_standby_clock_width_percent";
    public static final String KEY_STANDBY_CLOCK_WIDTH_AUTO = "key_standby_clock_width_auto";
    public static final String KEY_STANDBY_CLOCK_ROUNDNESS_PERCENT = "key_standby_clock_roundness_percent";
    public static final String KEY_STANDBY_TIME_SPACING_DP = "key_standby_time_spacing_dp";
    public static final String KEY_STANDBY_FONT_WEIGHT = "key_standby_font_weight";
    public static final String KEY_STANDBY_WIRELESS_ONLY = "key_standby_wireless_only";
    public static final String KEY_STANDBY_WIDGET_IDS = "key_standby_widget_ids";
    public static final String KEY_STANDBY_MAX_SLANT = "key_standby_max_slant";
    public static final String KEY_STANDBY_FONT_WIDTH = "key_standby_font_width";
    public static final String KEY_STANDBY_VIBRANT_PALETTE = "key_standby_vibrant_palette";
    public static final String KEY_STANDBY_DYNAMIC_COLOR = "key_standby_dynamic_color";
    public static final String KEY_STANDBY_SINGLE_COLOR_MODE = "key_standby_single_color_mode";
    public static final String KEY_STANDBY_WIDGETS_CONFIG = "key_standby_widgets_config";
    public static final String KEY_STANDBY_PAGE_LAYOUT = "key_standby_page_layout";
    public static final String KEY_STANDBY_SPLIT_RATIO = "key_standby_split_ratio";

}
