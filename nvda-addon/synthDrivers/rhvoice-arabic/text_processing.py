# -*- coding: utf-8 -*-
"""Arabic Text Pre-Processing and Neural Diacritization Pipeline.
Ported from org.pbt.rh.ar (Java) and text_processing.py.
Consolidates:;1. DictionaryManager default & user rules application.
2. High-performance Emoji replacement.
3. ArNorm / LatinTransliterator normalization.
4. ONNX Neural Diacritization (Shikkeel / Rawi).
5. User Tashkeel Merging (merge_user_diacritics).
6. Post-processing: fix_naa_pronoun, dedupe_tanween, fix_bare_hamza_vowel, swap_tanween_fatha_alif, sukoonize_end_of_sentence.
7. Hamza restoration algorithm is completely removed as requested.
"""
import os
import re
import json
import unicodedata

try:
    from . import dictionary_manager as dict_mgr
    from . import ar_textnorm
except Exception:
    import dictionary_manager as dict_mgr
    import ar_textnorm

_DIAC_RE = re.compile(r'[\u064B-\u0652\u0653\u0670]')

def _parse_chars_with_diac(text):
    out = []
    i = 0
    length = len(text)
    while i < length:
        base = text[i]
        i += 1
        diac = []
        while i < length and _DIAC_RE.match(text[i]):
            diac.append(text[i])
            i += 1
        out.append((base, ''.join(diac)))
    return out

def merge_user_diacritics(original_text, model_diacritized):
    if not original_text or not model_diacritized:
        return model_diacritized

    orig_list = _parse_chars_with_diac(original_text)
    model_list = _parse_chars_with_diac(model_diacritized)

    if len(orig_list) != len(model_list):
        o_words = original_text.split(' ')
        m_words = model_diacritized.split(' ')
        if len(o_words) == len(m_words):
            return ' '.join(merge_user_diacritics(ow, mw) for ow, mw in zip(o_words, m_words))
        return model_diacritized

    merged_pairs = []
    for (obase, odiac), (mbase, mdiac) in zip(orig_list, model_list):
        merged_pairs.append((obase, odiac if odiac else mdiac))

    out = []
    for base, diac in merged_pairs:
        out.append(base)
        if diac:
            out.append(diac)

    return unicodedata.normalize('NFC', ''.join(out))


class ShikkeelDiacritizer:
    def __init__(self, model_path, vocab_path, output_vocab_path, pad_len=450):
        import numpy as np
        import onnxruntime as ort
        
        self.pad_len = pad_len

        with open(vocab_path, "r", encoding="utf-8") as f:
            self.vocab = json.load(f)

        with open(output_vocab_path, "r", encoding="utf-8") as f:
            output_vocab = json.load(f)

        self.class_map = {int(k): v for k, v in output_vocab.items()}
        self.class_map.setdefault(3, "")
        self.class_map.setdefault(17, "\u064b")
        
        max_class = max(self.class_map.keys()) + 1
        self._class_array = [""] * max_class
        for k, v in self.class_map.items():
            if v in ("<PAD>", "<UNK>"):
                self._class_array[k] = ""
            else:
                self._class_array[k] = v

        sess_options = ort.SessionOptions()
        sess_options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
        sess_options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
        sess_options.intra_op_num_threads = 1
        sess_options.inter_op_num_threads = 1

        self.session = ort.InferenceSession(
            model_path,
            sess_options,
            providers=["CPUExecutionProvider"],
        )
        self._unk_id = self.vocab.get("<UNK>", 1)

    def _indices_for_chunk(self, text):
        import numpy as np
        unk_id = self._unk_id
        indices = [self.vocab.get(char, unk_id) for char in text]
        padded_len = max(self.pad_len, len(indices))
        indices.extend([0] * (padded_len - len(indices)))
        return np.asarray(indices, dtype=np.int64).reshape(1, -1)

    def diacritize_chunk(self, text):
        import numpy as np
        if not text:
            return ""

        inputs = self._indices_for_chunk(text)
        logits = self.session.run(["outputs"], {"inputs": inputs})[0][0, :len(text)]
        pred_classes = np.argmax(logits, axis=-1)

        class_arr = self._class_array
        result_parts = []
        for i, char in enumerate(text):
            result_parts.append(char)
            cls_id = int(pred_classes[i])
            if cls_id < len(class_arr):
                result_parts.append(class_arr[cls_id])
        
        return "".join(result_parts)

    def _split_line(self, line, limit=400):
        if len(line) <= limit:
            return [line]
        chunks, current_chunk, current_len = [], [], 0
        for word in line.split(" "):
            if current_chunk and current_len + len(word) + 1 > limit:
                chunks.append(" ".join(current_chunk))
                current_chunk, current_len = [word], len(word)
            else:
                current_chunk.append(word)
                current_len += len(word) + 1
        if current_chunk:
            chunks.append(" ".join(current_chunk))
        return chunks

    def __call__(self, text: str) -> str:
        if not text or not text.strip():
            return text
        bewe = "".join(
            c for c in unicodedata.normalize("NFD", text)
            if unicodedata.category(c) != "Mn" and unicodedata.category(c) != "So"
        )
        if not bewe:
            return text
            
        return "\n".join(
            " ".join(self.diacritize_chunk(c) for c in self._split_line(line))
            for line in bewe.split("\n")
        )


