/* Copyright (C) 2013, 2014, 2016, 2019  RHVOICE ARABIC <yakovleva.o.v@gmail.com> */

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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.ArrayList;

public final class CheckTTSData extends Activity {
    private static final String TAG = "RHVoiceCheckDataActivity";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (BuildConfig.DEBUG)
            Log.v(TAG, "checking data");

        ArrayList<String> installedLanguages = new ArrayList<>();
        ArrayList<String> notInstalledLanguages = new ArrayList<>();

        // Standard Arabic language tags supported by the bundled Zayd voice
        installedLanguages.add("ara");
        installedLanguages.add("ar");
        installedLanguages.add("ara-EGY");
        installedLanguages.add("ar-EG");
        installedLanguages.add("ara-SAU");
        installedLanguages.add("ar-SA");

        Intent resultIntent = new Intent();
        resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, installedLanguages);
        resultIntent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, notInstalledLanguages);
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, resultIntent);
        finish();
    }
}

