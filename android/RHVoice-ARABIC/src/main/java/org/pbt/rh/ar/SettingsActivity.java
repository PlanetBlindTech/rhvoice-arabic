package org.pbt.rh.ar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsActivity extends AppCompatActivity {

    private LabeledSeekBar seekSpeed;
    private LabeledSeekBar seekPitch;
    private LabeledSeekBar seekVolume;
    private MaterialSwitch switchReadEmojis;
    private MaterialSwitch switchPauseSukoon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        seekSpeed = findViewById(R.id.seek_speed);
        seekPitch = findViewById(R.id.seek_pitch);
        seekVolume = findViewById(R.id.seek_volume);
        switchPauseSukoon = findViewById(R.id.switch_pause_sukoon);
        switchReadEmojis = findViewById(R.id.switch_read_emojis);
        MaterialButton btnEnableEngine = findViewById(R.id.btn_enable_engine);

        if (btnEnableEngine != null) {
            btnEnableEngine.setOnClickListener(v -> openTtsSettings());
        }

        loadSettings();
        setupListeners();
    }

    private void openTtsSettings() {
        try {
            Intent intent = new Intent("com.android.settings.TTS_SETTINGS");
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Exception ex) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {
                    Toast.makeText(this, "تعذر فتح إعدادات النظام", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int savedRate = 100;
        try {
            savedRate = Integer.parseInt(prefs.getString("language.ara.rate", "100"));
        } catch (Exception e) {
            try { savedRate = prefs.getInt("language.ara.rate", 100); } catch (Exception ignored) {}
        }

        int savedPitch = 100;
        try {
            savedPitch = Integer.parseInt(prefs.getString("language.ara.pitch", "100"));
        } catch (Exception e) {
            try { savedPitch = prefs.getInt("language.ara.pitch", 100); } catch (Exception ignored) {}
        }

        int savedVolume = 100;
        try {
            savedVolume = Integer.parseInt(prefs.getString("language.ara.volume", "100"));
        } catch (Exception e) {
            try { savedVolume = prefs.getInt("language.ara.volume", 100); } catch (Exception ignored) {}
        }

        if (savedPitch < 50) savedPitch = 100;

        int speedProgress = Math.max(0, Math.min(350, savedRate - 50));
        int pitchProgress = Math.max(0, Math.min(150, savedPitch - 50));
        int volumeProgress = Math.max(0, Math.min(250, savedVolume));

        if (seekSpeed != null) {
            seekSpeed.setProgress(speedProgress);
        }

        if (seekPitch != null) {
            seekPitch.setProgress(pitchProgress);
        }

        if (seekVolume != null) {
            seekVolume.setProgress(volumeProgress);
        }

        if (switchPauseSukoon != null) {
            switchPauseSukoon.setChecked(prefs.getBoolean("pause_sukoon", true));
        }

        if (switchReadEmojis != null) {
            switchReadEmojis.setChecked(prefs.getBoolean(DictionaryManager.PREF_READ_EMOJIS, true));
        }
    }

    private void setupListeners() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) applySettingsLive();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                notifyConfigChanged();
            }
        };

        if (seekSpeed != null) seekSpeed.setOnSeekBarChangeListener(listener);
        if (seekPitch != null) seekPitch.setOnSeekBarChangeListener(listener);
        if (seekVolume != null) seekVolume.setOnSeekBarChangeListener(listener);

        if (switchPauseSukoon != null) {
            switchPauseSukoon.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                prefs.edit().putBoolean("pause_sukoon", isChecked).apply();
                notifyConfigChanged();
            });
        }

        if (switchReadEmojis != null) {
            switchReadEmojis.setOnCheckedChangeListener((buttonView, isChecked) -> {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(SettingsActivity.this);
                prefs.edit().putBoolean(DictionaryManager.PREF_READ_EMOJIS, isChecked).apply();
                notifyConfigChanged();
            });
        }
    }

    private void applySettingsLive() {
        int rateVal = seekSpeed != null ? seekSpeed.getProgress() + 50 : 100;
        int pitchVal = seekPitch != null ? seekPitch.getProgress() + 50 : 100;
        int volumeVal = seekVolume != null ? seekVolume.getProgress() : 100;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit()
            .putString("language.ara.rate", String.valueOf(rateVal))
            .putString("language.ara.pitch", String.valueOf(pitchVal))
            .putString("language.ara.volume", String.valueOf(volumeVal))
            .putInt("language.ara.rate_int", rateVal)
            .putInt("language.ara.pitch_int", pitchVal)
            .putInt("language.ara.volume_int", volumeVal)
            .apply();

        notifyConfigChanged();
    }

    private void notifyConfigChanged() {
        try {
            LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent(RHVoiceService.ACTION_CONFIG_CHANGE));
            sendBroadcast(new Intent(RHVoiceService.ACTION_CONFIG_CHANGE));
        } catch (Exception ignored) {}
    }

    public static void show(Context context) {
        Intent intent = new Intent(context, SettingsActivity.class);
        context.startActivity(intent);
    }
}
