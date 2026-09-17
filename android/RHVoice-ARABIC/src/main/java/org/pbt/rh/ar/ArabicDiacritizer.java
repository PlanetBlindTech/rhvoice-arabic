package org.pbt.rh.ar;

import android.content.Context;
import android.util.Log;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public class ArabicDiacritizer {

    private final Context context;
    private static final String TAG = "ArabicDiacritizer";
    private static final String VOCAB_ASSET = "model/vocab.json";
    private static final String MODEL_ASSET = "model/rawi_ensemble.onnx";

    private OrtEnvironment ortEnv = null;
    private OrtSession ortSession = null;
    private final Object inferLock = new Object();

    private Map<String, Integer> charToIdx = new HashMap<>();
    private Map<Integer, String> idxToDiac = new HashMap<>();
    private boolean isLoaded = false;

    private static final int CACHE_SIZE = 1500;
    private static final android.util.LruCache<String, String> DIACRITICS_CACHE = new android.util.LruCache<>(CACHE_SIZE);

    public ArabicDiacritizer(Context context) {
        this.context = context;
    }

    public synchronized boolean isLoaded() {
        return isLoaded && ortSession != null;
    }

    public void warmup() {
        if (!isLoaded()) return;
        try {
            // Run a minimal dummy inference to initialize ONNX execution memory and JIT kernels
            diacritizeWhole("بسم");
            Log.d(TAG, "Diacritizer ONNX warmup completed");
        } catch (Throwable t) {
            Log.w(TAG, "Diacritizer warmup ignored", t);
        }
    }

    public synchronized boolean load() {
        if (isLoaded && ortSession != null) return true;
        try {
            if (charToIdx.isEmpty() || idxToDiac.isEmpty()) {
                JSONObject vocabJson = loadVocab();
                if (vocabJson == null) {
                    Log.e(TAG, "Failed to load vocab.json");
                    return false;
                }

                JSONObject charToIdxObj = vocabJson.getJSONObject("char_to_idx");
                Iterator<String> charKeys = charToIdxObj.keys();
                while (charKeys.hasNext()) {
                    String key = charKeys.next();
                    charToIdx.put(key, charToIdxObj.getInt(key));
                }

                JSONObject diacToIdxObj = vocabJson.getJSONObject("diac_to_idx");
                Iterator<String> diacKeys = diacToIdxObj.keys();
                while (diacKeys.hasNext()) {
                    String key = diacKeys.next();
                    idxToDiac.put(diacToIdxObj.getInt(key), key);
                }
            }

            int cpuCores = Math.max(2, Runtime.getRuntime().availableProcessors());
            ortEnv = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT);
            sessionOptions.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            
            // Balanced thread scaling to prevent battery drain or lag on weak phones
            int intraThreads = (cpuCores <= 2) ? 1 : ((cpuCores <= 4) ? 2 : Math.min(4, cpuCores - 2));
            sessionOptions.setIntraOpNumThreads(Math.max(1, intraThreads));
            sessionOptions.setInterOpNumThreads(1);

            try {
                sessionOptions.addXnnpack(Collections.emptyMap());
            } catch (Throwable ignored) {}

            File modelDir = new File(context.getFilesDir(), "model");
            File modelFile = new File(modelDir, "rawi_ensemble.onnx");
            File legacyCacheFile = new File(context.getCacheDir(), "rawi_ensemble.onnx");

            if (!modelFile.exists() || modelFile.length() < 10000000) {
                if (legacyCacheFile.exists() && legacyCacheFile.length() > 10000000) {
                    modelFile = legacyCacheFile;
                } else {
                    Log.i(TAG, "Extracting ONNX model from assets...");
                    copyAssetToFile(MODEL_ASSET, modelFile);
                }
            }

            ortSession = ortEnv.createSession(modelFile.getAbsolutePath(), sessionOptions);

            isLoaded = true;
            Log.i(TAG, "Rawi Diacritizer loaded successfully with " + intraThreads + " intra threads on " + cpuCores + " cores");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error loading Rawi diacritizer", e);
            isLoaded = false;
            return false;
        }
    }

    public String diacritize(String text) {
        return diacritizeWhole(text);
    }

    /**
     * Raw whole-string diacritization that mirrors the Windows (Python)
     * DirectRawiDiacritizer.__call__ byte-for-byte:
     *   - build "bare" by NFD-normalising, dropping symbol (So) and mark (Mn)
     *     characters (this folds أ/ؤ/ئ to ا/و/ي, exactly like Python);
     *   - run the model on the whole string in one pass (no punctuation splitting,
     *     no phrase rules);
     *   - reattach the predicted mark to each letter and return.
     * The merge with the user's own diacritics and hamza restoration are done by
     * the caller, in the same order as Windows, so the two platforms match.
     */
    public String diacritizeWhole(String text) {
        if (!isLoaded) {
            if (!load()) return text;
        }
        if (text == null || text.trim().isEmpty()) return text;

        synchronized (DIACRITICS_CACHE) {
            String cached = DIACRITICS_CACHE.get(text);
            if (cached != null) {
                return cached;
            }
        }

        // Strip only Arabic diacritics and tatweel, keeping all base letters
        // (including أ إ آ ؤ ئ ء ى ا ٱ) exactly as they are in the input.
        StringBuilder bareSb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!isArabicDiacriticMark(c)) {
                bareSb.append(c);
            }
        }
        String bare = bareSb.toString();
        if (bare.isEmpty()) return text;

        // Tokenize: flatten Hamza variants only for model vocabulary lookup,
        // but keep the original characters in 'bare' for reassembly.
        long[] tokenIds = new long[bare.length()];
        int unkId = unkForVocab();
        for (int i = 0; i < bare.length(); i++) {
            char mapped = mapCharForVocab(bare.charAt(i));
            tokenIds[i] = charToIdx.getOrDefault(String.valueOf(mapped), unkId);
        }
        long[][] inputArray = new long[1][];
        inputArray[0] = tokenIds;

        try {
            OnnxTensor inputTensor;
            OrtSession.Result results;
            synchronized (inferLock) {
                inputTensor = OnnxTensor.createTensor(ortEnv, inputArray);
                results = ortSession.run(Collections.singletonMap("input", inputTensor));
            }
            OnnxValue outputTensor = results.get(0);
            Object outputValue = outputTensor.getValue();
            long[] diacIds = extractIds(outputValue);
            inputTensor.close();
            results.close();
            if (diacIds == null) return text;

            // Reassemble: attach predicted diacritics to the ORIGINAL characters
            // (preserving أ إ آ ؤ ئ ء ى ا exactly as in the input).
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < bare.length(); i++) {
                char ch = bare.charAt(i);
                out.append(ch);
                if (i < diacIds.length && Character.isLetter(ch)) {
                    String mark = idxToDiac.get((int) diacIds[i]);
                    if (mark != null && !mark.isEmpty()) out.append(mark);
                }
            }
            String result = out.toString();
            synchronized (DIACRITICS_CACHE) {
                DIACRITICS_CACHE.put(text, result);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Error during whole-string diacritization", e);
            return text;
        }
    }

    /**
     * Returns true for Arabic combining diacritical marks, Hamza combining
     * marks (U+0654/U+0655), superscript alef, and tatweel — characters that
     * should be stripped before feeding text to the ONNX model, but that are
     * NOT base letters. This deliberately does NOT strip Hamza seat characters
     * (أ إ آ ؤ ئ ء ٱ) which are base letters and must be preserved.
     */
    private static boolean isArabicDiacriticMark(char c) {
        return (c >= '\u064B' && c <= '\u0652')  // fathatan .. sukun
            || c == '\u0653'    // maddah above
            || c == '\u0654'    // hamza above (combining, from NFD)
            || c == '\u0655'    // hamza below (combining, from NFD)
            || c == '\u0670'    // superscript alef
            || c == '\u0640';   // tatweel
    }

    private int unkForVocab() {
        return charToIdx.getOrDefault("<UNK>", 1);
    }

    private static long[] extractIds(Object outputValue) {
        if (outputValue instanceof long[][]) return ((long[][]) outputValue)[0];
        if (outputValue instanceof long[]) return (long[]) outputValue;
        if (outputValue instanceof int[][]) {
            int[] a = ((int[][]) outputValue)[0];
            long[] r = new long[a.length];
            for (int i = 0; i < a.length; i++) r[i] = a[i];
            return r;
        }
        if (outputValue instanceof int[]) {
            int[] a = (int[]) outputValue;
            long[] r = new long[a.length];
            for (int i = 0; i < a.length; i++) r[i] = a[i];
            return r;
        }
        return null;
    }

    private char mapCharForVocab(char c) {
        if (c == 'أ' || c == 'إ' || c == 'آ' || c == 'ٱ') return 'ا';
        if (c == 'ؤ') return 'و';
        if (c == 'ئ') return 'ي';
        return c;
    }

    private JSONObject loadVocab() {
        try (InputStream is = context.getAssets().open(VOCAB_ASSET)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            int read = 0;
            while (read < size) {
                int r = is.read(buffer, read, size - read);
                if (r == -1) break;
                read += r;
            }
            return new JSONObject(new String(buffer, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.e(TAG, "Error reading vocab.json from assets", e);
            return null;
        }
    }

    private void copyAssetToFile(String assetPath, File destFile) throws java.io.IOException {
        if (destFile.getParentFile() != null) {
            destFile.getParentFile().mkdirs();
        }
        File tmpFile = new File(destFile.getAbsolutePath() + ".tmp");
        try (InputStream in = new java.io.BufferedInputStream(context.getAssets().open(assetPath), 65536);
             OutputStream out = new java.io.BufferedOutputStream(new FileOutputStream(tmpFile), 65536)) {
            byte[] buffer = new byte[65536];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
        }
        if (destFile.exists()) {
            destFile.delete();
        }
        if (!tmpFile.renameTo(destFile)) {
            tmpFile.delete();
        }
    }

    private static class CharWithDiac {
        char baseChar;
        String diacritics;
        CharWithDiac(char baseChar, String diacritics) {
            this.baseChar = baseChar;
            this.diacritics = diacritics;
        }
    }

    private static List<CharWithDiac> parseCharsWithDiac(String text) {
        List<CharWithDiac> list = new ArrayList<>();
        int i = 0;
        int len = text.length();
        while (i < len) {
            char base = text.charAt(i);
            i++;
            StringBuilder diac = new StringBuilder();
            while (i < len && isDiacriticChar(text.charAt(i))) {
                diac.append(text.charAt(i));
                i++;
            }
            list.add(new CharWithDiac(base, diac.toString()));
        }
        return list;
    }

    private static boolean isDiacriticChar(char c) {
        return (c >= 0x064B && c <= 0x0652) || c == 0x0653 || c == 0x0670;
    }

    private static final String TANWEEN_SET = "\u064B\u064C\u064D"; // ً ٌ ٍ
    private static boolean isTanween(char c) { return TANWEEN_SET.indexOf(c) != -1; }
    private static boolean isAlifChar(char c) { return c == '\u0627' || c == '\u0649'; } // ا ى
    private static boolean anyTanween(String s) {
        for (int i = 0; i < s.length(); i++) if (isTanween(s.charAt(i))) return true;
        return false;
    }
    private static String dropTanween(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (!isTanween(s.charAt(i))) b.append(s.charAt(i));
        return b.toString();
    }
    private static String keepTanween(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (isTanween(s.charAt(i))) b.append(s.charAt(i));
        return b.toString();
    }
    private static String dropShortVowels(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\u064E' && c != '\u064F' && c != '\u0650') b.append(c);
        }
        return b.toString();
    }

    /** Faithful port of the Windows (Python) _dedupe_tanween_alif, in place. */
    private static void dedupeTanweenAlif(List<CharWithDiac> pairs) {
        int n = pairs.size();
        for (int i = 0; i < n - 1; i++) {
            CharWithDiac cur = pairs.get(i);
            CharWithDiac next = pairs.get(i + 1);
            boolean isLastAlif = (i + 1 == n - 1) && isAlifChar(next.baseChar);
            if (!isLastAlif) continue;

            boolean consT = anyTanween(cur.diacritics);
            boolean alifT = anyTanween(next.diacritics);
            if (consT && alifT) {
                cur.diacritics = dropTanween(cur.diacritics);
            } else if (consT && !alifT) {
                String moved = keepTanween(cur.diacritics);
                cur.diacritics = dropTanween(cur.diacritics);
                next.diacritics = dropShortVowels(next.diacritics) + moved;
            }
        }
    }

    /**
     * Faithful port of the Windows (Python) merge_user_diacritics, so both platforms
     * produce identical tashkeel: prefer the user's own marks where supplied, else the
     * model's, then collapse a tanween duplicated across the consonant/alif boundary.
     */
    public static String mergeUserDiacritics(String originalText, String modelDiacritized) {
        if (originalText == null || modelDiacritized == null || originalText.isEmpty() || modelDiacritized.isEmpty()) {
            return modelDiacritized;
        }

        List<CharWithDiac> origList = parseCharsWithDiac(originalText);
        List<CharWithDiac> modelList = parseCharsWithDiac(modelDiacritized);

        if (origList.size() != modelList.size()) {
            String[] oWords = originalText.split(" ", -1);
            String[] mWords = modelDiacritized.split(" ", -1);
            if (oWords.length > 1 && oWords.length == mWords.length) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < oWords.length; i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(mergeUserDiacritics(oWords[i], mWords[i]));
                }
                return sb.toString();
            }
            return modelDiacritized;
        }

        List<CharWithDiac> merged = new ArrayList<>();
        for (int i = 0; i < origList.size(); i++) {
            CharWithDiac orig = origList.get(i);
            CharWithDiac model = modelList.get(i);
            String diac = !orig.diacritics.isEmpty() ? orig.diacritics : model.diacritics;
            diac = ArabicTextNormalizer.sanitizeCharVowels(orig.baseChar, diac);
            merged.add(new CharWithDiac(orig.baseChar, diac));
        }
        dedupeTanweenAlif(merged);

        StringBuilder sb = new StringBuilder();
        for (CharWithDiac cd : merged) {
            sb.append(cd.baseChar);
            if (!cd.diacritics.isEmpty()) sb.append(cd.diacritics);
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC);
    }

    public synchronized void release() {
        try {
            if (ortSession != null) {
                ortSession.close();
            }
            if (ortEnv != null) {
                ortEnv.close();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error releasing ONNX resources", e);
        }
        ortSession = null;
        ortEnv = null;
        isLoaded = false;
    }
}
