# ProGuard / R8 rules for release builds.
#
# Release uses `proguard-android-optimize.txt` plus this file. AGP already keeps everything
# named in AndroidManifest.xml (activities, services, receivers, providers, the Application
# and the BackupAgent) and generates keep rules for custom Views inflated from layout XML,
# so none of that is repeated here.
#
# What IS repeated here is anything reached by *reflection*, which R8 cannot see.

# Keep source file / line numbers so Play Console crash reports are readable, then hide the
# original file name. Upload app/build/outputs/mapping/release/mapping.txt with each release.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ---------------------------------------------------------------------------
# Strip debug logging from release builds
# ---------------------------------------------------------------------------
# Debug/verbose logging is developer-facing and some of it prints whole alarm objects,
# including user-set labels, to logcat — where any app holding READ_LOGS could read it.
# R8 removes these calls entirely (requires the -optimize config, which release uses).
#
# Log.i/w/e are deliberately kept: they carry the breadcrumbs that make a crash report
# useful, and none of them log user content.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Generic signatures and annotations are required by Gson (TypeToken) and Retrofit, both of
# which inspect parameterized types at runtime.
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault


# ---------------------------------------------------------------------------
# Gson
# ---------------------------------------------------------------------------
# Gson maps JSON keys onto *field names*. If R8 renames a field, both the weather API
# responses and the user's persisted city list / standby widget layout silently decode to
# null. These types must keep their members verbatim.

-keep class com.feldman.clock.ui.clock.model.City { *; }
-keep class com.feldman.clock.ui.standby.widgets.WidgetData { *; }
-keep class com.feldman.clock.core.network.** { *; }

# Gson's own reflective machinery.
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Anonymous TypeToken subclasses (`object : TypeToken<List<City>>() {}`) rely on their
# generic superclass signature surviving.
-if class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation class <1>


# ---------------------------------------------------------------------------
# Retrofit + OkHttp
# ---------------------------------------------------------------------------
# Retrofit builds implementations of these interfaces from their annotations at runtime.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# Retain service method annotations and parameter types.
-keepclasseswithmembers,includedescriptorclasses interface * {
    @retrofit2.http.* <methods>;
}

-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**


# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Navigation destinations (Dest and its nested objects) are @Serializable. The compiler
# plugin generates $$serializer companions that are looked up reflectively.

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.feldman.clock.**$$serializer { *; }
-keepclassmembers class com.feldman.clock.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.feldman.clock.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# The Dest hierarchy is a sealed interface of `data object`s used as nav keys; keep the whole
# tree so both serialization and Parcelable round-trips stay intact.
-keep class com.feldman.clock.app.navigation.Dest { *; }
-keep class com.feldman.clock.app.navigation.Dest$* { *; }


# ---------------------------------------------------------------------------
# AndroidX Preference
# ---------------------------------------------------------------------------
# Preference subclasses are instantiated by name from res/xml preference trees.
-keep public class * extends androidx.preference.Preference {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context);
}


# ---------------------------------------------------------------------------
# App components reached indirectly
# ---------------------------------------------------------------------------
# Alarm/timer/stopwatch state is driven by PendingIntents and AlarmManager callbacks that
# reference these by class name across process restarts and reboots.
-keep class com.feldman.clock.alarm.** { *; }
-keep class com.feldman.clock.timer.** { *; }
-keep class com.feldman.clock.stopwatch.** { *; }

# AppWidgetProviders and the DreamService are resolved by name from the framework.
-keep class com.feldman.clock.ui.widgets.** { *; }
-keep class com.feldman.clock.ui.standby.ClockStandbyService { *; }

# Backup/restore serialises settings by key; the agent and its helpers must not be renamed.
-keep class com.feldman.clock.app.ClockBackupAgent { *; }

# Parcelable implementations (nav keys, alarm models) need their CREATOR field.
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Enums are commonly persisted by name() in DataStore/SharedPreferences.
-keepclassmembers enum com.feldman.clock.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ---------------------------------------------------------------------------
# Compose
# ---------------------------------------------------------------------------
# Compose ships its own consumer rules; this only silences a known desugaring warning.
-dontwarn androidx.compose.**
