package org.pbt.rh.ar;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Unified Dictionary Management Engine for RHVoice Arabic.
 * Consolidates all dictionary subsystems:
 * 1. Default Dictionary (Assets: Dictionaries/Default-dic.json) - Chars, Diacritics, Symbols, Currencies, Abbreviations, Phrase Rules.
 * 2. Custom User Dictionary (Files: user_dictionary.json) - User custom pronunciations & flexible Arabic regex matching.
 * 3. Emoji Dictionary (Assets: Dictionaries/emojis.json) - High-performance prefix-tree (Trie) emoji replacement.
 */
public final class DictionaryManager {

    private static final String TAG = "DictionaryManager";
    private static final String DEFAULT_DIC_ASSET = "Dictionaries/Default-dic.json";
    private static final String EMOJI_ASSET = "Dictionaries/emojis.json";
    private static final String USER_DIC_FILE = "user-dic.json";
    private static final String USER_DIC_DIR = "Dictionaries";

    public static final String PREF_READ_EMOJIS = "read_emojis";

    public static class CompiledRule {
        final String original;
        final String replacement;
        final Pattern pattern;
        final String quotedReplacement;
        final char filterChar;

        CompiledRule(String orig, String repl, Pattern pattern) {
            this.original = orig;
            this.replacement = repl;
            this.pattern = pattern;
            this.quotedReplacement = java.util.regex.Matcher.quoteReplacement(repl);

            char found = 0;
            String clean = orig.replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "").trim();
            for (int i = 0; i < clean.length(); i++) {
                char c = clean.charAt(i);
                if ("بتثجحخدذرزسشصضطظعغفقكلمنهة".indexOf(c) != -1) {
                    found = c;
                    break;
                }
            }
            this.filterChar = found;
        }
    }

    private static final Map<String, String> charNames = new HashMap<>();
    private static final Map<String, String> diacriticNames = new HashMap<>();
    private static final Map<String, String> symbols = new HashMap<>();
    private static final Map<String, String> currencies = new HashMap<>();
    private static final Map<String, String> abbreviations = new HashMap<>();
    private static final Map<String, String> phraseRules = new HashMap<>();
    private static final List<CompiledRule> defaultCompiledRules = new ArrayList<>();

    private static final String AR_LETTER = "[\\u0621-\\u064A\\u0670\\u0671]";
    private static final String TASHKEEL_PATTERN = "[\\u064B-\\u065F\\u0670\\u0640]*";
    private static final String AR_ANY_PRECEDING = "[\\u0621-\\u064A\\u0670\\u0671\\u064B-\\u065F\\u0640]";

    private static volatile boolean isDefaultLoaded = false;
    private static final Object defaultLock = new Object();

    public static Pattern buildFlexibleRegex(String orig) {
        if (orig == null || orig.trim().isEmpty()) return null;
        String cleanOrig = orig.trim().replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "");
        if (cleanOrig.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        boolean hasArabic = false;

        char firstChar = cleanOrig.charAt(0);
        if ((firstChar >= 0x0600 && firstChar <= 0x06FF) || firstChar == '\u0670' || firstChar == '\u0671') {
            sb.append("(?<!").append(AR_ANY_PRECEDING).append(")");
            hasArabic = true;
        } else if (Character.isLetterOrDigit(firstChar)) {
            sb.append("(?<![a-zA-Z0-9_])");
        }

        for (int i = 0; i < cleanOrig.length(); i++) {
            char ch = cleanOrig.charAt(i);
            if (ch == ' ') {
                sb.append("\\s+");
            } else if (ch >= 0x0600 && ch <= 0x06FF) {
                hasArabic = true;
                if (ch == 'ا' || ch == 'أ' || ch == 'إ' || ch == 'ٱ') {
                    sb.append("[اأإٱ]").append(TASHKEEL_PATTERN);
                } else if (ch == 'آ') {
                    sb.append("[آ]").append(TASHKEEL_PATTERN);
                } else if (ch == 'ه') {
                    sb.append("[ه]").append(TASHKEEL_PATTERN);
                } else if (ch == 'ة') {
                    sb.append("[ة]").append(TASHKEEL_PATTERN);
                } else if (ch == 'ي' || ch == 'ى') {
                    sb.append("[يى]").append(TASHKEEL_PATTERN);
                } else if (ch == 'و' || ch == 'ؤ') {
                    sb.append("[وؤ]").append(TASHKEEL_PATTERN);
                } else if (ch == 'ئ' || ch == 'ء') {
                    sb.append("[ئء]").append(TASHKEEL_PATTERN);
                } else {
                    sb.append(Pattern.quote(String.valueOf(ch))).append(TASHKEEL_PATTERN);
                }
            } else {
                sb.append(Pattern.quote(String.valueOf(ch)));
            }
        }

        char lastChar = cleanOrig.charAt(cleanOrig.length() - 1);
        if ((lastChar >= 0x0600 && lastChar <= 0x06FF) || lastChar == '\u0670' || lastChar == '\u0671') {
            sb.append("(?!").append(TASHKEEL_PATTERN).append(AR_LETTER).append(")");
        } else if (Character.isLetterOrDigit(lastChar)) {
            sb.append("(?![a-zA-Z0-9_])");
        }

        int flags = !hasArabic ? Pattern.CASE_INSENSITIVE : 0;
        try {
            return Pattern.compile(sb.toString(), flags);
        } catch (Exception e) {
            Log.e(TAG, "Failed to compile regex for: " + orig, e);
            return null;
        }
    }

    public static void loadDefault(Context context) {
        if (isDefaultLoaded || context == null) return;
        synchronized (defaultLock) {
            if (isDefaultLoaded) return;
            try (InputStream is = context.getAssets().open(DEFAULT_DIC_ASSET);
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }

                JSONArray arr = new JSONArray(sb.toString());
                int count = arr.length();
                List<Map.Entry<String, String>> rulesToCompile = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String cp = obj.optString("cp");
                    String tts = obj.optString("tts");
                    String type = obj.optString("type", "char");

                    if (cp == null || cp.isEmpty() || tts == null || tts.isEmpty()) continue;

                    switch (type) {
                        case "phrase":
                        case "word":
                            phraseRules.put(cp, tts);
                            String cleanCp = cp.replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "").trim();
                            if (cleanCp.length() > 1 && !cp.equals(tts)) {
                                rulesToCompile.add(new java.util.AbstractMap.SimpleEntry<>(cp, tts));
                            }
                            break;
                        case "abbreviation":
                            abbreviations.put(cp, tts);
                            break;
                        case "currency":
                            currencies.put(cp, tts);
                            break;
                        case "symbol":
                            symbols.put(cp, tts);
                            break;
                        case "diacritic":
                            diacriticNames.put(cp, tts);
                            break;
                        case "char":
                        default:
                            charNames.put(cp, tts);
                            break;
                    }
                }

                rulesToCompile.sort((a, b) -> Integer.compare(b.getKey().length(), a.getKey().length()));
                defaultCompiledRules.clear();
                for (Map.Entry<String, String> entry : rulesToCompile) {
                    Pattern p = buildFlexibleRegex(entry.getKey());
                    if (p != null) {
                        defaultCompiledRules.add(new CompiledRule(entry.getKey(), entry.getValue(), p));
                    }
                }

                isDefaultLoaded = true;
                Log.i(TAG, "Default dictionary loaded: " + defaultCompiledRules.size() + " active speech rules, " + count + " total entries.");
            } catch (Exception e) {
                Log.e(TAG, "Error loading Default-dic.json", e);
            }
        }
    }

    public static String applyDefaultRules(String text) {
        if (text == null || text.isEmpty() || !isDefaultLoaded || defaultCompiledRules.isEmpty()) {
            return text;
        }
        String result = text;
        for (CompiledRule rule : defaultCompiledRules) {
            if (rule.pattern != null) {
                if (rule.filterChar != 0 && result.indexOf(rule.filterChar) == -1) {
                    continue;
                }
                try {
                    result = rule.pattern.matcher(result).replaceAll(rule.quotedReplacement);
                } catch (Exception ignored) {}
            }
        }
        return result;
    }

    public static Map<String, String> getCharNames() { return charNames; }
    public static Map<String, String> getDiacriticNames() { return diacriticNames; }
    public static Map<String, String> getSymbols() { return symbols; }
    public static Map<String, String> getCurrencies() { return currencies; }
    public static Map<String, String> getAbbreviations() { return abbreviations; }
    public static Map<String, String> getPhraseRules() { return phraseRules; }
    public static boolean isDefaultLoaded() { return isDefaultLoaded; }

    // 2. EMOJI DICTIONARY SUBSYSTEM (TRIE)
    private static class TrieNode {
        Map<Character, TrieNode> children;
        String tts;
    }

    private static final TrieNode emojiRoot = new TrieNode();
    private static final Map<String, String> allEmojiAndSymbols = new HashMap<>();
    private static volatile boolean isEmojiLoaded = false;
    private static final Object emojiLock = new Object();

    public static void initEmojiAsync(Context context) {
        if (isEmojiLoaded) return;
        new Thread(() -> loadEmoji(context)).start();
    }

    /**
     * Determines whether a given character sequence represents a genuine Emoji
     * or a Country Flag (to be spoken in continuous speech when enabled), as
     * opposed to technical symbols, mathematical operators, geometric shapes,
     * currencies, or keyboard keys (which should ONLY be spoken in single-char
     * spelling or keyboard typing mode).
     */
    public static boolean isGenuineEmojiOrFlag(String cp) {
        if (cp == null || cp.isEmpty()) return false;
        
        // 1. Regional Indicator Symbols (Flags: U+1F1E6 to U+1F1FF) & Flag Bases
        for (int i = 0; i < cp.length(); ) {
            int code = cp.codePointAt(i);
            if (code >= 0x1F1E6 && code <= 0x1F1FF) return true;
            if (code == 0x1F3F4 || code == 0x1F3F3) return true; // Black/White/Rainbow flag bases
            i += Character.charCount(code);
        }

        // 2. Standard Unicode Emoji blocks:
        // - 1F300..1FAFF: Misc Symbols & Pictographs, Emoticons, Transport, Supplemental, Extended-A
        // - 1F000..1F2FF: Enclosed Ideographic, Playing Cards, Mahjong, etc.
        // - 2600..27BF: Misc Symbols & Dingbats (weather, zodiac, smileys, food, activities)
        // - Specific standard Unicode emojis: 231A, 231B, 23E9..23FA, 2B50, 2B55, 203C, 2049, 2122, 2139, 2934, 2935, 3030, 303D, 3297, 3299
        for (int i = 0; i < cp.length(); ) {
            int code = cp.codePointAt(i);
            if (code >= 0x1F300 && code <= 0x1FAFF) return true;
            if (code >= 0x1F000 && code <= 0x1F2FF) return true;
            if (code >= 0x2600 && code <= 0x27BF) return true;
            if (code == 0x231A || code == 0x231B || (code >= 0x23E9 && code <= 0x23FA)
                || code == 0x2B50 || code == 0x2B55 || code == 0x203C || code == 0x2049
                || code == 0x2122 || code == 0x2139 || code == 0x2934 || code == 0x2935
                || code == 0x3030 || code == 0x303D || code == 0x3297 || code == 0x3299) {
                return true;
            }
            i += Character.charCount(code);
        }
        return false;
    }

    public static void loadEmoji(Context context) {
        if (isEmojiLoaded || context == null) return;
        synchronized (emojiLock) {
            if (isEmojiLoaded) return;
            try (InputStream is = context.getAssets().open(EMOJI_ASSET);
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {

                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int read;
                while ((read = reader.read(buf)) != -1) {
                    sb.append(buf, 0, read);
                }

                JSONArray arr = new JSONArray(sb.toString());
                int count = arr.length();
                int emojiCount = 0;
                for (int i = 0; i < count; i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String cp = obj.optString("cp");
                    String tts = obj.optString("tts");
                    if (cp != null && !cp.isEmpty() && tts != null && !tts.isEmpty()) {
                        allEmojiAndSymbols.put(cp, tts);

                        if (isGenuineEmojiOrFlag(cp)) {
                            insertEmoji(cp, tts);
                            emojiCount++;
                        }
                    }
                }
                isEmojiLoaded = true;
                Log.i(TAG, "Emoji dictionary loaded: " + emojiCount + " continuous emojis/flags, " + count + " total symbols.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to load emojis.json", e);
            }
        }
    }

    private static void insertEmoji(String cp, String tts) {
        TrieNode current = emojiRoot;
        for (int i = 0; i < cp.length(); i++) {
            char ch = cp.charAt(i);
            if (current.children == null) {
                current.children = new HashMap<>();
            }
            TrieNode next = current.children.get(ch);
            if (next == null) {
                next = new TrieNode();
                current.children.put(ch, next);
            }
            current = next;
        }
        current.tts = tts;
    }

    public static String replaceEmojis(String text) {
        if (text == null || text.isEmpty() || !isEmojiLoaded) {
            return text;
        }

        StringBuilder sb = new StringBuilder(text.length() + 32);
        int i = 0;
        int len = text.length();

        while (i < len) {
            TrieNode current = emojiRoot;
            int matchedLen = 0;
            String matchedTts = null;

            for (int j = i; j < len; j++) {
                char ch = text.charAt(j);
                if (current.children == null || !current.children.containsKey(ch)) {
                    break;
                }
                current = current.children.get(ch);
                if (current.tts != null) {
                    matchedLen = j - i + 1;
                    matchedTts = current.tts;
                }
            }

            if (matchedLen > 0 && matchedTts != null) {
                sb.append(" ").append(matchedTts).append(" ");
                i += matchedLen;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }

        return sb.toString();
    }

    public static String getEmojiOrSymbolName(String text) {
        if (text == null || text.isEmpty()) return null;
        return allEmojiAndSymbols.get(text);
    }

    public static boolean isEmojiLoaded() { return isEmojiLoaded; }

    // 3. USER CUSTOM DICTIONARY SUBSYSTEM
    public static class UserEntry {
        private final String original;
        private final String replacement;

        public UserEntry(String original, String replacement) {
            this.original = original != null ? original.trim() : "";
            this.replacement = replacement != null ? replacement.trim() : "";
        }

        public String getOriginal() { return original; }
        public String getReplacement() { return replacement; }
    }

    public static class IndexedUserEntry {
        private final int index;
        private final UserEntry entry;

        public IndexedUserEntry(int index, UserEntry entry) {
            this.index = index;
            this.entry = entry;
        }

        public int getIndex() { return index; }
        public UserEntry getEntry() { return entry; }
    }

    public static final class UserDict {
        private final File file;
        private final List<UserEntry> entries = new ArrayList<>();
        private long lastLoadedModifiedTime = 0;

        public UserDict(Context context) {
            File dir = new File(context.getFilesDir(), USER_DIC_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            this.file = new File(dir, USER_DIC_FILE);
            File oldFile = new File(context.getFilesDir(), "user_dictionary.json");
            if (oldFile.exists() && !this.file.exists()) {
                oldFile.renameTo(this.file);
            }
            load();
        }

        public synchronized void checkAndReload() {
            if (file != null && file.exists()) {
                long currentModified = file.lastModified();
                if (currentModified != lastLoadedModifiedTime) {
                    load();
                }
            }
        }

        public synchronized void load() {
            entries.clear();
            if (file == null || !file.exists()) {
                lastLoadedModifiedTime = 0;
                return;
            }
            lastLoadedModifiedTime = file.lastModified();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                int read = fis.read(data);
                if (read <= 0) return;
                String json = new String(data, 0, read, StandardCharsets.UTF_8);
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String orig = obj.optString("original", "").trim();
                    String repl = obj.optString("replacement", "").trim();
                    if (!orig.isEmpty() && !repl.isEmpty()) {
                        entries.add(new UserEntry(orig, repl));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load user dictionary", e);
            } finally {
                rebuildCompiledRules();
            }
        }

        private synchronized void save() {
            try {
                JSONArray arr = new JSONArray();
                for (UserEntry entry : entries) {
                    JSONObject obj = new JSONObject();
                    obj.put("original", entry.getOriginal());
                    obj.put("replacement", entry.getReplacement());
                    arr.put(obj);
                }
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(arr.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                lastLoadedModifiedTime = file.lastModified();
            } catch (Exception e) {
                Log.e(TAG, "Failed to save user dictionary", e);
            } finally {
                rebuildCompiledRules();
            }
        }

        public synchronized List<UserEntry> getAll() {
            checkAndReload();
            return new ArrayList<>(entries);
        }

        public synchronized void add(String original, String replacement) {
            if (original == null || original.trim().isEmpty() || replacement == null || replacement.trim().isEmpty()) {
                return;
            }
            checkAndReload();
            entries.add(new UserEntry(original, replacement));
            save();
        }

        public synchronized void update(int index, String original, String replacement) {
            checkAndReload();
            if (index >= 0 && index < entries.size()) {
                if (original == null || original.trim().isEmpty() || replacement == null || replacement.trim().isEmpty()) {
                    return;
                }
                entries.set(index, new UserEntry(original, replacement));
                save();
            }
        }

        public synchronized void delete(int index) {
            checkAndReload();
            if (index >= 0 && index < entries.size()) {
                entries.remove(index);
                save();
            }
        }

        public synchronized void clearAll() {
            entries.clear();
            save();
        }

        public File getFile() {
            return file;
        }

        /**
         * Import entries from a JSON InputStream.
         * Validates format: must be a JSONArray of objects with "original"+"replacement" keys.
         * @return number of entries imported, or -1 if format is invalid.
         */
        public synchronized int importEntries(InputStream inputStream) {
            try {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[8192];
                int read;
                while ((read = inputStream.read(buf)) != -1) {
                    sb.append(new String(buf, 0, read, StandardCharsets.UTF_8));
                }
                String json = sb.toString().trim();
                if (!validateDictionaryJson(json)) {
                    return -1;
                }

                checkAndReload();
                JSONArray arr = new JSONArray(json);
                int imported = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String orig = obj.optString("original", obj.optString("cp", "")).trim();
                    String repl = obj.optString("replacement", obj.optString("tts", "")).trim();
                    if (!orig.isEmpty() && !repl.isEmpty()) {
                        boolean exists = false;
                        for (UserEntry e : entries) {
                            if (e.getOriginal().equals(orig) && e.getReplacement().equals(repl)) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            entries.add(new UserEntry(orig, repl));
                            imported++;
                        }
                    }
                }
                if (imported > 0) {
                    save();
                }
                return imported;
            } catch (Exception e) {
                Log.e(TAG, "Failed to import dictionary", e);
                return -1;
            }
        }

        /**
         * Validates that the JSON string is a valid dictionary format:
         * A JSONArray where each element has ("original" and "replacement") OR ("cp" and "tts") non-empty string keys.
         */
        public static boolean validateDictionaryJson(String json) {
            if (json == null || json.isEmpty()) return false;
            try {
                JSONArray arr = new JSONArray(json);
                if (arr.length() == 0) return false;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    boolean hasUserKeys = obj.has("original") && obj.has("replacement");
                    boolean hasDefaultKeys = obj.has("cp") && obj.has("tts");
                    if (!hasUserKeys && !hasDefaultKeys) {
                        return false;
                    }
                    String orig = obj.optString("original", obj.optString("cp", "")).trim();
                    String repl = obj.optString("replacement", obj.optString("tts", "")).trim();
                    if (orig.isEmpty() || repl.isEmpty()) {
                        return false;
                    }
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        private static class CompiledRule {
            final String original;
            final String replacement;
            final Pattern pattern;
            final String quotedReplacement;

            CompiledRule(String orig, String repl, Pattern pattern) {
                this.original = orig;
                this.replacement = repl;
                this.pattern = pattern;
                this.quotedReplacement = java.util.regex.Matcher.quoteReplacement(repl);
            }
        }

        private final List<CompiledRule> compiledRules = new ArrayList<>();

        public synchronized List<IndexedUserEntry> search(String query) {
            checkAndReload();
            List<IndexedUserEntry> result = new ArrayList<>();
            if (query == null || query.trim().isEmpty()) {
                for (int i = 0; i < entries.size(); i++) {
                    result.add(new IndexedUserEntry(i, entries.get(i)));
                }
                return result;
            }
            String q = query.trim().toLowerCase();
            for (int i = 0; i < entries.size(); i++) {
                UserEntry e = entries.get(i);
                if (e.getOriginal().toLowerCase().contains(q) || e.getReplacement().toLowerCase().contains(q)) {
                    result.add(new IndexedUserEntry(i, e));
                }
            }
            return result;
        }

        private void rebuildCompiledRules() {
            compiledRules.clear();
            if (entries.isEmpty()) return;

            List<UserEntry> sorted = new ArrayList<>(entries);
            Collections.sort(sorted, (a, b) -> Integer.compare(b.getOriginal().length(), a.getOriginal().length()));

            for (UserEntry entry : sorted) {
                String orig = entry.getOriginal();
                String repl = entry.getReplacement();
                if (orig.isEmpty() || repl.isEmpty()) continue;

                Pattern p = buildFlexibleRegex(orig);
                if (p != null) {
                    compiledRules.add(new CompiledRule(orig, repl, p));
                }
            }
        }

        public synchronized String applyReplacements(String text) {
            if (text == null || text.isEmpty()) {
                return text;
            }
            checkAndReload();
            if (compiledRules.isEmpty()) {
                return text;
            }

            String result = text;
            for (CompiledRule rule : compiledRules) {
                if (rule.pattern != null) {
                    try {
                        result = rule.pattern.matcher(result).replaceAll(rule.quotedReplacement);
                    } catch (Exception ignored) {}
                }
            }
            return result;
        }
    }
}
