# -*- coding: utf-8 -*-
"""Unified Dictionary Manager for RHVoice Arabic NVDA Addon.

Ported from org.pbt.rh.ar.DictionaryManager (Java).
Consolidates:
1. Default Dictionary (Default-dic.json): chars, diacritics, symbols, currencies, abbreviations, phrase rules.
2. Emoji Dictionary (emojis.json): high-performance prefix-tree (Trie) emoji replacement.
3. User Custom Dictionary (user-dic.json): user custom pronunciations with flexible Arabic regex matching.
"""
import os
import json
import re

_here = os.path.dirname(os.path.abspath(__file__))
_DICT_DIR = os.path.join(_here, "Dictionaries")
_DEFAULT_DIC_FILE = os.path.join(_DICT_DIR, "Default-dic.json")
_EMOJIS_FILE = os.path.join(_DICT_DIR, "emojis.json")

AR_LETTER = r"[\u0621-\u064A\u0670\u0671]"
TASHKEEL_PATTERN = r"[\u064B-\u065F\u0670\u0640]*"
AR_ANY_PRECEDING = r"[\u0621-\u064A\u0670\u0671\u064B-\u065F\u0640]"

class CompiledRule:
    __slots__ = ("original", "replacement", "pattern")
    def __init__(self, original, replacement, pattern):
        self.original = original
        self.replacement = replacement
        self.pattern = pattern

class TrieNode:
    __slots__ = ("children", "replacement")
    def __init__(self):
        self.children = {}
        self.replacement = None

class EmojiTrie:
    def __init__(self):
        self.root = TrieNode()

    def insert(self, emoji, name):
        if not emoji or not name:
            return
        node = self.root
        for ch in emoji:
            if ch not in node.children:
                node.children[ch] = TrieNode()
            node = node.children[ch]
        node.replacement = name

    def replace_all(self, text):
        if not text:
            return text
        result = []
        n = len(text)
        i = 0
        while i < n:
            node = self.root
            match_len = 0
            match_repl = None
            j = i
            while j < n:
                ch = text[j]
                if ch not in node.children:
                    break
                node = node.children[ch]
                j += 1
                if node.replacement is not None:
                    match_len = j - i
                    match_repl = node.replacement

            if match_repl is not None:
                if result and not result[-1].endswith(" "):
                    result.append(" ")
                result.append(match_repl)
                if j < n and text[j] != " ":
                    result.append(" ")
                i += match_len
            else:
                result.append(text[i])
                i += 1
        return "".join(result)


_is_default_loaded = False
_char_names = {}
_diacritic_names = {}
_symbols = {}
_currencies = {}
_abbreviations = {}
_phrase_rules = {}
_default_compiled_rules = []
_emoji_trie = EmojiTrie()
_emoji_map = {}
_user_compiled_rules = []
_user_dic_path = None


def is_genuine_emoji_or_flag(cp):
    if not cp:
        return False
    for ch in cp:
        code = ord(ch)
        if 0x1F1E6 <= code <= 0x1F1FF:
            return True
        if code in (0x1F3F4, 0x1F3F3):
            return True
        if (0x1F300 <= code <= 0x1FAFF) or (0x1F000 <= code <= 0x1F2FF) or \
           (0x2600 <= code <= 0x27BF) or \
           code in (0x231A, 0x231B, 0x2B50, 0x2B55, 0x203C, 0x2049, 0x2122, 0x2139, 0x2934, 0x2935, 0x3030, 0x303D, 0x3297, 0x3299) or \
           (0x23E9 <= code <= 0x23FA):
            return True
    return False


def build_flexible_regex(orig):
    if not orig or not orig.strip():
        return None
    clean = re.sub(r"[\u064B-\u065F\u0670\u0640]", "", orig.strip())
    if not clean:
        return None

    sb = []
    has_arabic = False
    first = clean[0]
    first_code = ord(first)
    if (0x0600 <= first_code <= 0x06FF) or first in ('\u0670', '\u0671'):
        sb.append(f"(?<!{AR_ANY_PRECEDING})")
        has_arabic = True
    elif first.isalnum() or first == '_':
        sb.append(r"(?<![a-zA-Z0-9_])")

    for ch in clean:
        if ch == ' ':
            sb.append(r"\s+")
        elif 0x0600 <= ord(ch) <= 0x06FF:
            has_arabic = True
            if ch in ('ا', 'أ', 'إ', 'ٱ'):
                sb.append(f"[اأإٱ]{TASHKEEL_PATTERN}")
            elif ch == 'آ':
                sb.append(f"[آ]{TASHKEEL_PATTERN}")
            elif ch == 'ه':
                sb.append(f"[ه]{TASHKEEL_PATTERN}")
            elif ch == 'ة':
                sb.append(f"[ة]{TASHKEEL_PATTERN}")
            elif ch in ('ي', 'ى'):
                sb.append(f"[يى]{TASHKEEL_PATTERN}")
            elif ch in ('و', 'ؤ'):
                sb.append(f"[وؤ]{TASHKEEL_PATTERN}")
            elif ch in ('ئ', 'ء'):
                sb.append(f"[ئء]{TASHKEEL_PATTERN}")
            else:
                sb.append(f"{re.escape(ch)}{TASHKEEL_PATTERN}")
        else:
            sb.append(re.escape(ch))

    last = clean[-1]
    last_code = ord(last)
    if (0x0600 <= last_code <= 0x06FF) or last in ('\u0670', '\u0671'):
        sb.append(f"(?!{TASHKEEL_PATTERN}{AR_LETTER})")
    elif last.isalnum() or last == '_':
        sb.append(r"(?![a-zA-Z0-9_])")

    flags = 0 if has_arabic else re.IGNORECASE
    try:
        return re.compile("".join(sb), flags)
    except Exception:
        return None