def get_single_char_name(text):
    return ar_textnorm.get_single_char_name(text)

def normalize_text(text, is_single_char_request=False):
    return ar_textnorm.normalize(text, is_single_char_request)

def is_pre_diacritized_spelling(text):
    return ar_textnorm.is_pre_diacritized_spelling(text)

def swap_shadda_harakah(text):
    return ar_textnorm.swap_shadda_harakah(text)

def shadda_before_harakah(text):
    return ar_textnorm.shadda_before_harakah(text)

def harakah_before_shadda(text):
    return ar_textnorm.harakah_before_shadda(text)

def process_arabic_text(text, diacritizer=None, read_emojis=True, pause_sukoon=True, is_single_char_request=False):
    """Complete synthesis pre-processing pipeline matching Android RHVoice-ARABIC.
    1. Apply User Dictionary (user-dic.json).
    2. Apply Default Dictionary Rules (Default-dic.json).
    3. Single-character check.
    4. Emoji & symbol replacement (emojis.json).
    5. Single-character check (post-emoji).
    6. ArNorm / Latin normalization (numbers, dates, times, currencies, IT words).
    7. Neural Tashkeel + user tashkeel merge + post-processing (naa fix, dedupe tanween, fix bare hamza vowel, swap tanween-fatha-alif, pause sukoon).
    8. Hamza restoration algorithm is completely removed as requested.
    """
    if not isinstance(text, str) or not text.strip():
        return text

    # 1. User custom dictionary
    text = dict_mgr.apply_user_dictionary(text)

    # 2. Default dictionary phrase rules
    text = dict_mgr.apply_default_rules(text)

    # 3. Single-character exploration check
    if is_single_char_request or len(text.strip()) == 1:
        single = ar_textnorm.get_single_char_name(text)
        if single:
            return single

    # 4. Emoji replacement if enabled
    emoji_processed = dict_mgr.replace_emojis(text) if read_emojis else text

    # 5. Single-character exploration check post-emoji
    if is_single_char_request or len(emoji_processed.strip()) == 1:
        single = ar_textnorm.get_single_char_name(emoji_processed)
        if single:
            return single

    # 6. Pre-diacritization normalization
    norm = ar_textnorm.normalize(emoji_processed, is_single_char_request=is_single_char_request)
    if ar_textnorm.is_pre_diacritized_spelling(norm):
        return norm

    # 7. Neural Diacritization
    has_arabic = bool(re.search(r'[\u0600-\u06FF]', norm))
    if has_arabic and len(norm.strip()) > 2 and diacritizer:
        try:
            diacritized = diacritizer(norm)
            merged = merge_user_diacritics(norm, diacritized)
            merged = ar_textnorm.fix_naa_pronoun(norm, merged)
            merged = ar_textnorm.dedupe_tanween(merged)
            merged = ar_textnorm.fix_bare_hamza_vowel(merged)
            merged = ar_textnorm.swap_tanween_fatha_alif(merged)
            if pause_sukoon:
                merged = ar_textnorm.sukoonize_end_of_sentence(merged)
            return merged
        except Exception:
            pass

    # 8. Fallback
    if pause_sukoon:
        norm = ar_textnorm.sukoonize_end_of_sentence(norm)
    norm = ar_textnorm.swap_tanween_fatha_alif(norm)
    return norm
