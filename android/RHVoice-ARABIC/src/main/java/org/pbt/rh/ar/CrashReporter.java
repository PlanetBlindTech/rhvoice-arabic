package org.pbt.rh.ar;

import android.app.Activity;
import android.app.Application;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Robust Crash and Error Detection Subsystem for RHVoice Arabic.
 * - Detects unhandled exceptions and unexpected crashes across all threads.
 * - Formats full diagnostics (Device, OS, App version, Thread, Timestamp, Stack Trace).
 * - Copies the error to the clipboard once and only once per crash/error event.
 * - Handles Android 10+ background clipboard restrictions by persisting pending crash
 *   reports and flushing them to clipboard when any Activity resumes in foreground.
 */
public final class CrashReporter {

    private static final String TAG = "CrashReporter";
    private static final String PREF_PENDING_CRASH = "pending_crash_report_payload";
    private static final String PREF_LAST_COPIED_HASH = "last_copied_crash_hash";

    // Ensures we only copy once per crash cascade
    private static final AtomicBoolean HAS_COPIED_CRASH_ONCE = new AtomicBoolean(false);

    private static volatile boolean isInitialized = false;

    private CrashReporter() {}

    /**
     * Initializes global uncaught exception handling and activity lifecycle hooks.
     */
    public static void initialize(Application app) {
        if (isInitialized || app == null) return;
        isInitialized = true;

        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                handleUncaughtException(app, thread, throwable);
            } catch (Throwable t) {
                Log.e(TAG, "Failed in custom uncaught exception handler", t);
            } finally {
                if (defaultHandler != null) {
                    defaultHandler.uncaughtException(thread, throwable);
                }
            }
        });

        // Register Activity Lifecycle Callbacks to flush any pending crash report when app comes to foreground
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

            @Override
            public void onActivityStarted(Activity activity) {}

            @Override
            public void onActivityResumed(Activity activity) {
                flushPendingCrashReport(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {}

            @Override
            public void onActivityStopped(Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

            @Override
            public void onActivityDestroyed(Activity activity) {}
        });
    }

    /**
     * Internal handler for unexpected crashes / unhandled exceptions.
     */
    private static void handleUncaughtException(Context context, Thread thread, Throwable throwable) {
        String report = formatErrorReport("UNEXPECTED_CRASH", thread != null ? thread.getName() : "Unknown", throwable);
        Log.e(TAG, report, throwable);

        // 1. Persist to SharedPreferences so it's guaranteed saved even if process dies instantly
        savePendingCrashReport(context, report);

        // 2. Try immediate copy once (if foreground / supported)
        if (HAS_COPIED_CRASH_ONCE.compareAndSet(false, true)) {
            copyToClipboardDirect(context, "ArabicTTS_Crash", report, false);
        }
    }

    /**
     * General error capture method for critical try-catch blocks across the app.
     * Copies to clipboard once and only once for new errors.
     */
    public static void captureError(Context context, String source, Throwable throwable) {
        if (context == null || throwable == null) return;
        String report = formatErrorReport("CAUGHT_ERROR: " + source, Thread.currentThread().getName(), throwable);
        Log.e(TAG, report, throwable);

        // Check deduplication hash so we don't spam clipboard with identical repeated errors
        int errorHash = report.hashCode();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        int lastHash = prefs.getInt(PREF_LAST_COPIED_HASH, 0);

        if (errorHash != lastHash) {
            prefs.edit().putInt(PREF_LAST_COPIED_HASH, errorHash).apply();
            copyToClipboardDirect(context, "ArabicTTS_Error", report, true);
        }
    }

    /**
     * Copies text to the Android Clipboard, handling MainLooper threading safely.
     */
    public static void copyToClipboardDirect(Context context, String label, String text, boolean showToast) {
        if (context == null || text == null) return;
        Runnable copyRunnable = () -> {
            try {
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ClipData clip = ClipData.newPlainText(label, text);
                    clipboard.setPrimaryClip(clip);
                    if (showToast && context instanceof Activity) {
                        Toast.makeText(context, "تم نسخ تقرير الخطأ إلى الحافظة", Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Throwable t) {
                Log.w(TAG, "Could not write to clipboard directly: " + t.getMessage());
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            copyRunnable.run();
        } else {
            new Handler(Looper.getMainLooper()).post(copyRunnable);
        }
    }

    /**
     * Formats a comprehensive diagnostic report.
     */
    private static String formatErrorReport(String errorType, String threadName, Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());

        sb.append("========================================\n");
        sb.append("🔴 RHVOICE ARABIC - ERROR REPORT\n");
        sb.append("========================================\n");
        sb.append("Type: ").append(errorType).append("\n");
        sb.append("Time: ").append(timestamp).append("\n");
        sb.append("Thread: ").append(threadName).append("\n");
        sb.append("App Version: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        sb.append("Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("----------------------------------------\n");
        sb.append("Exception: ").append(throwable != null ? throwable.getClass().getName() : "null").append("\n");
        sb.append("Message: ").append(throwable != null ? throwable.getMessage() : "null").append("\n");
        sb.append("----------------------------------------\n");
        sb.append("Stack Trace:\n");
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            sb.append(sw.toString());
        } else {
            sb.append("No stack trace available.\n");
        }
        sb.append("========================================\n");
        return sb.toString();
    }

    private static void savePendingCrashReport(Context context, String report) {
        if (context == null || report == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
            prefs.edit().putString(PREF_PENDING_CRASH, report).commit(); // commit() synchronously for crash safety
        } catch (Throwable ignored) {}
    }

    /**
     * Flushes any pending crash report to the clipboard when the user opens any activity.
     */
    private static void flushPendingCrashReport(Activity activity) {
        if (activity == null) return;
        try {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
            String pending = prefs.getString(PREF_PENDING_CRASH, null);
            if (pending != null && !pending.isEmpty()) {
                // Clear immediately so it only copies once
                prefs.edit().remove(PREF_PENDING_CRASH).apply();
                copyToClipboardDirect(activity, "ArabicTTS_Crash", pending, true);
                Log.i(TAG, "Pending crash report successfully copied to clipboard once on activity resume.");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Error flushing pending crash report", t);
        }
    }
}
