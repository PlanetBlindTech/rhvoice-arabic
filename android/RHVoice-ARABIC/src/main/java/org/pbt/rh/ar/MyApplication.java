package org.pbt.rh.ar;

import androidx.multidex.MultiDexApplication;
import android.content.Context;
import android.util.Log;
import com.google.android.material.color.DynamicColors;

public final class MyApplication extends MultiDexApplication {
    private static final String TAG = "ArabicTTS.MyApplication";

    public static void copyToClipboard(Context context, String label, String text) {
        CrashReporter.copyToClipboardDirect(context, label, text, false);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize comprehensive error and crash detection subsystem
        CrashReporter.initialize(this);

        DynamicColors.applyToActivitiesIfAvailable(this);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES);
        
        // Load ONNX runtime first to avoid symbol conflicts with RHVoice
        try {
            System.loadLibrary("onnxruntime");
            System.loadLibrary("onnxruntime4j_jni");
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Preloaded onnxruntime successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to preload onnxruntime", t);
            CrashReporter.captureError(this, "PreloadOnnxRuntime", t);
        }

        // Run heavy asset extraction, repository initialization, and dictionary loading
        // in a dedicated background thread so process creation on the main UI thread
        // is instantaneous (<10ms), preventing any freeze in Android Settings or TalkBack.
        new Thread(() -> {
            try {
                AssetExtractor.copyAssets(this);
                Repository.initialize(this);
                DictionaryManager.loadDefault(this);
                DictionaryManager.initEmojiAsync(this);
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Background preloading completed successfully");
                }
            } catch (Throwable t) {
                Log.e(TAG, "Background initialization error", t);
            }
        }, "MyApplication-BgInit").start();
    }
}
