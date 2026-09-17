package org.pbt.rh.ar;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String UTTERANCE_ID = "rhvoice_utterance";

    private TextInputEditText textInput;
    private MaterialButton btnPlay;

    private TextToSpeech tts;
    private boolean ttsInitialized = false;
    private boolean isSpeakingState = false;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private String lastInputText = null;
    private String cachedProcessedText = null;
    private String lastTargetUtteranceId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }

        textInput = findViewById(R.id.text_input);
        btnPlay = findViewById(R.id.btn_play);

        DictionaryManager.initEmojiAsync(this);

        try {
            Intent serviceIntent = new Intent(this, RHVoiceService.class);
            startService(serviceIntent);
        } catch (Throwable ignored) {}

        initTTS();

        if (btnPlay != null) {
            btnPlay.setOnClickListener(v -> {
                if (isSpeakingState) {
                    stopText();
                } else {
                    speakText();
                }
            });
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_dictionary) {
            startActivity(new Intent(this, DictionaryActivity.class));
            return true;
        } else if (id == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("ara"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(new Locale("ar"));
                }
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    result = tts.setLanguage(new Locale("ar", "EG"));
                }
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    ttsInitialized = true;
                } else {
                    Log.e(TAG, "Language not supported by TTS engine");
                    CrashReporter.copyToClipboardDirect(MainActivity.this, "ArabicTTS_InitError", "MainActivity initTTS: setLanguage(ara/ar/ar-EG) failed with code: " + result, true);
                    Toast.makeText(MainActivity.this, "اللغة العربية غير مدعومة في المحرك", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.e(TAG, "TTS Init failed with status: " + status);
                CrashReporter.copyToClipboardDirect(MainActivity.this, "ArabicTTS_InitError", "MainActivity initTTS: Init failed with status: " + status, true);
                Toast.makeText(MainActivity.this, "فشل تهيئة محرك النطق", Toast.LENGTH_SHORT).show();
            }
        }, getPackageName());

        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> setPlayingState(true));
            }

            @Override
            public void onDone(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(lastTargetUtteranceId)) {
                    runOnUiThread(() -> setPlayingState(false));
                }
            }

            @Override
            public void onError(String utteranceId) {
                if (utteranceId != null && utteranceId.equals(lastTargetUtteranceId)) {
                    runOnUiThread(() -> setPlayingState(false));
                }
            }
        });
    }

    private void setPlayingState(boolean playing) {
        isSpeakingState = playing;
        if (btnPlay == null) return;
        if (playing) {
            btnPlay.setText(R.string.stop);
            btnPlay.setIconResource(R.drawable.ic_stop);
            btnPlay.setBackgroundColor(getResources().getColor(R.color.stop_button_bg));
            btnPlay.setTextColor(getResources().getColor(R.color.stop_button_on));
            btnPlay.setIconTintResource(R.color.stop_button_on);
        } else {
            btnPlay.setText(R.string.listen);
            btnPlay.setIconResource(R.drawable.ic_play);
            btnPlay.setBackgroundColor(getResources().getColor(R.color.primary));
            btnPlay.setTextColor(getResources().getColor(R.color.on_primary));
            btnPlay.setIconTintResource(R.color.on_primary);
        }
    }

    private List<String> chunkTextForTTS(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return chunks;
        }

        String[] sentences = text.split("(?<=[.،؛؟!?\\n])\\s*");
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            if (sentence.trim().isEmpty()) continue;

            if (sentence.length() > 500) {
                String[] words = sentence.split("\\s+");
                for (String word : words) {
                    if (currentChunk.length() + word.length() + 1 > 500) {
                        if (currentChunk.length() > 0) {
                            chunks.add(currentChunk.toString().trim());
                            currentChunk.setLength(0);
                        }
                    }
                    if (currentChunk.length() > 0) currentChunk.append(" ");
                    currentChunk.append(word);
                }
            } else {
                if (currentChunk.length() + sentence.length() + 1 > 500) {
                    if (currentChunk.length() > 0) {
                        chunks.add(currentChunk.toString().trim());
                        currentChunk.setLength(0);
                    }
                }
                if (currentChunk.length() > 0) currentChunk.append(" ");
                currentChunk.append(sentence);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        if (chunks.isEmpty() && !text.trim().isEmpty()) {
            chunks.add(text.trim());
        }

        return chunks;
    }

    private void speakTextChunks(String textToSpeak) {
        if (tts == null) return;
        tts.setSpeechRate(1.0f);
        tts.setPitch(1.0f);

        List<String> chunks = chunkTextForTTS(textToSpeak);
        String finalUttId = UTTERANCE_ID + "_" + (chunks.size() - 1);
        lastTargetUtteranceId = finalUttId;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            String uId = UTTERANCE_ID + "_" + i;
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, uId);

            int queueMode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
            int result = tts.speak(chunk, queueMode, params);
            if (result != TextToSpeech.SUCCESS) {
                CrashReporter.copyToClipboardDirect(MainActivity.this, "ArabicTTS_SpeakError", "MainActivity tts.speak returned code: " + result + " for chunk " + i, true);
                Toast.makeText(MainActivity.this, "فشل بدء النطق", Toast.LENGTH_SHORT).show();
                setPlayingState(false);
                return;
            }
        }
    }

    private void speakText() {
        if (!ttsInitialized) {
            Toast.makeText(this, "جاري التهيئة...", Toast.LENGTH_SHORT).show();
            initTTS();
            return;
        }

        final String text = textInput.getText() != null ? textInput.getText().toString().trim() : "";
        if (TextUtils.isEmpty(text)) {
            textInput.setError("أدخل نصاً");
            return;
        }

        setPlayingState(true);

        if (text.equals(lastInputText) && cachedProcessedText != null) {
            speakTextChunks(cachedProcessedText);
            return;
        }

        executorService.execute(() -> {
            final String textToSpeak = text;
            cachedProcessedText = textToSpeak;
            lastInputText = text;

            runOnUiThread(() -> {
                if (!ttsInitialized) {
                    setPlayingState(false);
                    return;
                }
                speakTextChunks(textToSpeak);
            });
        });
    }

    private void stopText() {
        if (ttsInitialized && tts != null) {
            tts.stop();
        }
        setPlayingState(false);
    }

    @Override
    protected void onDestroy() {
        executorService.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
