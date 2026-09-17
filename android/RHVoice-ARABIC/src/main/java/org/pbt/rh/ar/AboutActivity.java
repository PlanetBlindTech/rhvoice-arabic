package org.pbt.rh.ar;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        MaterialToolbar toolbar = findViewById(R.id.about_toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        MaterialButton btnYoutube = findViewById(R.id.btn_youtube);
        if (btnYoutube != null) {
            btnYoutube.setOnClickListener(v -> openUrl("https://youtube.com/@planetblindtech?si=PLR4cp13TihMeBNH"));
        }

        MaterialButton btnTelegram = findViewById(R.id.btn_telegram);
        if (btnTelegram != null) {
            btnTelegram.setOnClickListener(v -> openUrl("https://t.me/mohammad_loay222"));
        }

        MaterialButton btnEmail = findViewById(R.id.btn_email);
        if (btnEmail != null) {
            btnEmail.setOnClickListener(v -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_SENDTO);
                    intent.setData(Uri.parse("mailto:planetblindtec@gmail.com"));
                    startActivity(intent);
                } catch (Exception ignored) {}
            });
        }
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ignored) {}
    }
}
