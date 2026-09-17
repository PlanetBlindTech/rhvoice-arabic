/* Copyright (C) 2013  RHVOICE ARABIC <yakovleva.o.v@gmail.com> */

/* This program is free software: you can redistribute it and/or modify */
/* it under the terms of the GNU Lesser General Public License as published by */
/* the Free Software Foundation, either version 2.1 of the License, or */
/* (at your option) any later version. */

/* This program is distributed in the hope that it will be useful, */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the */
/* GNU Lesser General Public License for more details. */

/* You should have received a copy of the GNU Lesser General Public License */
/* along with this program.  If not, see <https://www.gnu.org/licenses/>. */

package org.pbt.rh.ar;

import java.util.Locale;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;

public final class SampleTextActivity extends Activity {
    private final String TAG = "RHVoiceSampleTextActivity";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        final Intent request = getIntent();
        String language = request != null ? request.getStringExtra("language") : null;
        if (language == null) {
            Locale locale = Locale.getDefault();
            language = locale != null ? locale.getISO3Language() : "ara";
        }

        Resources resources = getResources();
        String[] languages = resources.getStringArray(R.array.sample_languages);
        String[] messages = resources.getStringArray(R.array.sample_messages);
        String message = messages[0]; // Arabic sample text

        if (language != null) {
            String lower = language.toLowerCase(Locale.ROOT);
            for (int i = 0; i < languages.length; ++i) {
                if (languages[i].equalsIgnoreCase(lower) || (lower.startsWith("ar") && languages[i].startsWith("ar"))) {
                    message = messages[i];
                    break;
                }
            }
        }

        final Intent response = new Intent();
        response.putExtra("sampleText", message);
        setResult(RESULT_OK, response);
        finish();
    }
}