def load_default():
    global _is_default_loaded, _char_names, _diacritic_names, _symbols
    global _currencies, _abbreviations, _phrase_rules, _default_compiled_rules
    global _emoji_trie, _emoji_map

    if _is_default_loaded:
        return True

    # 1. Load Default-dic.json (array of {cp, tts, type})
    if os.path.exists(_DEFAULT_DIC_FILE):
        try:
            with open(_DEFAULT_DIC_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)

            rules_to_compile = []
            if isinstance(data, list):
                for item in data:
                    cp = item.get("cp", "")
                    tts = item.get("tts", "")
                    type_str = item.get("type", "char")
                    if not cp or not tts:
                        continue

                    if type_str in ("phrase", "word"):
                        _phrase_rules[cp] = tts
                        clean_cp = re.sub(r"[\u064B-\u065F\u0670\u0640]", "", cp).strip()
                        if len(clean_cp) > 1 and cp != tts:
                            rules_to_compile.append((cp, tts))
                    elif type_str == "abbreviation":
                        _abbreviations[cp] = tts
                    elif type_str == "currency":
                        _currencies[cp] = tts
                    elif type_str == "symbol":
                        _symbols[cp] = tts
                    elif type_str == "diacritic":
                        _diacritic_names[cp] = tts
                    else:
                        _char_names[cp] = tts
            elif isinstance(data, dict):
                _char_names = data.get("char_names", {})
                _diacritic_names = data.get("diacritic_names", {})
                _symbols = data.get("symbols", {})
                _currencies = data.get("currencies", {})
                _abbreviations = data.get("abbreviations", {})
                _phrase_rules = data.get("phrase_rules", {})
                for k, v in _phrase_rules.items():
                    clean_k = re.sub(r"[\u064B-\u065F\u0670\u0640]", "", k).strip()
                    if len(clean_k) > 1 and k != v:
                        rules_to_compile.append((k, v))

            rules_to_compile.sort(key=lambda x: len(x[0].strip()), reverse=True)
            _default_compiled_rules = []
            for cp, tts in rules_to_compile:
                pat = build_flexible_regex(cp)
                if pat:
                    _default_compiled_rules.append(CompiledRule(cp, tts, pat))

        except Exception:
            pass

    # 2. Load emojis.json
    if os.path.exists(_EMOJIS_FILE):
        try:
            with open(_EMOJIS_FILE, "r", encoding="utf-8") as f:
                emojis_data = json.load(f)

            _emoji_trie = EmojiTrie()
            _emoji_map = {}
            if isinstance(emojis_data, list):
                for item in emojis_data:
                    cp = item.get("cp", "")
                    tts = item.get("tts", "")
                    if cp and tts:
                        _emoji_map[cp] = tts
                        if is_genuine_emoji_or_flag(cp):
                            _emoji_trie.insert(cp, tts)
            elif isinstance(emojis_data, dict):
                _emoji_map = emojis_data
                for cp, tts in emojis_data.items():
                    if cp and tts:
                        if is_genuine_emoji_or_flag(cp):
                            _emoji_trie.insert(cp, tts)
        except Exception:
            pass

    _is_default_loaded = True
    return True


def load_user_dictionary(config_dir):
    global _user_compiled_rules, _user_dic_path
    if not config_dir:
        return
    _user_dic_path = os.path.join(config_dir, "user-dic.json")
    if not os.path.exists(_user_dic_path):
        _user_compiled_rules = []
        return
    try:
        with open(_user_dic_path, "r", encoding="utf-8") as f:
            entries = json.load(f)
        rules = []
        entries_sorted = sorted(entries, key=lambda e: len(e.get("original", "")), reverse=True)
        for entry in entries_sorted:
            orig = entry.get("original", "").strip()
            repl = entry.get("replacement", "").strip()
            if orig and repl:
                pat = build_flexible_regex(orig)
                if pat:
                    rules.append(CompiledRule(orig, repl, pat))
        _user_compiled_rules = rules
    except Exception:
        _user_compiled_rules = []


def get_char_names():
    load_default()
    return _char_names

def get_diacritic_names():
    load_default()
    return _diacritic_names

def get_symbols():
    load_default()
    return _symbols

def get_currencies():
    load_default()
    return _currencies

def get_abbreviations():
    load_default()
    return _abbreviations

def get_phrase_rules():
    load_default()
    return _phrase_rules

def get_emoji_or_symbol_name(token):
    load_default()
    if not token:
        return None
    if token in _emoji_map:
        return _emoji_map[token]
    if token in _symbols:
        return _symbols[token]
    return None

def replace_emojis(text):
    load_default()
    if not text:
        return text
    return _emoji_trie.replace_all(text)

def apply_default_rules(text):
    load_default()
    if not text or not text.strip():
        return text
    res = text
    for rule in _default_compiled_rules:
        res = rule.pattern.sub(rule.replacement, res)
    return res

def apply_user_dictionary(text):
    if not text or not text.strip() or not _user_compiled_rules:
        return text
    res = text
    for rule in _user_compiled_rules:
        res = rule.pattern.sub(rule.replacement, res)
    return res
