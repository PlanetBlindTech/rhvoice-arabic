package org.pbt.rh.ar;

import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import android.util.Log;

import android.os.Looper;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ServiceLifecycleDispatcher;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class RHVoiceService extends TextToSpeechService implements LifecycleOwner {

    public static final String KEY_PARAM_TEST_VOICE = "org.pbt.rh.ar.param_test_voice";
    public static final String ACTION_CHECK_DATA = "org.pbt.rh.ar.ACTION_CHECK_DATA";
    public static final String ACTION_CONFIG_CHANGE = "org.pbt.rh.ar.ACTION_CONFIG_CHANGE";

    private static final String TAG = "RHVoiceTTS";

    private static class Tts {
        public TTSEngine engine = null;
        public List<AndroidVoiceInfo> voices = new ArrayList<>();
        public Map<String, LanguageInfo> languageIndex = new HashMap<>();
        public Map<String, AndroidVoiceInfo> voiceIndex = new HashMap<>();

        public Tts() {}

        public Tts(Tts other, boolean passEngine) {
            this.voices = other.voices;
            this.languageIndex = other.languageIndex;
            this.voiceIndex = other.voiceIndex;
            if (!passEngine) return;
            this.engine = other.engine;
            other.engine = null;
        }
    }

    private static class TtsManager {
        private Tts tts;
        private boolean done;

        public synchronized void reset(Tts newTts) {
            if (newTts == null) throw new IllegalArgumentException();
            if (tts != null && tts.engine != null) tts.engine.shutdown();
            tts = newTts;
        }

        public synchronized void destroy() {
            done = true;
            if (tts != null && tts.engine != null) tts.engine.shutdown();
            tts = null;
        }

        public synchronized void unload() {
            if (tts != null && tts.engine != null) {
                try {
                    tts.engine.shutdown();
                } catch (Throwable ignored) {}
                tts.engine = null;
            }
        }

        public synchronized boolean isEngineReady() {
            return !done && tts != null && tts.engine != null && !tts.voices.isEmpty();
        }

        public synchronized Tts acquireForSynthesis() {
            if (done || tts == null || tts.engine == null || tts.voices.isEmpty()) return null;
            return new Tts(tts, true);
        }

        public synchronized void release(Tts usedTts) {
            if (usedTts == null || usedTts.engine == null) throw new IllegalArgumentException();
            if (done || tts.engine != null) usedTts.engine.shutdown();
            else tts = new Tts(usedTts, true);
        }

        public synchronized Tts get() {
            if (tts == null) return null;
            return new Tts(tts, false);
        }
    }

    private final TtsManager ttsManager = new TtsManager();
    private volatile boolean speaking = false;
    private List<String> paths = new ArrayList<>();
    private Handler handler;
    private ArabicDiacritizer diacritizer;

    private final Object initLock = new Object();
    private volatile boolean isInitComplete = false;
    private volatile CountDownLatch initLatch = new CountDownLatch(1);

    private static final long IDLE_RELEASE_DELAY_MS = 3 * 60 * 1000; // 3 minutes idle
    private final Runnable idleReleaseRunnable = () -> {
        new Thread(() -> {
            synchronized (initLock) {
                if (speaking) {
                    scheduleIdleRelease(IDLE_RELEASE_DELAY_MS);
                    return;
                }
                Log.i(TAG, "Releasing TTS engine and diacritizer after 3 minutes idle to save RAM and battery");
                if (diacritizer != null) {
                    diacritizer.release();
                }
                ttsManager.unload();
                isInitComplete = false;
                initLatch = new CountDownLatch(1);
            }
        }, "RHVoice-IdleRelease").start();
    };

    private void scheduleIdleRelease(long delayMs) {
        if (handler != null) {
            handler.removeCallbacks(idleReleaseRunnable);
            handler.postDelayed(idleReleaseRunnable, delayMs);
        }
    }

    private void cancelIdleRelease() {
        if (handler != null) {
            handler.removeCallbacks(idleReleaseRunnable);
        }
    }

    private void triggerModelPreload() {
        cancelIdleRelease();
        new Thread(() -> {
            synchronized (initLock) {
                if (isInitComplete && ttsManager.isEngineReady() && diacritizer != null && diacritizer.isLoaded()) {
                    return;
                }
                long start = System.currentTimeMillis();
                try {
                    // 1. Ensure assets are extracted (fast bypass if already done)
                    AssetExtractor.copyAssets(getApplicationContext());

                    // 2. Ensure dictionary rules are loaded
                    DictionaryManager.loadDefault(getApplicationContext());
                    DictionaryManager.initEmojiAsync(getApplicationContext());

                    // 3. Preload and warm up ONNX diacritizer
                    if (diacritizer == null) {
                        diacritizer = new ArabicDiacritizer(getApplicationContext());
                    }
                    if (diacritizer.load()) {
                        diacritizer.warmup();
                    }

                    // 4. Preload native TTS engine
                    initializeEngine();

                    isInitComplete = true;
                    Log.i(TAG, "Models preloaded and warmed up in " + (System.currentTimeMillis() - start) + "ms");
                } catch (Throwable t) {
                    Log.e(TAG, "Preload failed", t);
                } finally {
                    initLatch.countDown();
                    scheduleIdleRelease(IDLE_RELEASE_DELAY_MS);
                }
            }
        }, "RHVoice-Preload").start();
    }

    private void ensureModelsReadySynchronous() {
        synchronized (initLock) {
            if (isInitComplete && ttsManager.isEngineReady() && diacritizer != null && diacritizer.isLoaded()) {
                return;
            }
            try {
                AssetExtractor.copyAssets(getApplicationContext());
                DictionaryManager.loadDefault(getApplicationContext());
                if (diacritizer == null) {
                    diacritizer = new ArabicDiacritizer(getApplicationContext());
                }
                if (!diacritizer.isLoaded()) {
                    diacritizer.load();
                }
                initializeEngine();
                isInitComplete = true;
            } catch (Throwable t) {
                Log.e(TAG, "ensureModelsReadySynchronous error", t);
            } finally {
                initLatch.countDown();
            }
        }
    }

    private final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "RHVoice-Prefetch");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY);
        return t;
    });
    private volatile Future<?> currentPrefetchFuture = null;

    private class Player implements TTSClient {
        private final SynthesisCallback callback;
        private int sampleRate;
        private final AudioPostFilter postFilter = new AudioPostFilter();

        // Reusable buffer to avoid GC pressure from per-call allocations.
        // Grown lazily on first use or when a larger buffer is needed.
        private ByteBuffer reusableBuffer;
        private byte[] reusableBytes;

        public Player(SynthesisCallback callback) {
            this.callback = callback;
        }

        public boolean setSampleRate(int sr) {
            if (sampleRate != 0) return true;
            sampleRate = sr;
            postFilter.reset();
            callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1);
            return true;
        }

        public boolean playSpeech(short[] samples) {
            if (!speaking) return false;
            postFilter.process(samples);

            int needed = samples.length * 2;
            if (reusableBuffer == null || reusableBuffer.capacity() < needed) {
                reusableBuffer = ByteBuffer.allocate(needed);
                reusableBuffer.order(ByteOrder.LITTLE_ENDIAN);
                reusableBytes = reusableBuffer.array();
            }
            reusableBuffer.clear();
            reusableBuffer.asShortBuffer().put(samples);
            final int size = callback.getMaxBufferSize();
            int offset = 0;
            int count;
            while (offset < needed) {
                if (!speaking) return false;
                count = Math.min(size, needed - offset);
                if (callback.audioAvailable(reusableBytes, offset, count) != TextToSpeech.SUCCESS) return false;
                offset += count;
            }
            return true;
        }
    }

    private final ServiceLifecycleDispatcher lifecycleDispatcher = new ServiceLifecycleDispatcher(this);

    public Lifecycle getLifecycle() {
        return lifecycleDispatcher.getLifecycle();
    }

    private DictionaryManager.UserDict userDictionary;
    private final android.content.BroadcastReceiver configReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, android.content.Intent intent) {
            if (userDictionary != null) {
                userDictionary.load();
            }
        }
    };

    @Override
    public void onCreate() {
        userDictionary = new DictionaryManager.UserDict(this);
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
            .registerReceiver(configReceiver, new android.content.IntentFilter(ACTION_CONFIG_CHANGE));
        diacritizer = new ArabicDiacritizer(this);
        handler = new Handler(Looper.getMainLooper());
        lifecycleDispatcher.onServicePreSuperOnCreate();

        paths = new ArrayList<>();
        File langDir = new File(getFilesDir(), "data/languages/Arabic");
        File voiceDir = new File(getFilesDir(), "data/voices/Arabic/Zayd");
        paths.add(langDir.getAbsolutePath());
        paths.add(voiceDir.getAbsolutePath());

        super.onCreate();

        // Start preloading models immediately in background:
        triggerModelPreload();
    }

    private void initializeEngine() {
        if (ttsManager.isEngineReady()) return;
        Tts tts = new Tts();
        try {
            File configDir = Config.getDir(this);
            tts.engine = new TTSEngine("", configDir.getAbsolutePath(), paths, "", CoreLogger.instance);
        } catch (Throwable e) {
            Log.e(TAG, "Engine init failed", e);
            CrashReporter.captureError(this, "RHVoiceService.initializeEngine", e);
            return;
        }
        List<VoiceInfo> engineVoices = tts.engine.getVoices();
        if (engineVoices.isEmpty()) {
            Log.e(TAG, "No voices found");
            CrashReporter.copyToClipboardDirect(this, "ArabicTTS_InitError", "RHVoiceService.initialize Error: No voices found in paths " + paths, false);
            tts.engine.shutdown();
            return;
        }
        for (VoiceInfo engineVoice : engineVoices) {
            AndroidVoiceInfo nextVoice = new AndroidVoiceInfo(engineVoice);
            tts.voices.add(nextVoice);
            tts.voiceIndex.put(nextVoice.getName().toLowerCase(), nextVoice);
            LanguageInfo engineLanguage = engineVoice.getLanguage();
            tts.languageIndex.put(engineLanguage.getAlpha3Code(), engineLanguage);
        }
        ttsManager.reset(tts);
    }

    @Override
    public IBinder onBind(Intent intent) {
        cancelIdleRelease();
        triggerModelPreload();
        lifecycleDispatcher.onServicePreSuperOnBind();
        return super.onBind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // When client unbinds, release after 1 minute if idle
        scheduleIdleRelease(60 * 1000);
        return super.onUnbind(intent);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        lifecycleDispatcher.onServicePreSuperOnStart();
        super.onStart(intent, startId);
    }

    @Override
    public void onDestroy() {
        cancelIdleRelease();
        try {
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(this)
                .unregisterReceiver(configReceiver);
        } catch (Exception ignored) {}
        if (diacritizer != null) {
            diacritizer.release();
            diacritizer = null;
        }
        prefetchExecutor.shutdownNow();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        ttsManager.destroy();
        lifecycleDispatcher.onServicePreSuperOnDestroy();
        super.onDestroy();
    }

    @Override
    protected String[] onGetLanguage() {
        return new String[]{"ara", "EGY", "Zayd"};
    }

    @Override
    protected int onIsLanguageAvailable(String language, String country, String variant) {
        if (language == null || language.trim().isEmpty()) {
            return TextToSpeech.LANG_NOT_SUPPORTED;
        }
        String lang = language.trim().toLowerCase(Locale.ROOT);
        if (lang.equals("ara") || lang.equals("ar") || lang.equals("arabic")) {
            return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    @Override
    protected int onLoadLanguage(String language, String country, String variant) {
        return onIsLanguageAvailable(language, country, variant);
    }

    @Override
    protected void onStop() {
        speaking = false;
        Future<?> f = currentPrefetchFuture;
        if (f != null) {
            f.cancel(true);
            currentPrefetchFuture = null;
        }
    }

    private static boolean isPunctuationOnly(String text) {
        if (text == null) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private String processArabicText(String text) {
        return processArabicText(text, false);
    }

    private String processArabicText(String text, boolean isSingleCharRequest) {
        if (text == null) return null;

        if (isSingleCharRequest) {
            String single = ArabicTextNormalizer.getSingleCharName(text);
            if (single != null && !single.isEmpty()) return single;
        }

        if (text.trim().isEmpty()) return text;

        boolean readEmojis = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(DictionaryManager.PREF_READ_EMOJIS, true);
        String emojiProcessed = readEmojis ? DictionaryManager.replaceEmojis(text) : text;

        if (isSingleCharRequest) {
            String single = ArabicTextNormalizer.getSingleCharName(emojiProcessed);
            if (single != null && !single.isEmpty()) return single;
        }

        String norm = ArabicTextNormalizer.normalize(emojiProcessed, isSingleCharRequest);
        if (ArabicTextNormalizer.isPreDiacritizedSpelling(norm)) return norm;

        boolean hasArabic = false;
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            if (c >= 0x0600 && c <= 0x06FF) { hasArabic = true; break; }
        }
        if (hasArabic && norm.trim().length() > 2 && diacritizer != null) {
            try {
                String diacritized = diacritizer.diacritizeWhole(norm);
                String merged = ArabicDiacritizer.mergeUserDiacritics(norm, diacritized);
                merged = ArabicTextNormalizer.restoreHamza(norm, merged);
                merged = ArabicTextNormalizer.fixNaaPronoun(norm, merged);
                merged = ArabicTextNormalizer.dedupeTanween(merged);
                merged = ArabicTextNormalizer.fixBareHamzaVowel(merged);
                merged = ArabicTextNormalizer.swapTanweenFathaAlif(merged);
                
                android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                if (prefs.getBoolean("pause_sukoon", true)) {
                    merged = ArabicTextNormalizer.sukoonizeEndOfSentence(merged);
                }
                return merged;
            } catch (Exception e) {
                Log.e(TAG, "Diacritization failed", e);
            }
        }
        
        android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        if (prefs.getBoolean("pause_sukoon", true)) {
            norm = ArabicTextNormalizer.sukoonizeEndOfSentence(norm);
        }
        norm = ArabicTextNormalizer.swapTanweenFathaAlif(norm);
        return norm;
    }

    @Override
    protected void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        cancelIdleRelease();
        if (!isInitComplete || !ttsManager.isEngineReady()) {
            try {
                initLatch.await(8, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            if (!ttsManager.isEngineReady() || diacritizer == null || !diacritizer.isLoaded()) {
                ensureModelsReadySynchronous();
            }
        }

        Tts tts = ttsManager.acquireForSynthesis();
        if (tts == null) {
            Log.e(TAG, "Engine not ready");
            callback.error();
            return;
        }
        try {
            speaking = true;
            String rawText = request.getText();
            if (rawText != null) {
                if (userDictionary != null) {
                    rawText = userDictionary.applyReplacements(rawText);
                }
                rawText = DictionaryManager.applyDefaultRules(rawText);
            }

            final SynthesisParameters params = new SynthesisParameters();
            params.setVoiceProfile("Zayd");

            android.content.SharedPreferences prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
            int appRate = 100;
            try {
                appRate = Integer.parseInt(prefs.getString("language.ara.rate", "100"));
            } catch (Exception e) {
                try {
                    appRate = prefs.getInt("language.ara.rate", 100);
                } catch (Exception ignored) {}
            }

            int appPitch = 100;
            try {
                appPitch = Integer.parseInt(prefs.getString("language.ara.pitch", "100"));
            } catch (Exception e) {
                try {
                    appPitch = prefs.getInt("language.ara.pitch", 100);
                } catch (Exception ignored) {}
            }

            int appVolume = 100;
            try {
                appVolume = Integer.parseInt(prefs.getString("language.ara.volume", "100"));
            } catch (Exception e) {
                try {
                    appVolume = prefs.getInt("language.ara.volume", 100);
                } catch (Exception ignored) {}
            }

            double baseRate = appRate / 100.0;
            double basePitch = appPitch / 100.0;
            double baseVolume = appVolume / 100.0;

            int reqRateInt = request.getSpeechRate();
            double reqRate = extractRequestRate(request, reqRateInt);

            int reqPitchInt = request.getPitch();
            double reqPitch = extractRequestPitch(request, reqPitchInt);

            double reqVolume = extractRequestVolume(request);

            double rate = baseRate * reqRate;
            if (rate < 0.2) rate = 0.2;
            if (rate > 8.0) rate = 8.0;

            double pitch = basePitch * reqPitch;
            if (pitch < 0.3) pitch = 0.3;
            if (pitch > 2.5) pitch = 2.5;

            double volume = baseVolume * reqVolume;
            if (volume < 0.0) volume = 0.0;
            if (volume > 4.0) volume = 4.0;

            params.setRate(rate);
            params.setPitch(pitch);
            params.setVolume(volume);

            boolean isSSML = false;
            if (rawText != null && (rawText.startsWith("<speak") || rawText.contains("<prosody") || rawText.contains("</prosody>"))) {
                isSSML = true;
                params.setSSMLMode(true);
            }

            final Player player = new Player(callback);
            player.setSampleRate(24000);

            boolean isSingleCharRequest = (rawText != null && (
                rawText.length() == 1 ||
                rawText.trim().length() == 1 ||
                rawText.trim().isEmpty() ||
                ArabicTextNormalizer.getSingleCharName(rawText) != null
            ));
            if (isSSML) {
                String textToSpeak = rawText;
                try {
                    textToSpeak = processArabicText(rawText, isSingleCharRequest);
                } catch (Exception e) {
                    Log.e(TAG, "Preprocessing failed", e);
                }
                if (textToSpeak != null) {
                    textToSpeak = textToSpeak.replace("ـ", "");
                }
                tts.engine.speak(textToSpeak, params, player);
            } else {
                if (rawText != null) {
                    if (isSingleCharRequest) {
                        String textToSpeak = rawText;
                        try {
                            textToSpeak = processArabicText(rawText, true);
                        } catch (Exception e) {
                            Log.e(TAG, "Preprocessing failed", e);
                        }
                        if (textToSpeak != null) {
                            textToSpeak = textToSpeak.replace("ـ", "");
                        }
                        tts.engine.speak(textToSpeak, params, player);
                    } else {
                        String[] rawSentences = rawText.split("(?<=(?<!\\d)[.،؛؟!?,;\\n]|(?<=\\d)[،؛؟!?,;\\n]|(?<!\\d)\\.(?!\\d)|(?<!\\d):(?!\\d))(?=[^.،؛؟!?,;\\n])");
                        List<String> sentences = new ArrayList<>(rawSentences.length);
                        for (String s : rawSentences) {
                            if (s != null && !s.trim().isEmpty() && !isPunctuationOnly(s)) {
                                sentences.add(s);
                            }
                        }

                        if (!sentences.isEmpty()) {
                            // Process first sentence immediately for zero start-up latency
                            String currentTextToSpeak;
                            try {
                                currentTextToSpeak = processArabicText(sentences.get(0), false);
                            } catch (Exception e) {
                                currentTextToSpeak = sentences.get(0);
                            }
                            if (currentTextToSpeak != null) currentTextToSpeak = currentTextToSpeak.replace("ـ", "");

                            // Launch lookahead prefetch for sentence 1 if available
                            Future<String> nextSentenceFuture = null;
                            if (sentences.size() > 1) {
                                final String s1 = sentences.get(1);
                                nextSentenceFuture = prefetchExecutor.submit(() -> {
                                    try {
                                        String p = processArabicText(s1, false);
                                        return (p != null) ? p.replace("ـ", "") : null;
                                    } catch (Exception e) {
                                        return s1.replace("ـ", "");
                                    }
                                });
                                currentPrefetchFuture = nextSentenceFuture;
                            }

                            for (int i = 0; i < sentences.size(); i++) {
                                if (!speaking) {
                                    if (nextSentenceFuture != null) nextSentenceFuture.cancel(true);
                                    currentPrefetchFuture = null;
                                    break;
                                }

                                String textToSpeak;
                                if (i == 0) {
                                    textToSpeak = currentTextToSpeak;
                                } else {
                                    try {
                                        textToSpeak = (nextSentenceFuture != null) ? nextSentenceFuture.get() : processArabicText(sentences.get(i), false);
                                    } catch (Exception e) {
                                        textToSpeak = sentences.get(i);
                                    }
                                    if (textToSpeak != null) textToSpeak = textToSpeak.replace("ـ", "");

                                    // Launch prefetch for the next sentence ahead (bounded lookahead = 1)
                                    if (i + 1 < sentences.size()) {
                                        final String sNext = sentences.get(i + 1);
                                        nextSentenceFuture = prefetchExecutor.submit(() -> {
                                            try {
                                                String p = processArabicText(sNext, false);
                                                return (p != null) ? p.replace("ـ", "") : null;
                                            } catch (Exception e) {
                                                return sNext.replace("ـ", "");
                                            }
                                        });
                                        currentPrefetchFuture = nextSentenceFuture;
                                    } else {
                                        nextSentenceFuture = null;
                                        currentPrefetchFuture = null;
                                    }
                                }

                                if (textToSpeak != null && !textToSpeak.trim().isEmpty()) {
                                    tts.engine.speak(textToSpeak, params, player);
                                }
                            }
                        }
                    }
                }
            }
            callback.done();
        } catch (Throwable t) {
            Log.e(TAG, "Synthesis error", t);
            CrashReporter.captureError(this, "RHVoiceService.onSynthesizeText", t);
            callback.error();
        } finally {
            speaking = false;
            ttsManager.release(tts);
            scheduleIdleRelease(IDLE_RELEASE_DELAY_MS);
        }
    }

    @Override
    public String onGetDefaultVoiceNameFor(String language, String country, String variant) {
        if (language != null) {
            String lang = language.trim().toLowerCase(Locale.ROOT);
            if (lang.equals("ara") || lang.equals("ar") || lang.equals("arabic")) {
                return "Zayd";
            }
        }
        return null;
    }

    @Override
    public List<Voice> onGetVoices() {
        List<Voice> result = new ArrayList<>();
        Locale locale = new Locale("ar", "EG");
        Set<String> features = new HashSet<>();
        Voice v = new Voice("Zayd", locale, Voice.QUALITY_HIGH, Voice.LATENCY_NORMAL, false, features);
        result.add(v);
        return result;
    }

    @Override
    public int onIsValidVoiceName(String name) {
        if (name != null && name.equalsIgnoreCase("Zayd")) {
            return TextToSpeech.SUCCESS;
        }
        return TextToSpeech.ERROR;
    }

    @Override
    public int onLoadVoice(String name) {
        return onIsValidVoiceName(name);
    }

    private double extractRequestRate(SynthesisRequest request, int reqRateInt) {
        if (request == null) return 1.0;

        // 1. Explicit speechRate from SynthesisRequest (standard Android method)
        if (reqRateInt > 0 && reqRateInt != 100) {
            return normalizeRateValue(reqRateInt);
        }

        // 2. Fallback to Bundle keys if present and not a dummy 1.0/0
        Bundle bundle = request.getParams();
        if (bundle != null) {
            for (String key : new String[]{"rate", "speechRate", "speech_rate", "speed"}) {
                if (bundle.containsKey(key)) {
                    Object obj = bundle.get(key);
                    if (obj instanceof Number) {
                        double v = ((Number) obj).doubleValue();
                        if (v > 0 && Math.abs(v - 1.0) > 0.001) {
                            return normalizeRateValue(v);
                        }
                    } else if (obj instanceof String) {
                        try {
                            double v = Double.parseDouble((String) obj);
                            if (v > 0 && Math.abs(v - 1.0) > 0.001) {
                                return normalizeRateValue(v);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        if (reqRateInt > 0) {
            return normalizeRateValue(reqRateInt);
        }
        return 1.0;
    }

    private double extractRequestPitch(SynthesisRequest request, int reqPitchInt) {
        if (request == null) return 1.0;

        if (reqPitchInt > 0 && reqPitchInt != 100) {
            return normalizeParamValue(reqPitchInt);
        }

        Bundle bundle = request.getParams();
        if (bundle != null) {
            for (String key : new String[]{"pitch", "speechPitch", "speech_pitch"}) {
                if (bundle.containsKey(key)) {
                    Object obj = bundle.get(key);
                    if (obj instanceof Number) {
                        double v = ((Number) obj).doubleValue();
                        if (v > 0 && Math.abs(v - 1.0) > 0.001) {
                            return normalizeParamValue(v);
                        }
                    } else if (obj instanceof String) {
                        try {
                            double v = Double.parseDouble((String) obj);
                            if (v > 0 && Math.abs(v - 1.0) > 0.001) {
                                return normalizeParamValue(v);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        if (reqPitchInt > 0) {
            return normalizeParamValue(reqPitchInt);
        }
        return 1.0;
    }

    private double extractRequestVolume(SynthesisRequest request) {
        if (request == null) return 1.0;
        Bundle bundle = request.getParams();
        if (bundle != null) {
            for (String key : new String[]{TextToSpeech.Engine.KEY_PARAM_VOLUME, "volume", "speech_volume", "speechVolume"}) {
                if (bundle.containsKey(key)) {
                    Object obj = bundle.get(key);
                    if (obj instanceof Number) {
                        double v = ((Number) obj).doubleValue();
                        if (v >= 0) {
                            return normalizeParamValue(v);
                        }
                    } else if (obj instanceof String) {
                        try {
                            double v = Double.parseDouble((String) obj);
                            if (v >= 0) {
                                return normalizeParamValue(v);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        return 1.0;
    }

    private double normalizeRateValue(double v) {
        if (v <= 0) {
            return 1.0;
        }
        if (v >= 10.0) {
            return v / 100.0;
        }
        return v;
    }

    private double normalizeParamValue(double v) {
        if (v < 0) {
            return 1.0;
        }
        if (v >= 10.0) {
            return v / 100.0;
        }
        return v;
    }
}
