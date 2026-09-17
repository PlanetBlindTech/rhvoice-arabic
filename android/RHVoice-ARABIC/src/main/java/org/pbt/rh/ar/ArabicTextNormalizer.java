package org.pbt.rh.ar;

import java.math.BigInteger;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArabicTextNormalizer {

    // Dictionaries loaded dynamically from assets/Dictionaries/Default-dic.json
    private static final Map<String, String> CHAR_NAMES = DictionaryManager.getCharNames();

    private static final String[] UNITS = {
        "", "وَاحِد", "اثْنَان", "ثَلاَثَة", "أَرْبَعَة", "خَمْسَة", "سِتَّة", "سَبْعَة", "ثَمَانِيَة", "تِسْعَة", "عَشَرَة",
        "أَحَدَ عَشَرَ", "اثْنَا عَشَرَ", "ثَلاَثَةَ عَشَرَ", "أَرْبَعَةَ عَشَرَ", "خَمْسَةَ عَشَرَ", "سِتَّةَ عَشَرَ", "سَبْعَةَ عَشَرَ", "ثَمَانِيَةَ عَشَرَ", "تِسْعَةَ عَشَرَ"
    };

    private static final String[] TENS = {
        "", "", "عِشْرُونَ", "ثَلاَثُونَ", "أَرْبَعُونَ", "خَمْسُونَ", "سِتُّونَ", "سَبْعُونَ", "ثَمَانُونَ", "تِسْعُونَ"
    };

    private static final String[] HUNDREDS = {
        "", "مِئَة", "مِئَتَان", "ثَلاَثُمِئَة", "أَرْبَعُمِئَة", "خَمْسُمِئَة", "سِتُّمِئَة", "سَبْعُمِئَة", "ثَمَانُمِئَة", "تِسْعُمِئَة"
    };

    private static final String[] MONTHS = {
        "", "يَنَايِر", "فَبْرَايِر", "مَارِس", "أَبْرِيل", "مَايُو", "يُونِيُو",
        "يُولِيُو", "أَغُسْطُس", "سِبْتَمْبَر", "أُكْتُوبَر", "نُوفَمْبَر", "دِيسَمْبَر"
    };

    private static final String[] ORDINAL_UNITS_F = {
        "", "الأُولَى", "الثَّانِيَة", "الثَّالِثَة", "الرَّابِعَة", "الخَامِسَة",
        "السَّادِسَة", "السَّابِعَة", "الثَّامِنَة", "التَّاسِعَة", "العَاشِرَة"
    };

    private static final String[] ORDINAL_TENS = {
        "", "", "العِشْرُونَ", "الثَّلاَثُونَ"
    };

    private static final Map<String, String> ABBREVIATIONS = DictionaryManager.getAbbreviations();
    private static final Map<String, String> CURRENCIES = DictionaryManager.getCurrencies();
    private static final Map<String, String> SYMBOLS = DictionaryManager.getSymbols();
    private static final Map<String, String> DIACRITIC_NAMES = DictionaryManager.getDiacriticNames();

    private static final char SHADDA = '\u0651';
    private static final Pattern DIACRITIC_ONLY_RE =
        Pattern.compile("^[\\u064B-\\u065F\\u0670\\u0640\\u06D6-\\u06ED]+$");

    public static boolean isPreDiacritizedSpelling(String text) {
        if (text == null) return false;
        return CHAR_NAMES.containsValue(text) || DIACRITIC_NAMES.containsValue(text) || SYMBOLS.containsValue(text);
    }

    // Name a run made up solely of diacritics (no base letter): a single mark
    // (ّ -> شَدَّةٌ), a mark riding a tatweel (ـً -> تَنْوِينُ فَتْحٍ), or the frequent
    // shadda+vowel pair (ـَّ -> شَدَّةٌ مَفْتُوحَةٌ). Returns null if any mark is unknown.
    private static String nameLoneDiacritics(String marks) {
        String core = marks.replace("\u0640", "");   // drop tatweel carrier
        if (core.isEmpty()) {
            if (DIACRITIC_NAMES.containsKey("\u0640")) return DIACRITIC_NAMES.get("\u0640");
            if (CHAR_NAMES.containsKey("ـ")) return CHAR_NAMES.get("ـ");
            return "كَشِيدَةٌ";
        }
        if (core.length() == 1) {
            String c = String.valueOf(core.charAt(0));
            if (DIACRITIC_NAMES.containsKey(c)) return DIACRITIC_NAMES.get(c);
            if (CHAR_NAMES.containsKey(c)) return CHAR_NAMES.get(c);
        }
        if (core.length() == 2) {
            char c1 = core.charAt(0);
            char c2 = core.charAt(1);
            if (c1 == SHADDA || c2 == SHADDA) {
                char vowel = (c1 == SHADDA) ? c2 : c1;
                switch (vowel) {
                    case '\u064E': return "شَدَّةٌ مَفْتُوحَةٌ";
                    case '\u064F': return "شَدَّةٌ مَضْمُومَةٌ";
                    case '\u0650': return "شَدَّةٌ مَكْسُورَةٌ";
                    case '\u064B': return "شَدَّةٌ مَعَ تَنْوِينِ فَتْحٍ";
                    case '\u064C': return "شَدَّةٌ مَعَ تَنْوِينِ ضَمٍّ";
                    case '\u064D': return "شَدَّةٌ مَعَ تَنْوِينِ كَسْرٍ";
                    default: break;
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < core.length(); i++) {
            String c = String.valueOf(core.charAt(i));
            String nm = DIACRITIC_NAMES.get(c);
            if (nm == null) nm = CHAR_NAMES.get(c);
            if (nm == null) return null;
            if (sb.length() > 0) sb.append(" ");
            sb.append(nm);
        }
        return sb.toString();
    }

    public static String getSingleCharName(String text) {
        if (text == null) return null;

        // 1. Whitespace and Keyboard Control Keys (Space, Enter, Tab)
        if (text.equals(" ") || text.equals("\u00A0") || (text.length() > 0 && text.trim().isEmpty())) {
            if (text.contains("\n")) return "سَطْرٌ جَدِيدٌ";
            if (text.contains("\t")) return "مَسَافَةٌ بَادِئَةٌ";
            return "مَسَافَةٌ";
        }

        String t = foldDigits(text).trim();
        if (t.isEmpty()) return null;

        // 2. Direct lookup in dictionary tables
        if (CHAR_NAMES.containsKey(t)) {
            return CHAR_NAMES.get(t);
        }
        if (SYMBOLS.containsKey(t)) {
            return SYMBOLS.get(t);
        }
        if (DIACRITIC_NAMES.containsKey(t)) {
            return DIACRITIC_NAMES.get(t);
        }

        // 3. Multi-character punctuation sequences (Keyboard exploration)
        if (t.equals("...") || t.equals("..")) {
            return "عَلَامَةُ حَذْفٍ";
        }
        if (t.equals("--") || t.equals("——")) {
            return "شَرْطَةٌ طَوِيلَةٌ";
        }

        // 4. A run consisting only of diacritics/tatweel (no base letter)
        if (DIACRITIC_ONLY_RE.matcher(t).matches()) {
            String lone = nameLoneDiacritics(t);
            if (lone != null) return lone;
        }

        // 5. Look up in emoji and symbol dictionary (including all 4,053 symbols, arrows, currencies, etc.)
        String fromEmojiDic = DictionaryManager.getEmojiOrSymbolName(t);
        if (fromEmojiDic != null && !fromEmojiDic.isEmpty()) {
            return fromEmojiDic;
        }

        // 6. Base letter lookup when stripped of diacritics
        String bareTrimmed = t.replaceAll("[\\u064B-\\u0652\\u0670]", "");
        if (!bareTrimmed.isEmpty() && CHAR_NAMES.containsKey(bareTrimmed)) {
            return CHAR_NAMES.get(bareTrimmed);
        }
        return null;
    }

    private static final Map<Character, String> PF_MAP = new HashMap<>();
    static {
        // Presentation Forms B (FE70-FEFF)
        PF_MAP.put('\uFE70', "ً"); PF_MAP.put('\uFE71', "ً");
        PF_MAP.put('\uFE72', "ٌ"); PF_MAP.put('\uFE73', "ٌ");
        PF_MAP.put('\uFE74', "ٍ"); PF_MAP.put('\uFE75', "ٍ");
        PF_MAP.put('\uFE76', "َ"); PF_MAP.put('\uFE77', "َ");
        PF_MAP.put('\uFE78', "ُ"); PF_MAP.put('\uFE79', "ُ");
        PF_MAP.put('\uFE7A', "ِ"); PF_MAP.put('\uFE7B', "ِ");
        PF_MAP.put('\uFE7C', "ّ"); PF_MAP.put('\uFE7D', "ّ");
        PF_MAP.put('\uFE7E', "ْ"); PF_MAP.put('\uFE7F', "ْ");
        PF_MAP.put('\uFE80', "ء");
        PF_MAP.put('\uFE81', "آ"); PF_MAP.put('\uFE82', "آ");
        PF_MAP.put('\uFE83', "أ"); PF_MAP.put('\uFE84', "أ");
        PF_MAP.put('\uFE85', "ؤ"); PF_MAP.put('\uFE86', "ؤ");
        PF_MAP.put('\uFE87', "إ"); PF_MAP.put('\uFE88', "إ");
        PF_MAP.put('\uFE89', "ئ"); PF_MAP.put('\uFE8A', "ئ"); PF_MAP.put('\uFE8B', "ئ"); PF_MAP.put('\uFE8C', "ئ");
        PF_MAP.put('\uFE8D', "ا"); PF_MAP.put('\uFE8E', "ا");
        PF_MAP.put('\uFE8F', "ب"); PF_MAP.put('\uFE90', "ب"); PF_MAP.put('\uFE91', "ب"); PF_MAP.put('\uFE92', "ب");
        PF_MAP.put('\uFE93', "ة"); PF_MAP.put('\uFE94', "ة");
        PF_MAP.put('\uFE95', "ت"); PF_MAP.put('\uFE96', "ت"); PF_MAP.put('\uFE97', "ت"); PF_MAP.put('\uFE98', "ت");
        PF_MAP.put('\uFE99', "ث"); PF_MAP.put('\uFE9A', "ث"); PF_MAP.put('\uFE9B', "ث"); PF_MAP.put('\uFE9C', "ث");
        PF_MAP.put('\uFE9D', "ج"); PF_MAP.put('\uFE9E', "ج"); PF_MAP.put('\uFE9F', "ج"); PF_MAP.put('\uFEA0', "ج");
        PF_MAP.put('\uFEA1', "ح"); PF_MAP.put('\uFEA2', "ح"); PF_MAP.put('\uFEA3', "ح"); PF_MAP.put('\uFEA4', "ح");
        PF_MAP.put('\uFEA5', "خ"); PF_MAP.put('\uFEA6', "خ"); PF_MAP.put('\uFEA7', "خ"); PF_MAP.put('\uFEA8', "خ");
        PF_MAP.put('\uFEA9', "د"); PF_MAP.put('\uFEAA', "د");
        PF_MAP.put('\uFEAB', "ذ"); PF_MAP.put('\uFEAC', "ذ");
        PF_MAP.put('\uFEAD', "ر"); PF_MAP.put('\uFEAE', "ر");
        PF_MAP.put('\uFEAF', "ز"); PF_MAP.put('\uFEB0', "ز");
        PF_MAP.put('\uFEB1', "س"); PF_MAP.put('\uFEB2', "س"); PF_MAP.put('\uFEB3', "س"); PF_MAP.put('\uFEB4', "س");
        PF_MAP.put('\uFEB5', "ش"); PF_MAP.put('\uFEB6', "ش"); PF_MAP.put('\uFEB7', "ش"); PF_MAP.put('\uFEB8', "ش");
        PF_MAP.put('\uFEB9', "ص"); PF_MAP.put('\uFEBA', "ص"); PF_MAP.put('\uFEBB', "ص"); PF_MAP.put('\uFEBC', "ص");
        PF_MAP.put('\uFEBD', "ض"); PF_MAP.put('\uFEBE', "ض"); PF_MAP.put('\uFEBF', "ض"); PF_MAP.put('\uFEC0', "ض");
        PF_MAP.put('\uFEC1', "ط"); PF_MAP.put('\uFEC2', "ط"); PF_MAP.put('\uFEC3', "ط"); PF_MAP.put('\uFEC4', "ط");
        PF_MAP.put('\uFEC5', "ظ"); PF_MAP.put('\uFEC6', "ظ"); PF_MAP.put('\uFEC7', "ظ"); PF_MAP.put('\uFEC8', "ظ");
        PF_MAP.put('\uFEC9', "ع"); PF_MAP.put('\uFECA', "ع"); PF_MAP.put('\uFECB', "ع"); PF_MAP.put('\uFECC', "ع");
        PF_MAP.put('\uFECD', "غ"); PF_MAP.put('\uFECE', "غ"); PF_MAP.put('\uFECF', "غ"); PF_MAP.put('\uFED0', "غ");
        PF_MAP.put('\uFED1', "ف"); PF_MAP.put('\uFED2', "ف"); PF_MAP.put('\uFED3', "ف"); PF_MAP.put('\uFED4', "ف");
        PF_MAP.put('\uFED5', "ق"); PF_MAP.put('\uFED6', "ق"); PF_MAP.put('\uFED7', "ق"); PF_MAP.put('\uFED8', "ق");
        PF_MAP.put('\uFED9', "ك"); PF_MAP.put('\uFEDA', "ك"); PF_MAP.put('\uFEDB', "ك"); PF_MAP.put('\uFEDC', "ك");
        PF_MAP.put('\uFEDD', "ل"); PF_MAP.put('\uFEDE', "ل"); PF_MAP.put('\uFEDF', "ل"); PF_MAP.put('\uFEE0', "ل");
        PF_MAP.put('\uFEE1', "م"); PF_MAP.put('\uFEE2', "م"); PF_MAP.put('\uFEE3', "م"); PF_MAP.put('\uFEE4', "م");
        PF_MAP.put('\uFEE5', "ن"); PF_MAP.put('\uFEE6', "ن"); PF_MAP.put('\uFEE7', "ن"); PF_MAP.put('\uFEE8', "ن");
        PF_MAP.put('\uFEE9', "ه"); PF_MAP.put('\uFEEA', "ه"); PF_MAP.put('\uFEEB', "ه"); PF_MAP.put('\uFEEC', "ه");
        PF_MAP.put('\uFEED', "و"); PF_MAP.put('\uFEEE', "و");
        PF_MAP.put('\uFEEF', "ي"); PF_MAP.put('\uFEF0', "ي"); PF_MAP.put('\uFEF1', "ي"); PF_MAP.put('\uFEF2', "ي");
        PF_MAP.put('\uFEF3', "ي"); PF_MAP.put('\uFEF4', "ي");
        PF_MAP.put('\uFEF5', "لآ"); PF_MAP.put('\uFEF6', "لآ");
        PF_MAP.put('\uFEF7', "لأ"); PF_MAP.put('\uFEF8', "لأ");
        PF_MAP.put('\uFEF9', "لإ"); PF_MAP.put('\uFEFA', "لإ");
        PF_MAP.put('\uFEFB', "لا"); PF_MAP.put('\uFEFC', "لا");

        // Presentation Forms A & Farsi/Urdu
        PF_MAP.put('\uFB50', "ا"); PF_MAP.put('\uFB51', "ا");
        PF_MAP.put('\uFB52', "ب"); PF_MAP.put('\uFB53', "ب"); PF_MAP.put('\uFB54', "ب"); PF_MAP.put('\uFB55', "ب");
        PF_MAP.put('\uFB56', "ب"); PF_MAP.put('\uFB57', "ب"); PF_MAP.put('\uFB58', "ب"); PF_MAP.put('\uFB59', "ب");
        PF_MAP.put('\uFB7A', "ج"); PF_MAP.put('\uFB7B', "ج"); PF_MAP.put('\uFB7C', "ج"); PF_MAP.put('\uFB7D', "ج");
        PF_MAP.put('\uFB8A', "ز"); PF_MAP.put('\uFB8B', "ز");
        PF_MAP.put('\uFB8C', "ر"); PF_MAP.put('\uFB8D', "ر");
        PF_MAP.put('\uFB8E', "ك"); PF_MAP.put('\uFB8F', "ك"); PF_MAP.put('\uFB90', "ك"); PF_MAP.put('\uFB91', "ك");
        PF_MAP.put('\uFB92', "ك"); PF_MAP.put('\uFB93', "ك"); PF_MAP.put('\uFB94', "ك"); PF_MAP.put('\uFB95', "ك");
        PF_MAP.put('\uFBD3', "ك"); PF_MAP.put('\uFBD4', "ك"); PF_MAP.put('\uFBD5', "ك"); PF_MAP.put('\uFBD6', "ك");
        PF_MAP.put('\uFBFC', "ي"); PF_MAP.put('\uFBFD', "ي"); PF_MAP.put('\uFBFE', "ي"); PF_MAP.put('\uFBFF', "ي");
        PF_MAP.put('ک', "ك"); PF_MAP.put('ی', "ي"); PF_MAP.put('ۀ', "ه");
    }

    public static String normalizePresentationForms(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x200B && c <= 0x200F) || (c >= 0x202A && c <= 0x202E) ||
                (c >= 0x2060 && c <= 0x2069) || c == 0xFEFF || c == 0x00AD) {
                continue;
            }
            if (PF_MAP.containsKey(c)) {
                sb.append(PF_MAP.get(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String swapTanweenFathaAlif(String text) {
        if (text == null || text.isEmpty()) return text;
        // Swap Tanween Fatha with Alif for all consonants EXCEPT Waw (\u0648),
        // because Waw + Alif + Tanween (\u0648\u0627\u064B) is mispronounced as a long
        // dual syllable "-waan" (e.g. عضوان), whereas Waw + Tanween (\u0648\u064B\u0627)
        // correctly produces the short accusative singular "-wan" (عضوًا).
        String result = text.replaceAll("(?<!\u0648|\u0648\u0651)\u064B\u0627", "\u0627\u064B");
        result = result.replace("\u064B\u0649", "\u0649\u064B");
        return result;
    }

    public static String normalize(String text) {
        return normalize(text, false);
    }

    public static String normalize(String text, boolean isSingleCharRequest) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        text = text.replace("ـ", "");
        text = LatinTransliterator.transliterate(text);
        try {
            if (isSingleCharRequest) {
                String singleName = getSingleCharName(text);
                if (singleName != null) {
                    return singleName;
                }
            }
            return ArNorm.normalize(ArNorm.foldDigits(text));
        } catch (Exception e) {
            return text;
        }
    }

    public static String foldDigits(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '٠' && c <= '٩') {
                sb.append((char) ('0' + (c - '٠')));
            } else if (c >= '۰' && c <= '۹') {
                sb.append((char) ('0' + (c - '۰')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static final Pattern PAT_DATES_EXPAND = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})\\b");
    private static final Pattern PAT_TIME_COLON = Pattern.compile("(?:الساعة\\s+)?(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص|م)?(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAT_TIME_SINGLE = Pattern.compile("الساعة\\s+(\\d{1,2})\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص|م)?(?!\\d)", Pattern.CASE_INSENSITIVE);

    private static String expandAbbreviations(String text) {
        String arLetter = "[\\u0621-\\u064A\\u0670\\u0671]";
        for (Map.Entry<String, String> entry : ABBREVIATIONS.entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            String pattern = "(?!" + arLetter + ")" + Pattern.quote(key) + "(?!" + arLetter + ")";
            text = text.replaceAll(pattern, val);
        }
        return text;
    }

    private static String expandDates(String text) {
        Matcher matcher = PAT_DATES_EXPAND.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int d = Integer.parseInt(matcher.group(1));
            int m = Integer.parseInt(matcher.group(2));
            int y = Integer.parseInt(matcher.group(3));
            if (d >= 1 && d <= 31 && m >= 1 && m <= 12) {
                if (y < 100) y += (y < 50) ? 2000 : 1900;
                String dStr = getOrdinalDefinite(d);
                String mStr = MONTHS[m];
                String yStr = numberToWords(y);
                matcher.appendReplacement(sb, dStr + " مِنْ " + mStr + " " + yStr);
            } else {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String getOrdinalDefinite(int day) {
        if (day >= 1 && day <= 10) {
            return ORDINAL_UNITS_F[day];
        }
        if (day == 11) return "الحَادِيَةَ عَشَرَةَ";
        if (day == 12) return "الثَّانِيَةَ عَشَرَةَ";
        if (day >= 13 && day <= 19) {
            return "ال" + ORDINAL_UNITS_F[day - 10].replace("ال", "") + " عَشَرَةَ";
        }
        if (day == 20 || day == 30) {
            return ORDINAL_TENS[day / 10];
        }
        if (day > 20 && day <= 31) {
            int u = day % 10;
            int t = (day / 10) * 10;
            return ORDINAL_UNITS_F[u] + " وَ" + ORDINAL_TENS[t / 10];
        }
        return numberToWords(day);
    }

    private static String expandTimes(String text) {
        if (text == null) return null;
        
        Matcher m = PAT_TIME_COLON.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            int mi = Integer.parseInt(m.group(2));
            int sec = m.group(3) != null ? Integer.parseInt(m.group(3)) : -1;
            String periodStr = m.group(4);

            if (h < 0 || h > 23 || mi < 0 || mi > 59) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                continue;
            }

            String period = "";
            if (periodStr != null) {
                String pLower = periodStr.toLowerCase();
                if (pLower.contains("ص") || pLower.contains("am")) {
                    period = "صَبَاحاً";
                } else if (pLower.contains("م") || pLower.contains("pm")) {
                    period = "مَسَاءً";
                }
            }

            int hh = h;
            if (h == 0) {
                hh = 12;
                if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ";
            } else if (h == 12) {
                hh = 12;
                if (period.isEmpty()) period = "ظُهْراً";
            } else if (h > 12) {
                hh = h - 12;
                if (period.isEmpty()) period = "مَسَاءً";
            } else if (h >= 1 && h < 12) {
                hh = h;
                if (period.isEmpty() && m.group(1).startsWith("0")) {
                    period = "صَبَاحاً";
                }
            }

            String hourWord = (hh == 1) ? "الوَاحِدَةُ" : (hh >= 1 && hh <= 12 ? getOrdinalDefinite(hh) : String.valueOf(hh));
            String base = hourWord;

            String minWord = ArNorm.getDiacMinute(mi);
            if (!minWord.isEmpty()) {
                minWord = " " + minWord;
            }

            String secWord = "";
            if (sec > 0) {
                secWord = " وَ" + numberToWords(sec) + " ثَانِيَةً";
            }

            String out = base + minWord + secWord + (period.isEmpty() ? "" : (" " + period));
            m.appendReplacement(sb, Matcher.quoteReplacement(out));
        }
        m.appendTail(sb);
        text = sb.toString();

        Matcher m2 = PAT_TIME_SINGLE.matcher(text);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            int h = Integer.parseInt(m2.group(1));
            String periodStr = m2.group(2);

            if (h < 1 || h > 24) {
                m2.appendReplacement(sb2, Matcher.quoteReplacement(m2.group(0)));
                continue;
            }

            String period = "";
            if (periodStr != null) {
                String pLower = periodStr.toLowerCase();
                if (pLower.contains("ص") || pLower.contains("am")) {
                    period = "صَبَاحاً";
                } else if (pLower.contains("م") || pLower.contains("pm")) {
                    period = "مَسَاءً";
                }
            }

            int hh = h;
            if (h == 0 || h == 24) {
                hh = 12;
                if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ";
            } else if (h == 12) {
                hh = 12;
                if (period.isEmpty()) period = "ظُهْراً";
            } else if (h > 12) {
                hh = h - 12;
                if (period.isEmpty()) period = "مَسَاءً";
            } else {
                hh = h;
            }

            String hourWord = (hh == 1) ? "الوَاحِدَةُ" : (hh >= 1 && hh <= 12 ? getOrdinalDefinite(hh) : String.valueOf(hh));
            String out = hourWord + (period.isEmpty() ? "" : (" " + period));
            m2.appendReplacement(sb2, Matcher.quoteReplacement(out));
        }
        m2.appendTail(sb2);
        return sb2.toString();
    }

    private static String expandCurrencies(String text) {
        for (Map.Entry<String, String> entry : CURRENCIES.entrySet()) {
            String sym = entry.getKey();
            String name = entry.getValue();
            String escaped = Pattern.quote(sym);

            text = text.replaceAll("(\\d+)\\s*" + escaped, "$1 " + name);
            if (sym.equals("$") || sym.equals("€") || sym.equals("£")) {
                text = text.replaceAll(escaped + "\\s*(\\d+)", "$1 " + name);
            }
        }
        return text;
    }

    private static String expandPercentages(String text) {
        return text.replaceAll("(\\d+)\\s*%", "$1 بِالْمِئَة");
    }

    private static String expandMath(String text) {
        text = text.replaceAll("(?<=\\d)\\s*\\+\\s*(?=\\d)", " زَائِد ");
        text = text.replaceAll("(?<=\\d)\\s*-\\s*(?=\\d)", " نَاقِص ");
        text = text.replaceAll("(?<=\\d)\\s*\\*\\s*(?=\\d)", " ضَرَب ");
        text = text.replaceAll("(?<=\\d)\\s*/\\s*(?=\\d)", " قِسْمَة ");
        text = text.replaceAll("(?<=\\d)\\s*=\\s*(?=\\d)", " يُسَاوِي ");
        return text;
    }

    private static String expandSymbols(String text) {
        for (Map.Entry<String, String> entry : SYMBOLS.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return text;
    }

    private static String formatPunctuationSpacing(String text) {
        if (text == null) return null;
        // Insert space after punctuation marks (. ! ? ، ؛ ؟ , ; :) if followed immediately by a non-whitespace character
        return text.replaceAll("([.!?,;:\\u060C\\u061B\\u061F])([^\\s.!?,;:\\u060C\\u061B\\u061F])", "$1 $2");
    }

    private static String expandNumbers(String text) {
        Pattern pattern = Pattern.compile("\\b\\d+\\b");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            try {
                java.math.BigInteger num = new java.math.BigInteger(matcher.group(0));
                matcher.appendReplacement(sb, Num2WordsAr.cardinal(num));
            } catch (Exception e) {
                matcher.appendReplacement(sb, matcher.group(0));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String numberToWords(long number) {
        return Num2WordsAr.cardinal(number);
    }

    private static final String HAMZA_CHARS = "أإآءؤئٱ";

    public static boolean hasHamza(String word) {
        if (word == null) return false;
        for (int i = 0; i < word.length(); i++) {
            if (HAMZA_CHARS.indexOf(word.charAt(i)) != -1) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHamzaChar(char c) {
        return HAMZA_CHARS.indexOf(c) != -1;
    }

    /**
     * Flatten a Hamza-bearing character to its base letter for alignment
     * comparison. This is used only for matching, the original character
     * is always preserved in the output.
     */
    private static char flattenHamza(char c) {
        if (c == '\u0623' || c == '\u0625' || c == '\u0622' || c == '\u0671') return '\u0627'; // أ إ آ ٱ → ا
        if (c == '\u0624') return '\u0648'; // ؤ → و
        if (c == '\u0626') return '\u064A'; // ئ → ي
        if (c == '\u0649') return '\u064A'; // ى → ي (alif maqsura)
        return c;
    }

    /**
     * Flatten a whole word (strip diacritics + flatten hamza) for word-level
     * matching between original and diacritized text.
     */
    private static String flattenWord(String word) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (!isDiacritic(c)) {
                sb.append(flattenHamza(c));
            }
        }
        return sb.toString();
    }

    /**
     * Check whether two words correspond to each other after flattening.
     */
    private static boolean wordsMatch(String oWord, String dWord) {
        return flattenWord(oWord).equals(flattenWord(dWord));
    }

    private static class CharWithDiac {
        char baseChar;
        String diacritics;
        CharWithDiac(char baseChar, String diacritics) {
            this.baseChar = baseChar;
            this.diacritics = diacritics;
        }
    }

    private static java.util.List<CharWithDiac> parseCharsWithDiac(String text) {
        java.util.List<CharWithDiac> list = new java.util.ArrayList<>();
        if (text == null) return list;
        int i = 0;
        int len = text.length();
        while (i < len) {
            char base = text.charAt(i);
            i++;
            StringBuilder diac = new StringBuilder();
            while (i < len && isDiacritic(text.charAt(i))) {
                diac.append(text.charAt(i));
                i++;
            }
            list.add(new CharWithDiac(base, diac.toString()));
        }
        return list;
    }

    private static boolean isDiacritic(char c) {
        return (c >= '\u064B' && c <= '\u0655') || c == '\u0670' || c == '\u0640';
    }

    private static boolean isArabicLetter(char c) {
        return (c >= 0x0621 && c <= 0x064A) || (c >= 0x0671 && c <= 0x06D3);
    }

    // Instead of failing when character/word counts differ, uses alignment
    // that walks both sequences with independent pointers.

    /**
     * Enforces Arabic phonological and orthographic rules for Hamza and short vowels:
     * 1. 'أ' (Hamza Above): CANNOT take Kasra ('\u0650') or Tanween Kasr ('\u064D').
     *    If Kasra was predicted, replace with Fatha ('\u064E').
     * 2. 'إ' (Hamza Below): MUST take Kasra ('\u0650') or Tanween Kasr ('\u064D').
     *    If Fatha or Damma was predicted, replace with Kasra ('\u0650').
     * 3. 'ؤ' (Waw with Hamza): CANNOT take Kasra ('\u0650').
     *    If Kasra was predicted, replace with Fatha ('\u064E').
     * 4. 'آ' (Alef with Maddah): Long vowel; drop single short vowels ('\u064E', '\u064F', '\u0650', '\u0652').
     * 5. 'ى' (Alef Maksura): Silent long vowel; drop short vowels except Tanween Fatha ('\u064B').
     */
    public static String sanitizeCharVowels(char base, String diac) {
        if (diac == null || diac.isEmpty()) return diac;
        final char KASRA = '\u0650';
        final char FATHA = '\u064E';
        final char DAMMA = '\u064F';
        final char SUKOON = '\u0652';
        final char TANWEEN_FATH = '\u064B';
        final char TANWEEN_DAMM = '\u064C';
        final char TANWEEN_KASR = '\u064D';

        if (base == '\u0623') { // أ
            // Replace any kasra on Hamza Above with fatha
            if (diac.indexOf(KASRA) != -1) {
                diac = diac.replace(KASRA, FATHA);
            }
            if (diac.indexOf(TANWEEN_KASR) != -1) {
                diac = diac.replace(TANWEEN_KASR, TANWEEN_FATH);
            }
        } else if (base == '\u0625') { // إ
            // Replace fatha or damma on Hamza Below with kasra
            if (diac.indexOf(FATHA) != -1 || diac.indexOf(DAMMA) != -1) {
                diac = diac.replace(FATHA, KASRA).replace(DAMMA, KASRA);
            }
            if (diac.indexOf(TANWEEN_FATH) != -1 || diac.indexOf(TANWEEN_DAMM) != -1) {
                diac = diac.replace(TANWEEN_FATH, TANWEEN_KASR).replace(TANWEEN_DAMM, TANWEEN_KASR);
            }
        } else if (base == '\u0624') { // ؤ
            // Waw with Hamza cannot take Kasra
            if (diac.indexOf(KASRA) != -1) {
                diac = diac.replace(KASRA, FATHA);
            }
        } else if (base == '\u0622') { // آ
            // Maddah already represents Alif + Hamza + Fatha long vowel; drop short vowel marks
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < diac.length(); i++) {
                char c = diac.charAt(i);
                if (c != FATHA && c != DAMMA && c != KASRA && c != SUKOON) {
                    sb.append(c);
                }
            }
            diac = sb.toString();
        } else if (base == '\u0649') { // ى
            // Drop short vowels on Alef Maksura except Tanween Fatha
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < diac.length(); i++) {
                char c = diac.charAt(i);
                if (c != FATHA && c != DAMMA && c != KASRA && c != SUKOON) {
                    sb.append(c);
                }
            }
            diac = sb.toString();
        }
        return diac;
    }

    /**
     * Per-word hamza preservation with flexible alignment.
     * Uses flattenHamza to match characters between original and diacritized,
     * even if the diacritizer changed character counts slightly.
     * Always emits the ORIGINAL base character with the sanitized diacritics.
     */
    private static String preserveExactHamzas(String oWord, String dWord) {
        java.util.List<CharWithDiac> o = parseCharsWithDiac(oWord);
        java.util.List<CharWithDiac> d = parseCharsWithDiac(dWord);

        if (o.isEmpty()) return dWord;
        if (d.isEmpty()) return dWord;

        // Fast path: same character count — direct 1:1 mapping
        if (o.size() == d.size()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < o.size(); i++) {
                char base = o.get(i).baseChar;
                String odiac = o.get(i).diacritics;
                String ddiac = d.get(i).diacritics;
                String chosenDiac = !odiac.isEmpty() ? odiac : ddiac;
                chosenDiac = sanitizeCharVowels(base, chosenDiac);
                sb.append(base).append(chosenDiac);
            }
            return sb.toString();
        }

        // Flexible alignment: walk both lists with independent pointers
        StringBuilder sb = new StringBuilder();
        int oi = 0, di = 0;
        while (oi < o.size() && di < d.size()) {
            char oFlat = flattenHamza(o.get(oi).baseChar);
            char dFlat = flattenHamza(d.get(di).baseChar);

            if (oFlat == dFlat) {
                // Match: use original base char with best available diacritics
                char base = o.get(oi).baseChar;
                String odiac = o.get(oi).diacritics;
                String ddiac = d.get(di).diacritics;
                String chosenDiac = !odiac.isEmpty() ? odiac : ddiac;
                chosenDiac = sanitizeCharVowels(base, chosenDiac);
                sb.append(base).append(chosenDiac);
                oi++;
                di++;
            } else {
                // Mismatch: emit the diacritized char as-is and advance only di
                char base = d.get(di).baseChar;
                String ddiac = d.get(di).diacritics;
                ddiac = sanitizeCharVowels(base, ddiac);
                sb.append(base).append(ddiac);
                di++;
            }
        }
        // Remaining characters from the diacritized output
        while (di < d.size()) {
            char base = d.get(di).baseChar;
            String ddiac = d.get(di).diacritics;
            ddiac = sanitizeCharVowels(base, ddiac);
            sb.append(base).append(ddiac);
            di++;
        }
        return sb.toString();
    }

    /**
     * Restore Hamza/Alif characters from the original text into the diacritized
     * output. Uses flexible word-level alignment so that mismatched word counts
     * (from number expansion, emoji replacement, etc.) do not cause the entire
     * restoration to be skipped.
     */
    public static String restoreHamza(String original, String diacritized) {
        if (original == null || diacritized == null || original.isEmpty() || diacritized.isEmpty()) {
            return diacritized;
        }
        String[] oWords = original.split("\\s+");
        String[] dWords = diacritized.split("\\s+");

        if (oWords.length == 0 || dWords.length == 0) {
            return diacritized;
        }

        // Fast path: word counts match — direct 1:1 word mapping
        if (oWords.length == dWords.length) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < oWords.length; i++) {
                if (i > 0) sb.append(" ");
                sb.append(preserveExactHamzas(oWords[i], dWords[i]));
            }
            return sb.toString();
        }

        // Flexible word alignment: for each diacritized word, try to find its
        // matching original word. Words generated by normalization (number
        // expansion etc.) pass through unchanged.
        StringBuilder sb = new StringBuilder();
        int oi = 0;
        for (int di = 0; di < dWords.length; di++) {
            if (di > 0) sb.append(" ");
            if (oi < oWords.length && wordsMatch(oWords[oi], dWords[di])) {
                sb.append(preserveExactHamzas(oWords[oi], dWords[di]));
                oi++;
            } else {
                // Check if a later original word matches (skip non-matching originals)
                boolean found = false;
                for (int lookahead = oi + 1; lookahead < oWords.length && lookahead <= oi + 3; lookahead++) {
                    if (wordsMatch(oWords[lookahead], dWords[di])) {
                        oi = lookahead;
                        sb.append(preserveExactHamzas(oWords[oi], dWords[di]));
                        oi++;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // No matching original word — this is a generated word, pass through
                    sb.append(dWords[di]);
                }
            }
        }
        return sb.toString();
    }

    private static final String TANWEEN_SET = "\u064B\u064C\u064D";   // ً ٌ ٍ
    private static boolean isTanween(char c) { return TANWEEN_SET.indexOf(c) != -1; }
    private static boolean isAlifChar(char c) { return c == '\u0627' || c == '\u0649'; } // ا ى
    private static boolean containsTanween(String diac) {
        for (int i = 0; i < diac.length(); i++) if (isTanween(diac.charAt(i))) return true;
        return false;
    }
    private static String removeTanween(String diac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diac.length(); i++) if (!isTanween(diac.charAt(i))) sb.append(diac.charAt(i));
        return sb.toString();
    }
    private static String keepOnlyTanween(String diac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diac.length(); i++) if (isTanween(diac.charAt(i))) sb.append(diac.charAt(i));
        return sb.toString();
    }
    private static String removeShortVowels(String diac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < diac.length(); i++) {
            char c = diac.charAt(i);
            if (c != '\u064E' && c != '\u064F' && c != '\u0650') sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Give a bare seated-hamza its inherent short vowel when left without diacritics:
     * a word-initial or syllable-initial أ gets a fatha, an إ gets a kasra.
     * Also strictly sanitizes any contradictory vowels across the word according to Arabic grammar.
     */
    public static String fixBareHamzaVowel(String text) {
        if (text == null || text.isEmpty()) return text;
        final char HAMZA_ABOVE = '\u0623'; // أ -> fatha
        final char HAMZA_BELOW = '\u0625'; // إ -> kasra
        final char FATHA = '\u064E';
        final char KASRA = '\u0650';
        final char SHADDA = '\u0651';

        String[] words = text.split(" ");
        StringBuilder outAll = new StringBuilder();
        for (int w = 0; w < words.length; w++) {
            if (w > 0) outAll.append(" ");
            java.util.List<CharWithDiac> pairs = parseCharsWithDiac(words[w]);
            for (int i = 0; i < pairs.size(); i++) {
                CharWithDiac cd = pairs.get(i);
                // 1. Initial or after 'ال' bare hamza default assignment
                if (cd.diacritics.isEmpty() || cd.diacritics.equals(String.valueOf(SHADDA))) {
                    boolean isWordInitial = (i == 0);
                    boolean isAfterLam = (i >= 2 && pairs.get(i-1).baseChar == 'ل' && pairs.get(i-2).baseChar == 'ا');
                    if (isWordInitial || isAfterLam) {
                        if (cd.baseChar == HAMZA_ABOVE) {
                            cd.diacritics = cd.diacritics + FATHA;
                        } else if (cd.baseChar == HAMZA_BELOW) {
                            cd.diacritics = cd.diacritics + KASRA;
                        }
                    }
                }
                // 2. Sanitize according to strict Arabic grammar and phonology
                cd.diacritics = sanitizeCharVowels(cd.baseChar, cd.diacritics);
            }
            StringBuilder wb = new StringBuilder();
            for (CharWithDiac cd : pairs) wb.append(cd.baseChar).append(cd.diacritics);
            outAll.append(wb);
        }
        return Normalizer.normalize(outAll.toString(), Normalizer.Form.NFC);
    }

    public static String dedupeTanween(String text) {
        if (text == null || text.isEmpty()) return text;
        String[] words = text.split(" ");
        StringBuilder outAll = new StringBuilder();
        for (int w = 0; w < words.length; w++) {
            if (w > 0) outAll.append(" ");
            java.util.List<CharWithDiac> pairs = parseCharsWithDiac(words[w]);
            int n = pairs.size();
            for (int i = 0; i + 1 < n; i++) {
                CharWithDiac cur = pairs.get(i);
                CharWithDiac next = pairs.get(i + 1);
                // Tanween Alif is strictly word-final
                boolean isLastAlif = (i + 1 == n - 1) && isAlifChar(next.baseChar);
                if (!isLastAlif) continue;

                boolean consT = containsTanween(cur.diacritics);
                boolean alifT = containsTanween(next.diacritics);
                if (consT && alifT) {
                    cur.diacritics = removeTanween(cur.diacritics);
                } else if (consT && !alifT) {
                    String moved = keepOnlyTanween(cur.diacritics);
                    cur.diacritics = removeTanween(cur.diacritics);
                    next.diacritics = removeShortVowels(next.diacritics) + moved;
                }
            }

            // Ensure middle Madd Alif has Fatha on the preceding consonant so it is never swallowed
            for (int i = 0; i + 1 < n; i++) {
                CharWithDiac cur = pairs.get(i);
                CharWithDiac next = pairs.get(i + 1);
                if (next.baseChar == '\u0627' && i + 1 < n - 1) {
                    // Preceding consonant MUST have Fatha (َ)
                    if (cur.diacritics.isEmpty() || cur.diacritics.equals("\u0652")) {
                        cur.diacritics = "\u064E";
                    } else if (cur.diacritics.contains("\u0651") && !cur.diacritics.contains("\u064E") && !cur.diacritics.contains("\u064F") && !cur.diacritics.contains("\u0650")) {
                        cur.diacritics = cur.diacritics + "\u064E";
                    }
                }
            }

            StringBuilder wb = new StringBuilder();
            for (CharWithDiac cd : pairs) wb.append(cd.baseChar).append(cd.diacritics);
            outAll.append(wb);
        }
        return Normalizer.normalize(outAll.toString(), Normalizer.Form.NFC);
    }

    // Marks recognised by the -naa fix (matches the Python MARKS set exactly).
    private static final String NAA_MARKS = "\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0670";
    private static boolean isNaaMark(char c) { return NAA_MARKS.indexOf(c) != -1; }
    private static String naaBare(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (!isNaaMark(s.charAt(i))) b.append(s.charAt(i));
        return b.toString();
    }

    /**
     * Fix the 1st-person plural pronoun ending -naa (ـنا) when the diacritizer
     * mistook it for a tanween. This is a faithful port of the Windows (Python)
     * fix_naa_pronoun so the two platforms produce identical tashkeel, including
     * preserving a shadda on the nun (as in إِنَّا) rather than dropping it.
     */
    private static String fixNaaWord(String o, String d) {
        final char NUN = '\u0646';
        final char ALIF = '\u0627';
        final char FATHA = '\u064E';
        final char SHADDA = '\u0651';
        final String TANWEENS = "\u064B\u064C\u064D";

        String ob = naaBare(o);
        if (!ob.endsWith("\u0646\u0627")) return d;   // must bare-end with نا

        char[] chars = d.toCharArray();
        int n = chars.length;
        int ai = -1;
        for (int k = n - 1; k >= 0; k--) { if (chars[k] == ALIF) { ai = k; break; } }
        if (ai < 0) return d;

        StringBuilder tail = new StringBuilder();
        for (int k = ai + 1; k < n; k++) if (!isNaaMark(chars[k])) tail.append(chars[k]);

        int j = ai - 1;
        while (j >= 0 && isNaaMark(chars[j])) j--;
        if (j < 0) return d;

        // Marks that sat on that base letter (between j and ai). A shadda there is
        // part of the word and must be kept; only a mistaken tanween/vowel is
        // replaced with a plain fatha.
        boolean hadShadda = false;
        for (int k = j + 1; k < ai; k++) if (chars[k] == SHADDA) { hadShadda = true; break; }
        String nunMarks = (hadShadda ? String.valueOf(SHADDA) : "") + FATHA;

        StringBuilder out = new StringBuilder();
        if (chars[j] != NUN) {
            for (int k = 0; k <= j; k++) if (TANWEENS.indexOf(chars[k]) == -1) out.append(chars[k]);
            out.append(NUN).append(nunMarks).append(ALIF).append(tail);
            return out.toString();
        }
        for (int k = 0; k < j; k++) if (TANWEENS.indexOf(chars[k]) == -1) out.append(chars[k]);
        out.append(NUN).append(nunMarks).append(ALIF).append(tail);
        return out.toString();
    }

    public static String fixNaaPronoun(String original, String diacritized) {
        if (original == null || diacritized == null) return diacritized;
        String[] oWords = original.split(" ", -1);
        String[] dWords = diacritized.split(" ", -1);
        if (oWords.length != dWords.length) {
            if (oWords.length == 1 && dWords.length == 1) {
                return fixNaaWord(original, diacritized);
            }
            return diacritized;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < oWords.length; i++) {
            if (i > 0) sb.append(" ");
            sb.append(fixNaaWord(oWords[i], dWords[i]));
        }
        return sb.toString();
    }

    /**
     * Sukoonizes the last letter before a pause or sentence boundary.
     * Special Rule: Tanween Fatha ('\u064B') is strictly EXEMPTED from removal,
     * as it is the ONLY diacritic that is voiced and preserved at the end of a sentence.
     * Rule 1: If the final word ends with Ta Marbuta (ة) with non-tanween-fatha,
     * delete the Ta Marbuta and its diacritic, keeping the vowel on the preceding letter intact.
     * Rule 2: For any other final Arabic letter with short vowels / other tanweens,
     * strip the trailing diacritic so it is pronounced with Sukoon / paused (preserving Shadda if present).
     */
    public static String sukoonizeEndOfSentence(String text) {
        if (text == null || text.trim().isEmpty()) return text;

        // Exempt Tanween Fatha (\u064B): strictly preserved before optional punctuation and whitespace
        Pattern tanweenFathaPattern = Pattern.compile("\\u064B(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$");
        if (tanweenFathaPattern.matcher(text).find()) {
            return text;
        }

        // 1. If ending with Ta Marbuta (ة) + optional diacritics + trailing punctuation/spaces
        // Remove Ta Marbuta and its diacritic, keeping the preceding character's vowel intact
        String taMarbutaPattern = "ة[\\u064B-\\u0652\\u0670]*(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$";
        if (Pattern.compile(taMarbutaPattern).matcher(text).find()) {
            return text.replaceFirst(taMarbutaPattern, "$1");
        }

        // 2. For regular Arabic letters at the end of the sentence, remove non-tanween-fatha diacritics (preserving Shadda)
        String diacEndPattern = "([\\u0621-\\u064A\\u0671])(\\u0651?)[\\u064C\\u064D\\u064E\\u064F\\u0650\\u0652\\u0670]+(\\s*[\\.\\,\\،\\؛\\;\\?\\؟\\!\\:\\-\\_]*\\s*)$";
        return text.replaceFirst(diacEndPattern, "$1$2$3");
    }

    public static final class LatinTransliterator {
        private static final Map<String, String> KNOWN_NAMES = new HashMap<>();
        private static final Map<Character, String> SINGLE_LETTER_MAP = new HashMap<>();
        private static final Map<Character, String> LATIN_CHAR_MAP = new HashMap<>();

        private static final String[][] LATIN_COMBOS = {
            {"tion", "شِن"},
            {"sion", "شِن"},
            {"ture", "تْشَر"},
            {"cious", "شَس"},
            {"tious", "شَس"},
            {"cial", "شَل"},
            {"tial", "شَل"},
            {"ment", "مِنْت"},
            {"able", "أَبِل"},
            {"ible", "إِبِل"},
            {"less", "لِس"},
            {"ness", "نِس"},
            {"ship", "شِب"},

            {"board", "بُورْد"},
            {"card", "كَارْد"},
            {"hard", "هَارْد"},
            {"port", "بُورْت"},
            {"ford", "فُورْد"},
            {"land", "لَانْد"},
            {"wood", "وُود"},
            {"good", "جُود"},
            {"book", "بُوك"},
            {"look", "لُوك"},
            {"cook", "كُوك"},
            {"walk", "وُوك"},
            {"talk", "تُوك"},
            {"work", "وِيرْك"},
            {"word", "وِرْد"},

            // ight / aight / eight
            {"aight", "َايت"},
            {"eight", "إِيت"},
            {"ight", "َايت"},
            {"ought", "أُوت"},
            {"aught", "أُوت"},

            // Digraphs
            {"sch", "سْك"},
            {"tch", "تْش"},
            {"ch", "تْش"},
            {"sh", "ش"},
            {"th", "ث"},
            {"ph", "ف"},
            {"kh", "خ"},
            {"gh", "غ"},
            {"dh", "ذ"},
            {"zh", "ج"},
            {"wh", "و"},
            {"wr", "ر"},
            {"kn", "ن"},
            {"ps", "س"},
            {"qu", "كُو"},
            {"ck", "ك"},
            {"ng", "نْج"},

            // Vowel combinations / Diphthongs
            {"eau", "أُو"},
            {"eigh", "إِي"},
            {"air", "إِير"},
            {"ear", "إِير"},
            {"eer", "إِير"},
            {"oor", "أُور"},
            {"our", "أُور"},
            {"oar", "أُور"},
            {"ee", "ِي"},
            {"oo", "ُو"},
            {"ea", "ِي"},
            {"oa", "ُو"},
            {"ai", "َاي"},
            {"ay", "َاي"},
            {"ey", "ِي"},
            {"ei", "ِي"},
            {"oi", "ُوي"},
            {"oy", "ُوي"},
            {"ou", "َاو"},
            {"ow", "َاو"},
            {"au", "أُو"},
            {"aw", "أُو"},
            {"ie", "ِي"},
            {"ue", "ُو"},
            {"ui", "ُو"},

            {"bb", "ب"},
            {"cc", "ك"},
            {"dd", "د"},
            {"ff", "ف"},
            {"gg", "ج"},
            {"ll", "ل"},
            {"mm", "م"},
            {"nn", "ن"},
            {"pp", "ب"},
            {"rr", "ر"},
            {"ss", "س"},
            {"tt", "ت"},
            {"vv", "ف"},
            {"zz", "ز"}
        };

        static {
            SINGLE_LETTER_MAP.put('a', "إِيهْ"); SINGLE_LETTER_MAP.put('A', "إِيهْ");
            SINGLE_LETTER_MAP.put('b', "بِي"); SINGLE_LETTER_MAP.put('B', "بِي");
            SINGLE_LETTER_MAP.put('c', "سِي"); SINGLE_LETTER_MAP.put('C', "سِي");
            SINGLE_LETTER_MAP.put('d', "دِي"); SINGLE_LETTER_MAP.put('D', "دِي");
            SINGLE_LETTER_MAP.put('e', "إِي"); SINGLE_LETTER_MAP.put('E', "إِي");
            SINGLE_LETTER_MAP.put('f', "إِفْ"); SINGLE_LETTER_MAP.put('F', "إِفْ");
            SINGLE_LETTER_MAP.put('g', "جِي"); SINGLE_LETTER_MAP.put('G', "جِي");
            SINGLE_LETTER_MAP.put('h', "إِيتْشْ"); SINGLE_LETTER_MAP.put('H', "إِيتْشْ");
            SINGLE_LETTER_MAP.put('i', "آيْ"); SINGLE_LETTER_MAP.put('I', "آيْ");
            SINGLE_LETTER_MAP.put('j', "جَي"); SINGLE_LETTER_MAP.put('J', "جَي");
            SINGLE_LETTER_MAP.put('k', "كَي"); SINGLE_LETTER_MAP.put('K', "كَي");
            SINGLE_LETTER_MAP.put('l', "إِلْ"); SINGLE_LETTER_MAP.put('L', "إِلْ");
            SINGLE_LETTER_MAP.put('m', "إِمْ"); SINGLE_LETTER_MAP.put('M', "إِمْ");
            SINGLE_LETTER_MAP.put('n', "إِنْ"); SINGLE_LETTER_MAP.put('N', "إِنْ");
            SINGLE_LETTER_MAP.put('o', "أُو"); SINGLE_LETTER_MAP.put('O', "أُو");
            SINGLE_LETTER_MAP.put('p', "بِي"); SINGLE_LETTER_MAP.put('P', "بِي");
            SINGLE_LETTER_MAP.put('q', "كِيُو"); SINGLE_LETTER_MAP.put('Q', "كِيُو");
            SINGLE_LETTER_MAP.put('r', "آرْ"); SINGLE_LETTER_MAP.put('R', "آرْ");
            SINGLE_LETTER_MAP.put('s', "إِسْ"); SINGLE_LETTER_MAP.put('S', "إِسْ");
            SINGLE_LETTER_MAP.put('t', "تِي"); SINGLE_LETTER_MAP.put('T', "تِي");
            SINGLE_LETTER_MAP.put('u', "يُو"); SINGLE_LETTER_MAP.put('U', "يُو");
            SINGLE_LETTER_MAP.put('v', "فِي"); SINGLE_LETTER_MAP.put('V', "فِي");
            SINGLE_LETTER_MAP.put('w', "دَبْلِيُو"); SINGLE_LETTER_MAP.put('W', "دَبْلِيُو");
            SINGLE_LETTER_MAP.put('x', "إِكْسْ"); SINGLE_LETTER_MAP.put('X', "إِكْسْ");
            SINGLE_LETTER_MAP.put('y', "وَايْ"); SINGLE_LETTER_MAP.put('Y', "وَايْ");
            SINGLE_LETTER_MAP.put('z', "زِدْ"); SINGLE_LETTER_MAP.put('Z', "زِدْ");

            LATIN_CHAR_MAP.put('a', "َا");
            LATIN_CHAR_MAP.put('b', "ب");
            LATIN_CHAR_MAP.put('c', "ك");
            LATIN_CHAR_MAP.put('d', "د");
            LATIN_CHAR_MAP.put('e', "ِي");
            LATIN_CHAR_MAP.put('f', "ف");
            LATIN_CHAR_MAP.put('g', "ج");
            LATIN_CHAR_MAP.put('h', "ه");
            LATIN_CHAR_MAP.put('i', "ِي");
            LATIN_CHAR_MAP.put('j', "ج");
            LATIN_CHAR_MAP.put('k', "ك");
            LATIN_CHAR_MAP.put('l', "ل");
            LATIN_CHAR_MAP.put('m', "م");
            LATIN_CHAR_MAP.put('n', "ن");
            LATIN_CHAR_MAP.put('o', "ُو");
            LATIN_CHAR_MAP.put('p', "ب");
            LATIN_CHAR_MAP.put('q', "ق");
            LATIN_CHAR_MAP.put('r', "ر");
            LATIN_CHAR_MAP.put('s', "س");
            LATIN_CHAR_MAP.put('t', "ت");
            LATIN_CHAR_MAP.put('u', "ُو");
            LATIN_CHAR_MAP.put('v', "ف");
            LATIN_CHAR_MAP.put('w', "و");
            LATIN_CHAR_MAP.put('x', "كْس");
            LATIN_CHAR_MAP.put('y', "ي");
            LATIN_CHAR_MAP.put('z', "ز");

            // Comprehensive dictionary of tech brands, apps, software, and common terms (fully diacritized)
            KNOWN_NAMES.put("google", "جُوجِل");
            KNOWN_NAMES.put("android", "أَنْدْرُويْد");
            KNOWN_NAMES.put("apple", "أَبْل");
            KNOWN_NAMES.put("microsoft", "مَايْكْرُوسُوفْت");
            KNOWN_NAMES.put("windows", "وِينْدُوز");
            KNOWN_NAMES.put("linux", "لِينُكْس");
            KNOWN_NAMES.put("ubuntu", "أُوبُونْتُو");
            KNOWN_NAMES.put("meta", "مِيتَا");
            KNOWN_NAMES.put("facebook", "فَيْسْبُوك");
            KNOWN_NAMES.put("whatsapp", "وَاتْسَاب");
            KNOWN_NAMES.put("telegram", "تِلِيجْرَام");
            KNOWN_NAMES.put("instagram", "إِنْسْتِغْرَام");
            KNOWN_NAMES.put("twitter", "تُوِيتَر");
            KNOWN_NAMES.put("x", "إِكْسْ");
            KNOWN_NAMES.put("tiktok", "تِيكْ تُوك");
            KNOWN_NAMES.put("snapchat", "سْنَابْ شَات");
            KNOWN_NAMES.put("youtube", "يُوتْيُوب");
            KNOWN_NAMES.put("netflix", "نِتْفْلِيكْس");
            KNOWN_NAMES.put("spotify", "سْبُوتِيفَاي");
            KNOWN_NAMES.put("amazon", "أَمَازُون");
            KNOWN_NAMES.put("uber", "أُوبَر");
            KNOWN_NAMES.put("zoom", "زُوم");
            KNOWN_NAMES.put("skype", "سْكَايْب");
            KNOWN_NAMES.put("discord", "دِيسْكُورْد");
            KNOWN_NAMES.put("github", "جِيتْ هَاب");
            KNOWN_NAMES.put("gmail", "جِيمِيل");
            KNOWN_NAMES.put("chrome", "كْرُوم");
            KNOWN_NAMES.put("firefox", "فَايِرْفُوكْس");
            KNOWN_NAMES.put("edge", "إِيدْج");
            KNOWN_NAMES.put("safari", "سَفَارِي");
            KNOWN_NAMES.put("wikipedia", "وِيكِيبِيدْيَا");
            KNOWN_NAMES.put("tesla", "تِسْلاَ");
            KNOWN_NAMES.put("samsung", "سَامْسُونْج");
            KNOWN_NAMES.put("huawei", "هَوَاوِي");
            KNOWN_NAMES.put("xiaomi", "شَاوْمِي");
            KNOWN_NAMES.put("sony", "سُونِي");
            KNOWN_NAMES.put("intel", "إِينْتِل");
            KNOWN_NAMES.put("nvidia", "إِنْفِيدْيَا");
            KNOWN_NAMES.put("amd", "إِيهْ إِمْ دِي");

            KNOWN_NAMES.put("python", "بَايْثُون");
            KNOWN_NAMES.put("java", "جَافَا");
            KNOWN_NAMES.put("javascript", "جَافَاسْكْرِبْت");
            KNOWN_NAMES.put("typescript", "تَايِبْسْكْرِبْت");
            KNOWN_NAMES.put("php", "بِي إِتْشْ بِي");
            KNOWN_NAMES.put("html", "إِتْشْ تِي إِمْ إِل");
            KNOWN_NAMES.put("css", "سِي إِسْ إِس");
            KNOWN_NAMES.put("sql", "إِسْ كِيُو إِل");
            KNOWN_NAMES.put("api", "إِيهْ بِي آي");
            KNOWN_NAMES.put("sdk", "إِسْ دِي كَي");
            KNOWN_NAMES.put("apk", "إِيهْ بِي كَي");
            KNOWN_NAMES.put("pdf", "بِي دِي إِف");
            KNOWN_NAMES.put("ram", "رَام");
            KNOWN_NAMES.put("rom", "رُوم");
            KNOWN_NAMES.put("cpu", "سِي بِي يُو");
            KNOWN_NAMES.put("gpu", "جِي بِي يُو");
            KNOWN_NAMES.put("gps", "جِي بِي إِس");
            KNOWN_NAMES.put("sim", "سِيم");
            KNOWN_NAMES.put("vpn", "فِي بِي إِن");
            KNOWN_NAMES.put("wifi", "وَايْفَاي");
            KNOWN_NAMES.put("usb", "يُو إِسْ بِي");
            KNOWN_NAMES.put("sms", "إِسْ إِمْ إِس");
            KNOWN_NAMES.put("url", "يُو آرْ إِل");
            KNOWN_NAMES.put("ip", "آيْ بِي");
            KNOWN_NAMES.put("mac", "مَاك");
            KNOWN_NAMES.put("ios", "آيْ أُو إِس");
            KNOWN_NAMES.put("pc", "بِي سِي");
            KNOWN_NAMES.put("tv", "تِي فِي");
            KNOWN_NAMES.put("ai", "إِيهْ آي");
            KNOWN_NAMES.put("ok", "أُوكِي");
            KNOWN_NAMES.put("app", "آب");
            KNOWN_NAMES.put("apps", "آبْس");
            KNOWN_NAMES.put("bot", "بُوت");
            KNOWN_NAMES.put("link", "لِينْك");
            KNOWN_NAMES.put("online", "أُونْلاِين");
            KNOWN_NAMES.put("offline", "أُوفْلاِين");
            KNOWN_NAMES.put("email", "إِيمِيل");
            KNOWN_NAMES.put("e-mail", "إِيمِيل");
            KNOWN_NAMES.put("site", "سَايْت");
            KNOWN_NAMES.put("web", "وِيب");
            KNOWN_NAMES.put("chat", "تْشَات");
            KNOWN_NAMES.put("chatgpt", "تْشَاتْ جِي بِي تِي");
            KNOWN_NAMES.put("gemini", "جِيمِينَاي");
            KNOWN_NAMES.put("deepmind", "دِيبْ مَايْنْد");
            KNOWN_NAMES.put("openai", "أُوبْنْ إِيهْ آي");
            KNOWN_NAMES.put("antigravity", "أَنْتِي جْرَافِيتِي");
            KNOWN_NAMES.put("rhvoice", "آرْ إِتْشْ فُويْس");
            KNOWN_NAMES.put("talkback", "تُوكْ بَاك");
            KNOWN_NAMES.put("nvda", "إِنْ فِي دِي إِيه");

            KNOWN_NAMES.put("cancel", "كَانْسِل");
            KNOWN_NAMES.put("download", "دَاوْنْلُود");
            KNOWN_NAMES.put("upload", "أَبْلُود");
            KNOWN_NAMES.put("update", "أَبْدَيْت");
            KNOWN_NAMES.put("install", "إِنْسْتُول");
            KNOWN_NAMES.put("uninstall", "أَنْإِنْسْتُول");
            KNOWN_NAMES.put("setup", "سِيتْأَب");
            KNOWN_NAMES.put("settings", "سِيتِينْغْز");
            KNOWN_NAMES.put("options", "أُوبْشِنْز");
            KNOWN_NAMES.put("menu", "مِنْيُو");
            KNOWN_NAMES.put("login", "لُوجِين");
            KNOWN_NAMES.put("logout", "لُوجْآوت");
            KNOWN_NAMES.put("sign", "سَايْن");
            KNOWN_NAMES.put("signin", "سَايْنْ إِن");
            KNOWN_NAMES.put("signup", "سَايْنْ أَب");
            KNOWN_NAMES.put("password", "بَاسْوِرْد");
            KNOWN_NAMES.put("username", "يُوزَرْنِيم");
            KNOWN_NAMES.put("user", "يُوزَر");
            KNOWN_NAMES.put("admin", "أَدْمِين");
            KNOWN_NAMES.put("status", "سْتَاتُوس");
            KNOWN_NAMES.put("error", "إِيرُور");
            KNOWN_NAMES.put("warning", "وَارْنِينْج");
            KNOWN_NAMES.put("info", "إِينْفُو");
            KNOWN_NAMES.put("help", "هِيلْب");
            KNOWN_NAMES.put("search", "سِيرْتْش");
            KNOWN_NAMES.put("home", "هُوم");
            KNOWN_NAMES.put("back", "بَاك");
            KNOWN_NAMES.put("next", "نِكْسْت");
            KNOWN_NAMES.put("play", "بْلَاي");
            KNOWN_NAMES.put("pause", "بُوز");
            KNOWN_NAMES.put("stop", "سْتُوب");
            KNOWN_NAMES.put("resume", "رِيزْيُوم");
            KNOWN_NAMES.put("mute", "مْيُوت");
            KNOWN_NAMES.put("unmute", "أَنْمْيُوت");
            KNOWN_NAMES.put("reset", "رِيسِت");
            KNOWN_NAMES.put("restart", "رِيسْتَارْت");
            KNOWN_NAMES.put("power", "بَاوَر");
            KNOWN_NAMES.put("bluetooth", "بْلُوتُوث");
            KNOWN_NAMES.put("battery", "بَاتِرِي");
            KNOWN_NAMES.put("message", "مِسِيدْج");
            KNOWN_NAMES.put("messages", "مِسِيدْجِز");
            KNOWN_NAMES.put("call", "كُول");
            KNOWN_NAMES.put("calls", "كُولْز");
            KNOWN_NAMES.put("video", "فِيدْيُو");
            KNOWN_NAMES.put("audio", "أُودْيُو");
            KNOWN_NAMES.put("music", "مْيُوزِيك");
            KNOWN_NAMES.put("photo", "فُوتُو");
            KNOWN_NAMES.put("photos", "فُوتُوز");
            KNOWN_NAMES.put("image", "إِيمِيدْج");
            KNOWN_NAMES.put("camera", "كَامِيرَا");
            KNOWN_NAMES.put("gallery", "جَالِيرِي");
            KNOWN_NAMES.put("file", "فَايْل");
            KNOWN_NAMES.put("files", "فَايْلْز");
            KNOWN_NAMES.put("folder", "فُولْدَر");
            KNOWN_NAMES.put("document", "دُوكْيُومِنْت");
            KNOWN_NAMES.put("documents", "دُوكْيُومِنْتْس");
            KNOWN_NAMES.put("page", "بِيج");
            KNOWN_NAMES.put("view", "فْيُو");
            KNOWN_NAMES.put("edit", "إِدِيت");
            KNOWN_NAMES.put("delete", "دِلِيت");
            KNOWN_NAMES.put("remove", "رِيمُوف");
            KNOWN_NAMES.put("clear", "كْلِير");
            KNOWN_NAMES.put("copy", "كُوبِي");
            KNOWN_NAMES.put("cut", "كَات");
            KNOWN_NAMES.put("paste", "بَاسْت");
            KNOWN_NAMES.put("share", "شِير");
            KNOWN_NAMES.put("send", "سِينْد");
            KNOWN_NAMES.put("save", "سِيف");
            KNOWN_NAMES.put("open", "أُوبِن");
            KNOWN_NAMES.put("close", "كْلُوز");
            KNOWN_NAMES.put("exit", "إِكْزِت");
            KNOWN_NAMES.put("select", "سِلِكْت");
            KNOWN_NAMES.put("all", "أُول");
            KNOWN_NAMES.put("none", "نَان");
            KNOWN_NAMES.put("yes", "يَس");
            KNOWN_NAMES.put("no", "نُو");
            KNOWN_NAMES.put("true", "تْرُو");
            KNOWN_NAMES.put("false", "فُولْس");
            KNOWN_NAMES.put("code", "كُود");
            KNOWN_NAMES.put("data", "دَاتَا");
            KNOWN_NAMES.put("mode", "مُود");
            KNOWN_NAMES.put("test", "تِسْت");
            KNOWN_NAMES.put("version", "فِيرْشِن");
            KNOWN_NAMES.put("excel", "إِكْسِل");
            KNOWN_NAMES.put("word", "وِرْد");
            KNOWN_NAMES.put("outlook", "آوتْلُوك");
            KNOWN_NAMES.put("teams", "تِيمْز");
            KNOWN_NAMES.put("office", "أُوفِيس");
            KNOWN_NAMES.put("iphone", "آيْفُون");
        }

        private static String transliterateWord(String word) {
            if (word == null || word.isEmpty()) return word;
            String lower = word.toLowerCase(Locale.ROOT);

            // 1. Check direct known dictionary (fully diacritized)
            if (KNOWN_NAMES.containsKey(lower)) {
                return KNOWN_NAMES.get(lower);
            }

            // 2. Single letter spelling
            if (word.length() == 1 && SINGLE_LETTER_MAP.containsKey(word.charAt(0))) {
                return SINGLE_LETTER_MAP.get(word.charAt(0));
            }

            // 3. All-caps acronym spelling:
            // ONLY if uppercase, NO space, length is at most 4 characters (e.g. CPU, PDF, APK, TTS, FBI, CIA, NBC, KFC)
            if (word.length() >= 2 && word.length() <= 4 && word.matches("[A-Z]+")) {
                StringBuilder acronymSb = new StringBuilder();
                for (int i = 0; i < word.length(); i++) {
                    char ch = word.charAt(i);
                    if (SINGLE_LETTER_MAP.containsKey(ch)) {
                        if (acronymSb.length() > 0) acronymSb.append(" ");
                        acronymSb.append(SINGLE_LETTER_MAP.get(ch));
                    }
                }
                if (acronymSb.length() > 0) {
                    return acronymSb.toString();
                }
            }

            // 4. Phonetic transliteration for general words:
            String res = lower;

            // Handle silent final 'e' after consonant: e.g. name, game, plane, phone, home, code, mode, line, time, save
            if (res.length() >= 4 && res.endsWith("e")) {
                char penultimate = res.charAt(res.length() - 2);
                char antepenultimate = res.charAt(res.length() - 3);
                if ("bcdfghjklmnpqrstvwxyz".indexOf(penultimate) != -1 && "aeiouy".indexOf(antepenultimate) != -1) {
                    String prefix = res.substring(0, res.length() - 3);
                    if (antepenultimate == 'a') {
                        res = prefix + "ِي" + penultimate; // name -> نِيم, game -> جِيم, plane -> بْلِين
                    } else if (antepenultimate == 'o') {
                        res = prefix + "ُو" + penultimate; // phone -> فُون, home -> هُوم, code -> كُود
                    } else if (antepenultimate == 'i') {
                        res = prefix + "َاي" + penultimate; // line -> لاِين, time -> تَايْم
                    } else if (antepenultimate == 'u') {
                        res = prefix + "ُو" + penultimate; // tube -> تُوب
                    } else {
                        res = res.substring(0, res.length() - 1);
                    }
                }
            }

            for (String[] combo : LATIN_COMBOS) {
                res = res.replace(combo[0], combo[1]);
            }

            // Context-sensitive rules:
            // 'c' before 'e', 'i', 'y' -> 'س' ; else 'ك'
            res = res.replaceAll("c(?=[eiy])", "س");
            res = res.replace("c", "ك");

            // 'g' before 'e', 'i', 'y' -> 'ج' ; else 'ج'
            res = res.replaceAll("g(?=[eiy])", "ج");
            res = res.replace("g", "ج");

            // 'x' at start -> 'ز' ; else 'كْس'
            res = res.replaceAll("^x", "ز");
            res = res.replace("x", "كْس");

            StringBuilder finalRes = new StringBuilder();
            for (int i = 0; i < res.length(); i++) {
                char ch = res.charAt(i);
                if (LATIN_CHAR_MAP.containsKey(ch)) {
                    finalRes.append(LATIN_CHAR_MAP.get(ch));
                } else {
                    finalRes.append(ch);
                }
            }
            String out = finalRes.toString();

            // Fix word beginnings if starting with a bare vowel
            if (out.startsWith("َا") || out.startsWith("ا")) {
                out = "أَ" + out.replaceFirst("^َا+", "").replaceFirst("^ا+", "");
            } else if (out.startsWith("ِي") || out.startsWith("ي")) {
                out = "إِ" + out.replaceFirst("^ِي+", "").replaceFirst("^ي+", "");
            } else if (out.startsWith("ُو") || out.startsWith("و")) {
                out = "أُ" + out.replaceFirst("^ُو+", "").replaceFirst("^و+", "");
            }
            return out;
        }

        private static final Pattern PAT_LATIN_WORD =
            Pattern.compile("\\b[A-Za-z][A-Za-z0-9]*(?:[.'\\-][A-Za-z0-9]+)*\\b");

        public static String transliterate(String text) {
            if (text == null || text.isEmpty()) return text;
            Matcher matcher = PAT_LATIN_WORD.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(transliterateWord(matcher.group(0))));
            }
            matcher.appendTail(sb);
            return sb.toString();
        }

        public static boolean isTransliterated(String word) {
            if (word == null) return false;
            String lower = word.toLowerCase(Locale.ROOT);
            return KNOWN_NAMES.containsKey(lower) || KNOWN_NAMES.containsValue(word);
        }
    }

    /**
     * Faithful Java port of the Windows (Python) num2words Arabic cardinal/ordinal
     * used by the tashkeel normalizer, so numbers are spelled identically on both
     * platforms. Ported from lib_pure/num2words/lang_AR.py (Num2Word_AR).
     */
    public static final class Num2WordsAr {
    
        private static final String[] ARABIC_ONES = {
            "", "وَاحِدٌ", "اثْنَانِ", "ثَلَاثَةٌ", "أَرْبَعَةٌ", "خَمْسَةٌ", "سِتَّةٌ", "سَبْعَةٌ", "ثَمَانِيَةٌ", "تِسْعَةٌ",
            "عَشَرَةٌ", "أَحَدَ عَشَرَ", "اثْنَا عَشَرَ", "ثَلَاثَةَ عَشَرَ", "أَرْبَعَةَ عَشَرَ", "خَمْسَةَ عَشَرَ",
            "سِتَّةَ عَشَرَ", "سَبْعَةَ عَشَرَ", "ثَمَانِيَةَ عَشَرَ", "تِسْعَةَ عَشَرَ"
        };
        private static final String[] ARABIC_FEMININE_ONES = {
            "", "إِحْدَى", "اثْنَتَانِ", "ثَلَاثٌ", "أَرْبَعٌ", "خَمْسٌ", "سِتٌّ", "سَبْعٌ", "ثَمَانٍ", "تِسْعٌ",
            "عَشْرٌ", "إِحْدَى عَشْرَةَ", "اثْنَتَا عَشْرَةَ", "ثَلَاثَ عَشْرَةَ", "أَرْبَعَ عَشْرَةَ",
            "خَمْسَ عَشْرَةَ", "سِتَّ عَشْرَةَ", "سَبْعَ عَشْرَةَ", "ثَمَانِيَ عَشْرَةَ", "تِسْعَ عَشْرَةَ"
        };
        private static final String[] ARABIC_ORDINAL = {
            "", "أَوَّلُ", "ثَانِي", "ثَالِثٌ", "رَابِعٌ", "خَامِسٌ", "سَادِسٌ", "سَابِعٌ", "ثَامِنٌ",
            "تَاسِعٌ", "عَاشِرٌ", "حَادِيَ عَشَرَ", "ثَانِيَ عَشَرَ", "ثَالِثَ عَشَرَ", "رَابِعَ عَشَرَ",
            "خَامِسَ عَشَرَ", "سَادِسَ عَشَرَ", "سَابِعَ عَشَرَ", "ثَامِنَ عَشَرَ", "تَاسِعَ عَشَرَ"
        };
        private static final String[] ARABIC_TENS = {
            "عِشْرُونَ", "ثَلَاثُونَ", "أَرْبَعُونَ", "خَمْسُونَ", "سِتُّونَ", "سَبْعُونَ", "ثَمَانُونَ", "تِسْعُونَ"
        };
        private static final String[] ARABIC_HUNDREDS = {
            "", "مِائَةٌ", "مِائَتَانِ", "ثَلَاثُمِائَةٍ", "أَرْبَعُمِائَةٍ", "خَمْسُمِائَةٍ", "سِتُّمِائَةٍ",
            "سَبْعُمِائَةٍ", "ثَمَانِمِائَةٍ", "تِسْعُمِائَةٍ"
        };
        private static final String[] ARABIC_APPENDED_TWOS = {
            "مِائَتَا", "أَلْفَا", "مِلْيُونَا", "مِلْيَارَا", "تِرِيلْيُونَا", "كُوَادْرِيلْيُونَا",
            "كُوِينْتِلْيُونَا", "سِكْسْتِيلْيُونَا", "سَبْتِيلْيُونَا", "أُوكْتِيلْيُونَا",
            "نُونِيلْيُونَا", "دِيسِيلْيُونَا", "أَنْدِسِيلْيُونَا", "دُودِيسِيلْيُونَا",
            "تْرِيدِيسِيلْيُونَا", "كُوَادْرِيسِيلْيُونَا", "كُوِينْتِينِيلْيُونَا"
        };
        private static final String[] ARABIC_TWOS = {
            "مِائَتَانِ", "أَلْفَانِ", "مِلْيُونَانِ", "مِلْيَارَانِ", "تِرِيلْيُونَانِ",
            "كُوَادْرِيلْيُونَانِ", "كُوِينْتِلْيُونَانِ", "سِكْسْتِيلْيُونَانِ", "سَبْتِيلْيُونَانِ",
            "أُوكْتِيلْيُونَانِ", "نُونِيلْيُونَانِ", "دِيسِيلْيُونَانِ", "أَنْدِسِيلْيُونَانِ",
            "دُودِيسِيلْيُونَانِ", "تْرِيدِيسِيلْيُونَانِ", "كُوَادْرِيسِيلْيُونَانِ", "كُوِينْتِينِيلْيُونَانِ"
        };
        private static final String[] ARABIC_GROUP = {
            "مِائَةٌ", "أَلْفٌ", "مِلْيُونٌ", "مِلْيَارٌ", "تِرِيلْيُونٌ", "كُوَادْرِيلْيُونٌ",
            "كُوِينْتِلْيُونٌ", "سِكْسْتِيلْيُونٌ", "سَبْتِيلْيُونٌ", "أُوكْتِيلْيُونٌ", "نُونِيلْيُونٌ",
            "دِيسِيلْيُونٌ", "أَنْدِسِيلْيُونٌ", "دُودِيسِيلْيُونٌ", "تْرِيدِيسِيلْيُونٌ",
            "كُوَادْرِيسِيلْيُونٌ", "كُوِينْتِينِيلْيُونٌ"
        };
        private static final String[] ARABIC_APPENDED_GROUP = {
            "", "أَلْفاً", "مِلْيُوناً", "مِلْيَاراً", "تِرِيلْيُوناً", "كُوَادْرِيلْيُوناً",
            "كُوِينْتِلْيُوناً", "سِكْسْتِيلْيُوناً", "سَبْتِيلْيُوناً", "أُوكْتِيلْيُوناً",
            "نُونِيلْيُوناً", "دِيسِيلْيُوناً", "أَنْدِسِيلْيُوناً", "دُودِيسِيلْيُوناً",
            "تْرِيدِيسِيلْيُوناً", "كُوَادْرِيسِيلْيُوناً", "كُوِينْتِينِيلْيُوناً"
        };
        private static final String[] ARABIC_PLURAL_GROUPS = {
            "", "آلَافٍ", "مَلَايِينُ", "مِلْيَارَاتٌ", "تِرِيلْيُونَاتٌ", "كُوَادْرِيلْيُونَاتٌ",
            "كُوِينْتِلْيُونَاتٌ", "سِكْسْتِيلْيُونَاتٌ", "سَبْتِيلْيُونَاتٌ", "أُوكْتِيلْيُونَاتٌ",
            "نُونِيلْيُونَاتٌ", "دِيسِيلْيُونَاتٌ", "أَنْدِسِيلْيُونَاتٌ", "دُودِيسِيلْيُونَاتٌ",
            "تْرِيدِيسِيلْيُونَاتٌ", "كُوَادْرِيسِيلْيُونَاتٌ", "كُوِينْتِينِيلْيُونَاتٌ"
        };
    
        private BigInteger integerValue;
        private boolean isCurrencyNameFeminine = false;
    
        private String digitFeminineStatus(int digit, int groupLevel) {
            if (groupLevel == 0 && isCurrencyNameFeminine) {
                return ARABIC_FEMININE_ONES[digit];
            }
            return ARABIC_ONES[digit];
        }
    
        private String processArabicGroup(long groupNumber, int groupLevel, BigInteger remainingNumber) {
            long tens = groupNumber % 100;
            long hundreds = groupNumber / 100;
            String retVal = "";
    
            if (hundreds > 0) {
                if (tens == 0 && hundreds == 2 && groupLevel > 0) {
                    retVal = ARABIC_APPENDED_TWOS[0];
                } else {
                    retVal = ARABIC_HUNDREDS[(int) hundreds];
                    if (!retVal.isEmpty() && tens != 0) {
                        retVal += " وَ";
                    }
                }
            }
    
            if (tens > 0) {
                if (tens < 20) {
                    if (tens == 2 && hundreds == 0 && groupLevel > 0) {
                        retVal = ARABIC_TWOS[groupLevel];
                    } else {
                        if (tens == 1 && groupLevel > 0) {
                            retVal += ARABIC_GROUP[groupLevel];
                        } else {
                            retVal += digitFeminineStatus((int) tens, groupLevel);
                        }
                    }
                } else {
                    long ones = tens % 10;
                    long t = (tens / 10) - 2;
                    if (ones > 0) {
                        retVal += digitFeminineStatus((int) ones, groupLevel);
                    }
                    if (!retVal.isEmpty() && ones != 0) {
                        retVal += " وَ";
                    }
                    retVal += ARABIC_TENS[(int) t];
                }
            }
            return retVal;
        }
    
        private String convertToArabic(BigInteger number) {
            if (number.equals(BigInteger.ZERO)) return "صِفْرٌ";
            BigInteger tempNumber = number;
            String retVal = "";
            int group = 0;
            BigInteger thousand = BigInteger.valueOf(1000);
            while (tempNumber.compareTo(BigInteger.ZERO) > 0) {
                long numberToProcess = tempNumber.remainder(thousand).longValue();
                tempNumber = tempNumber.divide(thousand);
                String groupDescription = processArabicGroup(numberToProcess, group, tempNumber);
                if (!groupDescription.isEmpty()) {
                    if (group > 0) {
                        if (numberToProcess != 2 && numberToProcess != 1) {
                            long lastTwo = numberToProcess % 100;
                            if (lastTwo >= 3 && lastTwo <= 10) {
                                groupDescription = groupDescription + " " + ARABIC_PLURAL_GROUPS[group];
                            } else if (lastTwo >= 11 && lastTwo <= 99) {
                                groupDescription = groupDescription + " " + ARABIC_APPENDED_GROUP[group];
                            } else {
                                groupDescription = groupDescription + " " + ARABIC_GROUP[group];
                            }
                        }
                        if (!retVal.isEmpty()) {
                            retVal = groupDescription + " وَ" + retVal;
                        } else {
                            retVal = groupDescription;
                        }
                    } else {
                        retVal = groupDescription;
                    }
                }
                group += 1;
            }
            return retVal;
        }
    
        public String toCardinal(BigInteger number) {
            isCurrencyNameFeminine = false;
            String minus = "";
            if (number.compareTo(BigInteger.ZERO) < 0) { minus = "سَالِبُ "; number = number.negate(); }
            integerValue = number;
            return (minus + convertToArabic(number).trim()).trim();
        }
    
        public String toOrdinal(BigInteger number) {
            if (number.compareTo(BigInteger.valueOf(19)) <= 0 && number.compareTo(BigInteger.ZERO) >= 0) return ARABIC_ORDINAL[number.intValue()];
            isCurrencyNameFeminine = (number.compareTo(BigInteger.valueOf(100)) < 0);
            integerValue = number;
            return convertToArabic(number).trim();
        }
    
        // Convenience static wrappers matching the Python _cardinal/_ordinal usage.
        public static String cardinal(BigInteger n) { return new Num2WordsAr().toCardinal(n); }
        public static String cardinal(long n) { return cardinal(BigInteger.valueOf(n)); }
        public static String ordinal(BigInteger n) { return new Num2WordsAr().toOrdinal(n); }
        public static String ordinal(long n) { return ordinal(BigInteger.valueOf(n)); }
    }

    public static final class ArNorm {
    
        public static String toAsciiDigits(String s) {
            if (s == null) return null;
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= '\u0660' && c <= '\u0669') b.append((char) ('0' + (c - '\u0660')));
                else if (c >= '\u06F0' && c <= '\u06F9') b.append((char) ('0' + (c - '\u06F0')));
                else b.append(c);
            }
            return b.toString();
        }
        public static String foldDigits(String s) { return s == null ? s : toAsciiDigits(s); }
    
        static String cardinal(String n) { return Num2WordsAr.cardinal(new java.math.BigInteger(n)); }
        static String cardinal(long n) { return Num2WordsAr.cardinal(n); }
    
        private static final Map<Character, String> DIGIT_NAMES = new HashMap<>();
        static {
            DIGIT_NAMES.put('0', "صِفْرٌ"); DIGIT_NAMES.put('1', "وَاحِدٌ"); DIGIT_NAMES.put('2', "اثْنَانِ");
            DIGIT_NAMES.put('3', "ثَلَاثَةٌ"); DIGIT_NAMES.put('4', "أَرْبَعَةٌ"); DIGIT_NAMES.put('5', "خَمْسَةٌ");
            DIGIT_NAMES.put('6', "سِتَّةٌ"); DIGIT_NAMES.put('7', "سَبْعَةٌ"); DIGIT_NAMES.put('8', "ثَمَانِيَةٌ");
            DIGIT_NAMES.put('9', "تِسْعَةٌ");
        }
        static String spellDigits(String digits) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0) b.append(" ");
                char c = digits.charAt(i);
                b.append(DIGIT_NAMES.getOrDefault(c, String.valueOf(c)));
            }
            return b.toString();
        }
    
        private static final Map<String, String> FEM_ORD = new HashMap<>();
        static {
            FEM_ORD.put("أَوَّلُ", "الأُولَى"); FEM_ORD.put("ثَانِي", "الثَّانِيَةُ"); FEM_ORD.put("ثَالِثٌ", "الثَّالِثَةُ");
            FEM_ORD.put("رَابِعٌ", "الرَّابِعَةُ"); FEM_ORD.put("خَامِسٌ", "الخَامِسَةُ"); FEM_ORD.put("سَادِسٌ", "السَّادِسَةُ");
            FEM_ORD.put("سَابِعٌ", "السَّابِعَةُ"); FEM_ORD.put("ثَامِنٌ", "الثَّامِنَةُ"); FEM_ORD.put("تَاسِعٌ", "التَّاسِعَةُ");
            FEM_ORD.put("عَاشِرٌ", "العَاشِرَةُ");
        }
        static String ordinal(int n) { return ordinal(n, false); }
        static String ordinal(int n, boolean feminine) {
            String w = Num2WordsAr.ordinal(n);
            if (w == null || w.isEmpty()) return String.valueOf(n);
            if (feminine && !w.endsWith("ة") && !w.endsWith("َةُ") && !w.endsWith("َةَ")) return FEM_ORD.getOrDefault(w, w);
            return w;
        }
    
        private static final String[] ORD_UNITS_M = {
            "", "الأَوَّلُ", "الثَّانِي", "الثَّالِثُ", "الرَّابِعُ", "الخَامِسُ",
            "السَّادِسُ", "السَّابِعُ", "الثَّامِنُ", "التَّاسِعُ", "العَاشِرُ"
        };
        private static final String[] ORD_UNITS_F = {
            "", "الأُولَى", "الثَّانِيَةُ", "الثَّالِثَةُ", "الرَّابِعَةُ", "الخَامِسَةُ",
            "السَّادِسَةُ", "السَّابِعَةُ", "الثَّامِنَةُ", "التَّاسِعَةُ", "العَاشِرَةُ"
        };
        static String ordinalDefinite(int n, boolean feminine) {
            String[] tbl = feminine ? ORD_UNITS_F : ORD_UNITS_M;
            if (n >= 1 && n <= 10) return tbl[n];
            if (n == 11 || n == 12) {
                if (feminine) return n == 11 ? "الحَادِيَةَ عَشْرَةَ" : "الثَّانِيَةَ عَشْرَةَ";
                return n == 11 ? "الحَادِيَ عَشَرَ" : "الثَّانِيَ عَشَرَ";
            }
            if (n >= 13 && n <= 19) {
                String u = tbl[n - 10].replaceFirst("ال", "");
                return feminine ? ("ال" + u + " عَشْرَةَ") : ("ال" + u + " عَشَرَ");
            }
            if (n == 20) return "العِشْرُونَ";
            if (n == 30) return "الثَّلَاثُونَ";
            if (n >= 21 && n <= 39) {
                String u = tbl[n % 10];
                String t10 = ((n / 10) == 2) ? "العِشْرُونَ" : "الثَّلَاثُونَ";
                return u + " وَ" + t10;
            }
            return ordinal(n, feminine);
        }
        static String ordinalDefinite(int n) { return ordinalDefinite(n, true); }
    
        private static final String[] MONTHS = {"", "يَنَايِرَ", "فِبْرَايِرَ", "مَارِسَ", "أَبْرِيلَ", "مَايُو", "يُونْيُو",
            "يُولْيُو", "أُغُسْطُسَ", "سِبْتَمْبِرَ", "أُكْتُوبِرَ", "نُوفَمْبِرَ", "دِيسَمْبِرَ"};
    
        private static final LinkedHashMap<String, String> CURRENCY = new LinkedHashMap<>();
        static {
            Map<String, String> c = new HashMap<>();
            c.put("$", "دُولَاراً"); c.put("USD", "دُولَاراً"); c.put("£", "جُنَيْهاً"); c.put("GBP", "جُنَيْهاً");
            c.put("€", "يُورُو"); c.put("EUR", "يُورُو"); c.put("¥", "يِين");
            c.put("ر.س", "رِيَالاً"); c.put("ريال", "رِيَالاً"); c.put("SAR", "رِيَالاً");
            c.put("د.إ", "دِرْهَماً"); c.put("درهم", "دِرْهَماً"); c.put("AED", "دِرْهَماً");
            c.put("ج.م", "جُنَيْهاً مِصْرِيّاً"); c.put("د.ك", "دِينَاراً كُوَيْتِيّاً"); c.put("د.ع", "دِينَاراً عِرَاقِيّاً");
            c.put("ل.ل", "لِيرَةً لُبْنَانِيَّةً"); c.put("ل.س", "لِيرَةً سُورِيَّةً"); c.put("د.أ", "دِينَاراً أُرْدُنِيّاً");
            c.put("ر.ق", "رِيَالاً قَطَرِيّاً"); c.put("د.ب", "دِينَاراً بَحْرَيْنِيّاً"); c.put("ر.ع", "رِيَالاً عُمَانِيّاً");
            List<String> keys = new ArrayList<>(c.keySet());
            keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (String k : keys) CURRENCY.put(k, c.get(k));
        }

        private static final List<String[]> ABBREV = new ArrayList<>();
        static {
        }

        private static final String AR_LETTER = "[\\u0621-\\u064A\\u0670\\u0671]";

        private static final class CompiledAbbrev {
            final Pattern pattern;
            final String replacement;
            CompiledAbbrev(Pattern pattern, String replacement) {
                this.pattern = pattern;
                this.replacement = Matcher.quoteReplacement(replacement);
            }
        }
        private static final List<CompiledAbbrev> COMPILED_ABBREV = new ArrayList<>();
        static {
            for (String[] kv : ABBREV) {
                String esc = Pattern.quote(kv[0]);
                String pattern = "(?<!" + AR_LETTER + ")" + esc + "(?!" + AR_LETTER + ")";
                COMPILED_ABBREV.add(new CompiledAbbrev(Pattern.compile(pattern), kv[1]));
            }
        }

        private static final class CompiledCurrency {
            final String sym;
            final String unit;
            final Pattern patternPost;
            final Pattern patternPre;
            CompiledCurrency(String sym, String unit) {
                this.sym = sym;
                this.unit = unit;
                String esym = Pattern.quote(sym);
                this.patternPost = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*" + esym);
                if (sym.equals("$") || sym.equals("£") || sym.equals("€") || sym.equals("¥")) {
                    this.patternPre = Pattern.compile(esym + "\\s*(\\d+(?:\\.\\d+)?)");
                } else {
                    this.patternPre = null;
                }
            }
        }
        private static final List<CompiledCurrency> COMPILED_CURRENCIES = new ArrayList<>();
        static {
            for (Map.Entry<String, String> e : CURRENCY.entrySet()) {
                COMPILED_CURRENCIES.add(new CompiledCurrency(e.getKey(), e.getValue()));
            }
        }

        private static final Pattern PAT_DATES = Pattern.compile("\\b(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{2,4})\\b");
        private static final Pattern PAT_TIME_COLON_ARNORM = Pattern.compile("(?:السَّاعَةُ\\s+|السَّاعَةَ\\s+|الساعة\\s+)?(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))?(?!\\d)", Pattern.CASE_INSENSITIVE);
        private static final Pattern PAT_TIME_SINGLE_ARNORM = Pattern.compile("(?:السَّاعَةُ|السَّاعَةَ|الساعة)\\s+(\\d{1,2})\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))?(?!\\d)", Pattern.CASE_INSENSITIVE);
        private static final Pattern PAT_TIME_STANDALONE_ARNORM = Pattern.compile("(?<![\\d:])\\b(1[0-2]|0?[1-9])\\s*(صباحاً|صباحا|مساءً|مساء|a\\.m\\.|p\\.m\\.|am|pm|ص\\.|م\\.|ص(?!\\w|[\\u0621-\\u064A\\u0670\\u0671])|م(?!\\w|[\\u0621-\\u064A\\u0670\\u0671]))(?![\\d:])", Pattern.CASE_INSENSITIVE);
        private static final Pattern PAT_PERCENT = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");
        private static final Pattern PAT_PERMILLE = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*‰");
        private static final Pattern PAT_MATH_PLUS = Pattern.compile("(?<=\\d)\\s*\\+\\s*(?=\\d)");
        private static final Pattern PAT_MATH_MINUS = Pattern.compile("(?<=\\d)\\s*[-\u2212]\\s*(?=\\d)");
        private static final Pattern PAT_MATH_MUL = Pattern.compile("(?<=\\d)\\s*[\u00D7x]\\s*(?=\\d)");
        private static final Pattern PAT_MATH_DIV = Pattern.compile("(?<=\\d)\\s*[\u00F7/]\\s*(?=\\d)");
        private static final Pattern PAT_MATH_EQ = Pattern.compile("(?<=\\d)\\s*=\\s*");
        private static final Pattern PAT_PHONE = Pattern.compile("\\+?\\d[\\d\\s\\-]{6,}\\d");
        private static final Pattern PAT_ORDINALS = Pattern.compile("الـ\\s*(\\d{1,3})");
        private static final Pattern PAT_ROMAN = Pattern.compile("\\b(?=[IVX]+\\b)[IVX]{1,5}\\b");
        private static final Pattern PAT_VERSION_CLEAN = Pattern.compile("(?i)(إصدار|النسخة|نسخة|تطبيق|الإصدار)\\s+[vV](\\d+)");
        private static final Pattern PAT_VERSION_V = Pattern.compile("(?i)\\b[vV](\\d+(?:\\.\\d+)*)\\b");
        private static final Pattern PAT_VERSION_MULTIDOT = Pattern.compile("\\b(\\d+(?:\\.\\d+)+)\\b");
        private static final Pattern PAT_DECIMAL = Pattern.compile("(\\d+)[.,،\u066B](\\d+)");
        private static final Pattern PAT_INTEGER = Pattern.compile("\\d+");
        private static final Pattern PAT_MULTI_SPACE = Pattern.compile("\\s{2,}");
    
        private static final LinkedHashMap<String, String> SYMBOLS = new LinkedHashMap<>();
        static {
            SYMBOLS.put("&", "وَ"); SYMBOLS.put("@", "آت"); SYMBOLS.put("#", "رَقْم");
            SYMBOLS.put("%", "بِالْمِئَة"); SYMBOLS.put("٪", "بِالْمِئَة"); SYMBOLS.put("‰", "بِالْأَلْف");
            SYMBOLS.put("+", "زَائِد"); SYMBOLS.put("=", "يُسَاوِي"); SYMBOLS.put("≠", "لَا يُسَاوِي");
            SYMBOLS.put("<", "أَصْغَرُ مِنْ"); SYMBOLS.put(">", "أَكْبَرُ مِنْ");
            SYMBOLS.put("≤", "أَصْغَرُ أَوْ يُسَاوِي"); SYMBOLS.put("≥", "أَكْبَرُ أَوْ يُسَاوِي");
            SYMBOLS.put("±", "زَائِدٌ أَوْ نَاقِص"); SYMBOLS.put("×", "ضَرْب"); SYMBOLS.put("÷", "قِسْمَة");
            SYMBOLS.put("√", "جَذْر"); SYMBOLS.put("∞", "مَا لَا نِهَايَة"); SYMBOLS.put("π", "بَاي");
            SYMBOLS.put("°", "دَرَجَة"); SYMBOLS.put("µ", "مِيكْرُو"); SYMBOLS.put("©", "حُقُوقُ النَّشْر");
            SYMBOLS.put("®", "عَلَامَةٌ مُسَجَّلَة"); SYMBOLS.put("™", "عَلَامَةٌ تِجَارِيَّة");
            SYMBOLS.put("§", "فِقْرَة"); SYMBOLS.put("¶", "عَلَامَةُ فِقْرَة");
            SYMBOLS.put("†", "عَلَامَةُ إِحَالَة"); SYMBOLS.put("•", "نُقْطَةُ تَعْدَاد");
            SYMBOLS.put("·", "نُقْطَةٌ وَسَطِيَّة");
            SYMBOLS.put("★", "نَجْمَة"); SYMBOLS.put("☆", "نَجْمَة"); SYMBOLS.put("٭", "نَجْمَة");
            SYMBOLS.put("→", "يُؤَدِّي إِلَى");
            SYMBOLS.put("←", "مِنْ"); SYMBOLS.put("↔", "ذَهَابًا وَإِيَابًا"); SYMBOLS.put("⇒", "إِذَنْ");
            SYMBOLS.put("~", "تَقْرِيبًا"); SYMBOLS.put("|", "خَطٌّ عَمُودِيّ");
            SYMBOLS.put("﴿", "قَوْسٌ قُرْآنِيٌّ مَفْتُوح"); SYMBOLS.put("﴾", "قَوْسٌ قُرْآنِيٌّ مُغْلَق");
            SYMBOLS.put("۞", "رُبْعُ حِزْب"); SYMBOLS.put("۩", "عَلَامَةُ سَجْدَة");
        }

        private static final String[][] ROMAN = {
            {"XXI","21"},{"XIX","19"},{"XVIII","18"},{"XVII","17"},
            {"XVI","16"},{"XIV","14"},{"XIII","13"},{"XII","12"},
            {"XI","11"},{"VIII","8"},{"VII","7"},{"VI","6"},
            {"XX","20"},{"XV","15"},{"IX","9"},{"IV","4"},
            {"X","10"},{"V","5"},{"III","3"},{"II","2"},{"I","1"}
        };
    
        private static String amountWords(String num, String unit) {
            if (num.contains(".")) {
                String[] parts = num.split("\\.", 2);
                String whole = parts[0], frac = parts[1];
                String w = cardinal(whole);
                if (!frac.isEmpty() && Long.parseLong(frac) != 0) {
                    String f2 = (frac + "00").substring(0, 2);
                    return w + " " + unit + " وَ" + cardinal(f2);
                }
                return w + " " + unit;
            }
            return cardinal(num) + " " + unit;
        }
    
        private static String cardinalDec(String num) {
            String[] parts = num.split("[.,،\u066B]", 2);
            if (parts.length == 2) {
                return cardinalDec(parts[0], parts[1]);
            }
            return cardinal(num);
        }

        private static String cardinalDec(String whole, String frac) {
            if (frac.equals("0") || frac.equals("00")) {
                return cardinal(whole) + " فَاصِلَة صِفْرٌ";
            }
            return cardinal(whole) + " فَاصِلَة " + spellDigits(frac);
        }
    
        private static String xDates(String t) {
            Matcher m = PAT_DATES.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int d = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2));
                int y = Integer.parseInt(m.group(3));
                if (d < 1 || d > 31 || mo < 1 || mo > 12) { m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0))); continue; }
                if (y < 100) y += (y < 50) ? 2000 : 1900;
                String out = ordinalDefinite(d, false) + " مِنْ " + MONTHS[mo] + " " + cardinal(y);
                m.appendReplacement(sb, Matcher.quoteReplacement(out));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    
        private static final String[] DIAC_ORDINAL_HOURS = {
            "", "الوَاحِدَةُ", "الثَّانِيَةُ", "الثَّالِثَةُ", "الرَّابِعَةُ", "الخَامِسَةُ",
            "السَّادِسَةُ", "السَّابِعَةُ", "الثَّامِنَةُ", "التَّاسِعَةُ", "العَاشِرَةُ",
            "الحَادِيَةَ عَشْرَةَ", "الثَّانِيَةَ عَشْرَةَ"
        };
    
        private static final String[] DIAC_MIN_ONES_3_10 = {
            "", "", "", "ثَلَاثُ", "أَرْبَعُ", "خَمْسُ", "سِتُّ", "سَبْعُ", "ثَمَانِي", "تِسْعُ", "عَشْرُ"
        };
    
        private static final String[] DIAC_MIN_TEENS = {
            "", "", "", "", "", "", "", "", "", "", "",
            "إِحْدَى عَشْرَةَ", "اثْنَتَا عَشْرَةَ", "ثَلَاثَ عَشْرَةَ", "أَرْبَعَ عَشْرَةَ",
            "خَمْسَ عَشْرَةَ", "سِتَّ عَشْرَةَ", "سَبْعَ عَشْرَةَ", "ثَمَانِيَ عَشْرَةَ", "تِسْعَ عَشْرَةَ"
        };
    
        private static final String[] DIAC_MIN_TENS = {
            "", "", "عِشْرُونَ", "ثَلَاثُونَ", "أَرْبَعُونَ", "خَمْسُونَ"
        };
    
        private static final String[] DIAC_MIN_COMPOUND_ONES = {
            "", "", "", "ثَلَاثٌ", "أَرْبَعٌ", "خَمْسٌ", "سِتٌّ", "سَبْعٌ", "ثَمَانٍ", "تِسْعٌ"
        };
    
        public static String getDiacMinute(int mi) {
            if (mi <= 0 || mi > 59) return "";
            if (mi == 1) return "وَدَقِيقَةٌ وَاحِدَةٌ";
            if (mi == 2) return "وَدَقِيقَتَانِ";
            if (mi >= 3 && mi <= 10) {
                return "وَ" + DIAC_MIN_ONES_3_10[mi] + " دَقَائِقَ";
            }
            if (mi >= 11 && mi <= 19) {
                return "وَ" + DIAC_MIN_TEENS[mi] + " دَقِيقَةً";
            }
            
            int ones = mi % 10;
            int tens = mi / 10;
            if (tens >= 2 && tens <= 5) {
                if (ones == 0) {
                    return "وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً";
                } else if (ones == 1) {
                    return "وَإِحْدَى وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً";
                } else if (ones == 2) {
                    return "وَاثْنَتَانِ وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً";
                } else {
                    return "وَ" + DIAC_MIN_COMPOUND_ONES[ones] + " وَ" + DIAC_MIN_TENS[tens] + " دَقِيقَةً";
                }
            }
            return "";
        }
    
        private static String xTimes(String t) {
            if (t == null) return null;
            
            // 1. Colon Time (e.g. 11:00, 11:15, 11:30, 11:11, الساعة 11:00)
            Matcher m = PAT_TIME_COLON_ARNORM.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                int h = Integer.parseInt(m.group(1));
                int mi = Integer.parseInt(m.group(2));
                int sec = m.group(3) != null ? Integer.parseInt(m.group(3)) : -1;
                String periodStr = m.group(4);
    
                if (h < 0 || h > 23 || mi < 0 || mi > 59) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                    continue;
                }
    
                String period = "";
                if (periodStr != null) {
                    String pLower = periodStr.toLowerCase();
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً";
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً";
                    }
                }
    
                int hh = h;
                if (h == 0) {
                    hh = 12;
                    if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ";
                } else if (h == 12) {
                    hh = 12;
                    if (period.isEmpty()) period = "ظُهْراً";
                } else if (h > 12) {
                    hh = h - 12;
                    if (period.isEmpty()) period = "مَسَاءً";
                } else if (h >= 1 && h < 12) {
                    hh = h;
                    if (period.isEmpty() && m.group(1).startsWith("0")) {
                        period = "صَبَاحاً";
                    }
                }
    
                String hourWord = (hh >= 1 && hh <= 12) ? DIAC_ORDINAL_HOURS[hh] : String.valueOf(hh);
                String base = hourWord;
    
                String minWord = getDiacMinute(mi);
                if (!minWord.isEmpty()) {
                    minWord = " " + minWord;
                }
    
                String secWord = "";
                if (sec > 0) {
                    secWord = " وَ" + cardinal(sec) + " ثَانِيَةً";
                }
    
                String out = base + minWord + secWord + (period.isEmpty() ? "" : (" " + period));
                m.appendReplacement(sb, Matcher.quoteReplacement(out));
            }
            m.appendTail(sb);
            t = sb.toString();
    
            // 2. Explicit "الساعة X" Pattern (e.g. الساعة 11, الساعة 11 صباحاً)
            Matcher m2 = PAT_TIME_SINGLE_ARNORM.matcher(t);
            StringBuffer sb2 = new StringBuffer();
            while (m2.find()) {
                int h = Integer.parseInt(m2.group(1));
                String periodStr = m2.group(2);
    
                if (h < 1 || h > 24) {
                    m2.appendReplacement(sb2, Matcher.quoteReplacement(m2.group(0)));
                    continue;
                }
    
                String period = "";
                if (periodStr != null) {
                    String pLower = periodStr.toLowerCase();
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً";
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً";
                    }
                }
    
                int hh = h;
                if (h == 24 || h == 0) {
                    hh = 12;
                    if (period.isEmpty()) period = "مُنْتَصَفِ اللَّيْلِ";
                } else if (h == 12) {
                    hh = 12;
                    if (period.isEmpty()) period = "ظُهْراً";
                } else if (h > 12) {
                    hh = h - 12;
                    if (period.isEmpty()) period = "مَسَاءً";
                } else {
                    hh = h;
                }
    
                String hourWord = (hh >= 1 && hh <= 12) ? DIAC_ORDINAL_HOURS[hh] : String.valueOf(hh);
                String out = "السَّاعَةُ " + hourWord + (period.isEmpty() ? "" : (" " + period));
                m2.appendReplacement(sb2, Matcher.quoteReplacement(out));
            }
            m2.appendTail(sb2);
            t = sb2.toString();

            // 3. Standalone hour with period (e.g. 11 صباحاً, 11 ص, 11 مساءً, 11 م)
            Matcher m3 = PAT_TIME_STANDALONE_ARNORM.matcher(t);
            StringBuffer sb3 = new StringBuffer();
            while (m3.find()) {
                int h = Integer.parseInt(m3.group(1));
                String periodStr = m3.group(2);
                String period = "";
                if (periodStr != null) {
                    String pLower = periodStr.toLowerCase();
                    if (pLower.contains("ص") || pLower.contains("am")) {
                        period = "صَبَاحاً";
                    } else if (pLower.contains("م") || pLower.contains("pm")) {
                        period = "مَسَاءً";
                    }
                }
                String hourWord = (h >= 1 && h <= 12) ? DIAC_ORDINAL_HOURS[h] : String.valueOf(h);
                String out = hourWord + (period.isEmpty() ? "" : (" " + period));
                m3.appendReplacement(sb3, Matcher.quoteReplacement(out));
            }
            m3.appendTail(sb3);
            return sb3.toString();
        }
    
        private static String xCurrency(String t) {
            for (CompiledCurrency cc : COMPILED_CURRENCIES) {
                Matcher m1 = cc.patternPost.matcher(t);
                StringBuffer sb = new StringBuffer();
                while (m1.find()) m1.appendReplacement(sb, Matcher.quoteReplacement(amountWords(m1.group(1), cc.unit)));
                m1.appendTail(sb);
                t = sb.toString();
                if (cc.patternPre != null) {
                    Matcher m2 = cc.patternPre.matcher(t);
                    StringBuffer sb2 = new StringBuffer();
                    while (m2.find()) m2.appendReplacement(sb2, Matcher.quoteReplacement(amountWords(m2.group(1), cc.unit)));
                    m2.appendTail(sb2);
                    t = sb2.toString();
                }
            }
            return t;
        }
    
        private static String xPercent(String t) {
            Matcher m = PAT_PERCENT.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(cardinalDec(m.group(1)) + " بِالْمِئَةِ"));
            m.appendTail(sb); t = sb.toString();
            Matcher m2 = PAT_PERMILLE.matcher(t);
            StringBuffer sb2 = new StringBuffer();
            while (m2.find()) m2.appendReplacement(sb2, Matcher.quoteReplacement(cardinalDec(m2.group(1)) + " بِالْأَلْفِ"));
            m2.appendTail(sb2);
            return sb2.toString();
        }
    
        private static String xMath(String t) {
            t = PAT_MATH_PLUS.matcher(t).replaceAll(" زَائِدٌ ");
            t = PAT_MATH_MINUS.matcher(t).replaceAll(" نَاقِصٌ ");
            t = PAT_MATH_MUL.matcher(t).replaceAll(" ضَرْبٌ ");
            t = PAT_MATH_DIV.matcher(t).replaceAll(" قِسْمَةٌ ");
            t = PAT_MATH_EQ.matcher(t).replaceAll(" يُسَاوِي ");
            return t;
        }
    
        private static String xPhone(String t) {
            Matcher m = PAT_PHONE.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String s = m.group(0);
                boolean plus = s.startsWith("+");
                String digits = s.replaceAll("\\D", "");
                String out = spellDigits(digits);
                m.appendReplacement(sb, Matcher.quoteReplacement(plus ? ("زَائِدٌ " + out) : out));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    
        private static String xOrdinals(String t) {
            Matcher m = PAT_ORDINALS.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(ordinalDefinite(Integer.parseInt(m.group(1)), false)));
            m.appendTail(sb);
            return sb.toString();
        }
    
        private static String xRoman(String t) {
            Matcher m = PAT_ROMAN.matcher(t);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String tok = m.group(0);
                String rep = tok;
                for (String[] rv : ROMAN) { if (tok.equals(rv[0])) { rep = ordinalDefinite(Integer.parseInt(rv[1]), false); break; } }
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            return sb.toString();
        }
    
        private static String xNumbers(String t) {
            // 1. Clean and expand v/V prefixes in versions (e.g. v4.0 -> الإصدار 4.0)
            t = PAT_VERSION_CLEAN.matcher(t).replaceAll("$1 $2");
            t = PAT_VERSION_V.matcher(t).replaceAll("الإِصْدَارُ $1");
 
            // 2. Expand multi-dot version numbers (e.g. 1.0.0, 2.24.1, 4.0.1.2)
            Matcher mv = PAT_VERSION_MULTIDOT.matcher(t);
            StringBuffer sv = new StringBuffer();
            while (mv.find()) {
                String raw = mv.group(1);
                String[] parts = raw.split("\\.");
                StringBuilder sbv = new StringBuilder();
                for (int i = 0; i < parts.length; i++) {
                    if (i > 0) sbv.append(" نُقْطَةٌ ");
                    sbv.append(cardinal(parts[i]));
                }
                mv.appendReplacement(sv, Matcher.quoteReplacement(sbv.toString()));
            }
            mv.appendTail(sv);
            t = sv.toString();
 
            // 3. Expand single-dot/comma decimal numbers (e.g. 4.7, 4.0, 14.0, 3.5, 4٫7, 4,7, ٤.٧)
            Matcher md = PAT_DECIMAL.matcher(t);
            StringBuffer s1 = new StringBuffer();
            while (md.find()) md.appendReplacement(s1, Matcher.quoteReplacement(cardinalDec(md.group(1), md.group(2))));
            md.appendTail(s1);
            t = s1.toString();
 
            // 4. Expand integer numbers
            Matcher mi = PAT_INTEGER.matcher(t);
            StringBuffer s2 = new StringBuffer();
            while (mi.find()) mi.appendReplacement(s2, Matcher.quoteReplacement(cardinal(mi.group(0))));
            mi.appendTail(s2);
            return s2.toString();
        }
    
        private static String xSymbols(String t) {
            for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
                if (t.contains(e.getKey())) t = t.replace(e.getKey(), " " + e.getValue() + " ");
            }
            return t;
        }
    
        private static String xAbbrev(String t) {
            for (CompiledAbbrev ca : COMPILED_ABBREV) {
                t = ca.pattern.matcher(t).replaceAll(ca.replacement);
            }
            return t;
        }
    
        public static String normalize(String text) {
            if (text == null || text.trim().isEmpty()) return text;
            try {
                String t = toAsciiDigits(text);
                t = xAbbrev(t);

                boolean hasDigits = false;
                for (int i = 0; i < t.length(); i++) {
                    char c = t.charAt(i);
                    if (c >= '0' && c <= '9') {
                        hasDigits = true;
                        break;
                    }
                }

                if (hasDigits) {
                    t = xDates(t);
                    t = xTimes(t);
                    t = xCurrency(t);
                    t = xPercent(t);
                    t = xMath(t);
                    t = xOrdinals(t);
                    t = xPhone(t);
                    t = xNumbers(t);
                }

                t = xRoman(t);
                t = PAT_MULTI_SPACE.matcher(t).replaceAll(" ").trim();
                return t.isEmpty() ? text : t;
            } catch (Exception e) {
                return text;
            }
        }
    }
}
