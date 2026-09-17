# RHVoice Arabic — Zayd Voice

Arabic text-to-speech support for the RHVoice engine, including an Android application and an NVDA add-on for Windows.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Engine](https://img.shields.io/badge/Engine-RHVoice-informational)](https://github.com/RHVoice/RHVoice)
[![Diacritizer](https://img.shields.io/badge/Diacritizer-Rawi%20ONNX-purple)](https://github.com/TigreGotico/text2tashkeel)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Windows%20NVDA-orange)](#installation)

---

## Overview

This project extends [RHVoice](https://github.com/RHVoice/RHVoice), a statistical speech synthesis engine based on HTS, with support for the Arabic language. It integrates automatic neural diacritization via the **Rawi ONNX** model and a comprehensive Arabic text normalization pipeline.

---

## Repository Structure

```
RHVOICE-ARABIC/
│
├── android/                              # Android Gradle project
│   ├── RHVoice-ARABIC/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── assets/data/
│   │   │   │   ├── languages/Arabic/     # phonemes.xml, g2p.fst, language.conf
│   │   │   │   └── voices/Arabic/Zayd/  # HTS acoustic models (MGC, F0, BAP)
│   │   │   ├── java/org/pbt/rh/ar/      # RHVoiceService, ArabicDiacritizer
│   │   │   ├── jni/                     # JNI bindings to the RHVoice C++ core
│   │   │   └── res/                     # UI resources, strings (ar + en)
│   │   ├── build.gradle
│   │   └── proguard-rules.pro
│   ├── settings.gradle
│   └── gradlew / gradlew.bat
│
├── nvda-addon/                           # NVDA add-on for Windows
│   ├── manifest.ini
│   ├── synthDrivers/rhvoice-arabic/
│   │   ├── __init__.py                   # Main synthesizer driver
│   │   ├── ar_textnorm.py               # Text normalization (numbers, dates, currencies)
│   │   ├── text_processing.py           # Integrated text processing pipeline
│   │   ├── _libboot.py                  # Binary library loader
│   │   ├── model/
│   │   │   ├── rawi_ensemble.onnx       # Rawi diacritization model
│   │   │   └── vocab.json
│   │   ├── lib/                         # RHVoice and ONNX Runtime binary libraries (DLL)
│   │   ├── lib_pure/                    # Pure Python dependencies (pyarabic, num2words, hijridate)
│   │   └── libs/                        # Full ONNX Runtime library set
│   ├── data/voices/Zayd/                # HTS acoustic models
│   └── langdata/languages/Arabic/       # phonemes.xml, g2p.fst
│
├── releases/
│   ├── app/
│   │   └── rhvoice-Arabic_1.0.0-release.apk
│   └── nvda-addon/
│       └── rhvoice-arabic-1.0.0.nvda-addon
│
├── .gitignore
└── README.md
```

---

## Architecture

The synthesis pipeline is based on the full HTS toolchain:

| Component | Tool | Role |
|---|---|---|
| Speech synthesis engine | [RHVoice](https://github.com/RHVoice/RHVoice) | HTS-based statistical speech synthesis |
| Forced alignment | HTK (Hidden Markov Toolkit) | Phoneme-level speech alignment |
| Feature extraction | SPTK (Speech Processing Toolkit) | Acoustic feature extraction (MGC, F0, BAP) |
| Diacritization | [Rawi ONNX](https://github.com/TigreGotico/text2tashkeel) | Lightweight neural Arabic diacritization |
| Grapheme-to-phoneme (G2P) | Foma FST | Arabic to Buckwalter phonetic transcription |

---

## Training Data

| Property | Value |
|---|---|
| Dataset size | 10,026 utterances with matching reference transcripts |
| Data sources | Internet-sourced recordings and synthetically generated audio (open-source TTS models) |
| Training hardware | 8-core CPU, 16 GB RAM |

---

## Text Processing Components

### Diacritization
- The **Rawi ONNX** model automatically diacritizes undiacritized input text.
- Existing user-provided diacritics are preserved; automatic diacritization is applied only to unmarked segments.

### Text Normalization
- Integer, fractional, and ordinal numbers
- Hijri and Gregorian dates
- Time expressions
- Currencies and monetary amounts
- Percentages and common symbols

### Grapheme-to-Phoneme (G2P)
- Implemented in **Foma** as a finite-state transducer (FST).
- Converts Arabic script to the **Buckwalter** phonetic alphabet used internally by RHVoice.
- Design inspired by the [Mantoq](https://github.com/mush42/mantoq) project by [@mush42](https://github.com/mush42).

---

## Android Application

- **Application ID**: `org.pbt.rh.ar`
- **Minimum Android version**: 5.0 (API 21)
- **TTS service**: `RHVoiceService`, conforming to the Android `TextToSpeechService` standard.
- **Inference**: `ArabicDiacritizer.java` runs `rawi_ensemble.onnx` on-device via `onnxruntime-android`, with no network dependency.
- **Supported ABIs**: `arm64-v8a`, `armeabi-v7a`

---

## NVDA Add-on

- **Compatibility**: NVDA 2021.1 and later on Windows.
- **Languages**: Arabic (primary), with automatic language switching for English.
- **Bundled dependencies**: `pyarabic`, `num2words`, `hijridate`, `onnxruntime`.

---

## Installation

| Package | Version | Size |
|---|---|---|
| Android APK | 1.0.0 | ~53 MB |
| NVDA Add-on | 1.0.0 | ~70 MB |

**Android:**
1. Enable installation from unknown sources in device settings.
2. Install the APK file.
3. Navigate to: Settings > Accessibility > Text-to-Speech > Select "RHVoice Arabic".

**NVDA:**
1. Open the `.nvda-addon` file or install via NVDA > Tools > Add-on Store.
2. Restart NVDA.
3. Navigate to: Preferences > Settings > Speech > Select "RHVoice Arabic".

---

## Building from Source

**Requirements:** Android SDK, NDK r25.1+, JDK 11+

```bash
# Android APK
cd android
./gradlew assembleStableRelease
# Output: releases/app/rhvoice-Arabic_1.0.0-release.apk

# NVDA Add-on
cd nvda-addon
zip -r ../releases/nvda-addon/rhvoice-arabic-1.0.0.nvda-addon .
```

---

## Platform Support

| Platform | Status |
|---|---|
| Android | Supported |
| Windows (NVDA) | Supported |
| Linux (Orca) | Not yet supported — the diacritization layer is not integrated |

Contributions toward Linux support are welcome. Please open an [issue](https://github.com/shamsorachdi62/RHVOICE-ARABIC/issues) or submit a [pull request](https://github.com/shamsorachdi62/RHVOICE-ARABIC/pulls).

---

## Acknowledgements

| Contributor | Contribution |
|---|---|
| [Olga Yakovleva](https://github.com/RHVoice/RHVoice) | Original RHVoice engine |
| [HTK Development Team](https://htk.eng.cam.ac.uk/) | Forced alignment toolkit |
| [SPTK Development Team](https://sourceforge.net/projects/sp-tk/) | Speech feature extraction toolkit |
| [@mush42](https://github.com/mush42) (Musharraf Omar) | [Mantoq](https://github.com/mush42/mantoq) — G2P design reference |
| Ilias / GFF Team | Text dataset for voice training |
| Riad Assoum | [ClaritySynth](https://tecwindow.net) — text preprocessing code |
| [Casimiro Ferreira / TigreGotico](https://github.com/TigreGotico) | [Rawi](https://github.com/TigreGotico/text2tashkeel) — ONNX diacritization model |

---

## License

This project is licensed under the **GNU General Public License v3.0**.  
See the [LICENSE](nvda-addon/synthDrivers/rhvoice-arabic/gpl-3.0.txt) file for the full terms.
