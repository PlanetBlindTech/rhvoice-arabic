package org.pbt.rh.ar;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AssetExtractor {
    private static final String TAG = "ArabicTTS.AssetExtractor";

    /**
     * Recursively copy asset files from assetDir to targetDir on the local storage.
     */
    public static void copyAssets(Context context) {
        File targetDir = new File(context.getFilesDir(), "data");
        File voiceData = new File(targetDir, "voices/Arabic/Zayd/24000/voice.data");
        File langData = new File(targetDir, "languages/Arabic/numbers.fst");

        android.content.SharedPreferences prefs = context.getSharedPreferences("asset_extractor", Context.MODE_PRIVATE);
        int lastExtractedVersion = prefs.getInt("extracted_version", -1);
        int currentVersion = BuildConfig.VERSION_CODE;

        if (lastExtractedVersion == currentVersion && voiceData.exists() && voiceData.length() > 1000000 && langData.exists()) {
            // Already fully extracted for this app version - instantaneous bypass (<1ms)
            return;
        }

        Log.i(TAG, "Extracting TTS data assets to: " + targetDir.getAbsolutePath());

        try {
            // Copy languages
            copyAssetFolder(context.getAssets(), "data/languages", new File(targetDir, "languages"));
            // Copy voices
            copyAssetFolder(context.getAssets(), "data/voices", new File(targetDir, "voices"));
            prefs.edit().putInt("extracted_version", currentVersion).apply();
            Log.i(TAG, "Assets extraction completed successfully");
        } catch (IOException e) {
            Log.e(TAG, "Failed to extract assets", e);
        }
    }

    private static void copyAssetFolder(AssetManager assetManager, String assetPath, File targetDir) throws IOException {
        String[] assets = assetManager.list(assetPath);
        if (assets == null || assets.length == 0) {
            // It's a file, copy it
            copyAssetFile(assetManager, assetPath, targetDir);
        } else {
            // It's a directory, create it and copy contents
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw new IOException("Failed to create directory: " + targetDir.getAbsolutePath());
            }
            for (String asset : assets) {
                String subAssetPath = assetPath + "/" + asset;
                File subTargetDir = new File(targetDir, asset);
                copyAssetFolder(assetManager, subAssetPath, subTargetDir);
            }
        }
    }

    private static void copyAssetFile(AssetManager assetManager, String assetPath, File targetFile) throws IOException {
        // Always overwrite info/config/phoneme metadata files to ensure asset fixes (e.g. language.info, phonemes.xml) take effect on app update
        boolean isMetadata = assetPath.endsWith(".info") || assetPath.endsWith(".conf") || assetPath.endsWith("graph.txt") || assetPath.endsWith("phonemes.xml");
        if (!isMetadata && targetFile.exists() && targetFile.length() > 0) {
            return;
        }

        // Ensure parent directory exists
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directory for file: " + targetFile.getAbsolutePath());
        }

        Log.d(TAG, "Copying asset: " + assetPath + " -> " + targetFile.getAbsolutePath());
        try (InputStream in = assetManager.open(assetPath);
             OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }
    }
}
