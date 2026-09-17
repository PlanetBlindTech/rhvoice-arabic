# -*- coding: utf-8 -*-
import re
import unicodedata

try:
    from . import dictionary_manager as dict_mgr
except Exception:
    import dictionary_manager as dict_mgr

PF_MAP = {
    '\uFE70': 'ً', '\uFE71': 'ً', '\uFE72': 'ٌ', '\uFE73': 'ٌ',
    '\uFE74': 'ٍ', '\uFE75': 'ٍ', '\uFE76': 'َ', '\uFE77': 'َ',
    '\uFE78': 'ُ', '\uFE79': 'ُ', '\uFE7A': 'ِ', '\uFE7B': 'ِ',
    '\uFE7C': 'ّ', '\uFE7D': 'ّ', '\uFE7E': 'ْ', '\uFE7F': 'ْ',
    '\uFE80': 'ء', '\uFE81': 'آ', '\uFE82': 'آ', '\uFE83': 'أ',
    '\uFE84': 'أ', '\uFE85': 'ؤ', '\uFE86': 'ؤ', '\uFE87': 'إ',
    '\uFE88': 'إ', '\uFE89': 'ئ', '\uFE8A': 'ئ', '\uFE8B': 'ئ',
    '\uFE8C': 'ئ', '\uFE8D': 'ا', '\uFE8E': 'ا', '\uFE8F': 'ب',
    '\uFE90': 'ب', '\uFE91': 'ب', '\uFE92': 'ب', '\uFE93': 'ة',
    '\uFE94': 'ة', '\uFE95': 'ت', '\uFE96': 'ت', '\uFE97': 'ت',
    '\uFE98': 'ت', '\uFE99': 'ث', '\uFE9A': 'ث', '\uFE9B': 'ث',
    '\uFE9C': 'ث', '\uFE9D': 'ج', '\uFE9E': 'ج', '\uFE9F': 'ج',
    '\uFEA0': 'ج', '\uFEA1': 'ح', '\uFEA2': 'ح', '\uFEA3': 'ح',
    '\uFEA4': 'ح', '\uFEA5': 'خ', '\uFEA6': 'خ', '\uFEA7': 'خ',
    '\uFEA8': 'خ', '\uFEA9': 'د', '\uFEAA': 'د', '\uFEAB': 'ذ',
    '\uFEAC': 'ذ', '\uFEAD': 'ر', '\uFEAE': 'ر', '\uFEAF': 'ز',
    '\uFEB0': 'ز', '\uFEB1': 'س', '\uFEB2': 'س', '\uFEB3': 'س',
    '\uFEB4': 'س', '\uFEB5': 'ش', '\uFEB6': 'ش', '\uFEB7': 'ش',
    '\uFEB8': 'ش', '\uFEB9': 'ص', '\uFEBA': 'ص', '\uFEBB': 'ص',
    '\uFEBC': 'ص', '\uFEBD': 'ض', '\uFEBE': 'ض', '\uFEBF': 'ض',
    '\uFEC0': 'ض', '\uFEC1': 'ط', '\uFEC2': 'ط', '\uFEC3': 'ط',
    '\uFEC4': 'ط', '\uFEC5': 'ظ', '\uFEC6': 'ظ', '\uFEC7': 'ظ',
    '\uFEC8': 'ظ', '\uFEC9': 'ع', '\uFECA': 'ع', '\uFECB': 'ع',
    '\uFECC': 'ع', '\uFECD': 'غ', '\uFECE': 'غ', '\uFECF': 'غ',
    '\uFED0': 'غ', '\uFED1': 'ف', '\uFED2': 'ف', '\uFED3': 'ف',
    '\uFED4': 'ف', '\uFED5': 'ق', '\uFED6': 'ق', '\uFED7': 'ق',
    '\uFED8': 'ق', '\uFED9': 'ك', '\uFEDA': 'ك', '\uFEDB': 'ك',
    '\uFEDC': 'ك', '\uFEDD': 'ل', '\uFEDE': 'ل', '\uFEDF': 'ل',
    '\uFEE0': 'ل', '\uFEE1': 'م', '\uFEE2': 'م', '\uFEE3': 'م',
    '\uFEE4': 'م', '\uFEE5': 'ن', '\uFEE6': 'ن', '\uFEE7': 'ن',
    '\uFEE8': 'ن', '\uFEE9': 'ه', '\uFEEA': 'ه', '\uFEEB': 'ه',
    '\uFEEC': 'ه', '\uFEED': 'و', '\uFEEE': 'و', '\uFEEF': 'ى',
    '\uFEF0': 'ى', '\uFEF1': 'ي', '\uFEF2': 'ي', '\uFEF3': 'ي',
    '\uFEF4': 'ي', '\uFEF5': 'لآ', '\uFEF6': 'لآ', '\uFEF7': 'لأ',
    '\uFEF8': 'لأ', '\uFEF9': 'لإ', '\uFEFA': 'لإ', '\uFEFB': 'لا',
    '\uFEFC': 'لا', '\uFB50': 'ء', '\uFB51': 'ء', '\uFBFC': 'ي',
    '\uFBFD': 'ي', '\uFBFE': 'ي', '\uFBFF': 'ي', 'ک': 'ك', 'ی': 'ي', 'ۀ': 'ه'
}

SHADDA = '\u0651'
DIACRITIC_ONLY_RE = re.compile(r'^[\u064B-\u065F\u0670\u0640\u06D6-\u06ED]+$')
_DIAC_RE = re.compile(r'[\u064B-\u0652\u0653\u0670]')

def normalize_presentation_forms(text):
    if not text:
        return text
    sb = []
    for c in text:
        code = ord(c)
        if (0x200B <= code <= 0x200F) or (0x202A <= code <= 0x202E) or \
           (0x2060 <= code <= 0x2069) or code == 0xFEFF or code == 0x00AD:
            continue
        sb.append(PF_MAP.get(c, c))
    return ''.join(sb)

def fold_digits(text):
    if not text:
        return text
    sb = []
    for c in text:
        if '٠' <= c <= '٩':
            sb.append(chr(ord('0') + (ord(c) - ord('٠'))))
        elif '۰' <= c <= '۹':
            sb.append(chr(ord('0') + (ord(c) - ord('۰'))))
        else:
            sb.append(c)
    return ''.join(sb)

def is_pre_diacritized_spelling(text):
    if not text:
        return False
    char_names = dict_mgr.get_char_names()
    diac_names = dict_mgr.get_diacritic_names()
    symbols = dict_mgr.get_symbols()
    return (text in char_names.values()) or (text in diac_names.values()) or (text in symbols.values())

def name_lone_diacritics(marks):
    char_names = dict_mgr.get_char_names()
    diac_names = dict_mgr.get_diacritic_names()

    core = marks.replace('\u0640', '')
    if not core:
        if '\u0640' in diac_names:
            return diac_names['\u0640']
        if 'ـ' in char_names:
            return char_names['ـ']
        return 'كَشِيدَةٌ'

    if len(core) == 1:
        c = core[0]
        if c in diac_names:
            return diac_names[c]
        if c in char_names:
            return char_names[c]

    if len(core) == 2:
        c1, c2 = core[0], core[1]
        if c1 == SHADDA or c2 == SHADDA:
            vowel = c2 if c1 == SHADDA else c1
            if vowel == '\u064E': return 'شَدَّةٌ مَفْتُوحَةٌ'
            if vowel == '\u064F': return 'شَدَّةٌ مَضْمُومَةٌ'
            if vowel == '\u0650': return 'شَدَّةٌ مَكْسُورَةٌ'
            if vowel == '\u064B': return 'شَدَّةٌ مَعَ تَنْوِينِ فَتْحٍ'
            if vowel == '\u064C': return 'شَدَّةٌ مَعَ تَنْوِينِ ضَمٍّ'
            if vowel == '\u064D': return 'شَدَّةٌ مَعَ تَنْوِينِ كَسْرٍ'

    names = []
    for c in core:
        nm = diac_names.get(c) or char_names.get(c)
        if not nm:
            return None
        names.append(nm)
    return ' '.join(names)

def get_single_char_name(text):
    if not text:
        return None

    if text in (' ', '\u00A0') or (len(text) > 0 and not text.strip()):
        if '\n' in text: return 'سَطْرٌ جَدِيدٌ'
        if '\t' in text: return 'مَسَافَةٌ بَادِئَةٌ'
        return 'مَسَافَةٌ'

    t = fold_digits(text).strip()
    if not t:
        return None

    char_names = dict_mgr.get_char_names()
    symbols = dict_mgr.get_symbols()
    diac_names = dict_mgr.get_diacritic_names()

    if t in char_names: return char_names[t]
    if t in symbols: return symbols[t]
    if t in diac_names: return diac_names[t]

    if t in ('...', '..'): return 'عَلَامَةُ حَذْفٍ'
    if t in ('--', '——'): return 'شَرْطَةٌ طَوِيلَةٌ'

    if DIACRITIC_ONLY_RE.match(t):
        lone = name_lone_diacritics(t)
        if lone: return lone

    from_emoji = dict_mgr.get_emoji_or_symbol_name(t)
    if from_emoji: return from_emoji

    bare_trimmed = re.sub(r'[\u064B-\u0652\u0670]', '', t)
    if bare_trimmed and bare_trimmed in char_names:
        return char_names[bare_trimmed]

    return None

class CharWithDiac:
    __slots__ = ('base', 'diac')
    def __init__(self, base, diac=''):
        self.base = base
        self.diac = diac

def parse_chars_with_diac(word):
    pairs = []
    i = 0
    n = len(word)
    while i < n:
        base = word[i]
        i += 1
        diac = []
        while i < n and _DIAC_RE.match(word[i]):
            diac.append(word[i])
            i += 1
        pairs.append(CharWithDiac(base, ''.join(diac)))
    return pairs

def is_alif_char(c):
    return c in ('\u0627', '\u0649')

def is_tanween(c):
    return c in ('\u064B', '\u064C', '\u064D')

def contains_tanween(diac):
    return any(is_tanween(c) for c in diac)

def remove_tanween(diac):
    return ''.join(c for c in diac if not is_tanween(c))

def keep_only_tanween(diac):
    return ''.join(c for c in diac if is_tanween(c))

def remove_short_vowels(diac):
    return ''.join(c for c in diac if c not in ('\u064E', '\u064F', '\u0650'))

def sanitize_char_vowels(base, diac):
    if not diac:
        return diac
    vowels = [c for c in diac if c in ('\u064E', '\u064F', '\u0650')]
    has_shadda = (SHADDA in diac)
    tanweens = [c for c in diac if is_tanween(c)]
    has_sukun = ('\u0652' in diac)

    res = []
    if has_shadda:
        res.append(SHADDA)
    if tanweens:
        res.append(tanweens[-1])
    elif vowels:
        res.append(vowels[-1])
    elif has_sukun:
        res.append('\u0652')
    return ''.join(res)

def fix_bare_hamza_vowel(text):
    if not text:
        return text
    HAMZA_ABOVE = '\u0623'
    HAMZA_BELOW = '\u0625'
    FATHA = '\u064E'
    KASRA = '\u0650'

    words = text.split(' ')
    out_all = []
    for w, word in enumerate(words):
        pairs = parse_chars_with_diac(word)
        for i, cd in enumerate(pairs):
            if not cd.diac or cd.diac == str(SHADDA):
                is_word_initial = (i == 0)
                is_after_lam = (i >= 2 and pairs[i-1].base == 'ل' and pairs[i-2].base == 'ا')
                if is_word_initial or is_after_lam:
                    if cd.base == HAMZA_ABOVE:
                        cd.diac = cd.diac + FATHA
                    elif cd.base == HAMZA_BELOW:
                        cd.diac = cd.diac + KASRA
            cd.diac = sanitize_char_vowels(cd.base, cd.diac)
        wb = ''.join(cd.base + cd.diac for cd in pairs)
        out_all.append(wb)
    return unicodedata.normalize('NFC', ' '.join(out_all))

def dedupe_tanween(text):
    if not text:
        return text
    words = text.split(' ')
    out_all = []
    for word in words:
        pairs = parse_chars_with_diac(word)
        n = len(pairs)
        for i in range(n - 1):
            cur = pairs[i]
            nxt = pairs[i + 1]
            is_last_alif = (i + 1 == n - 1) and is_alif_char(nxt.base)
            if not is_last_alif:
                continue
            cons_t = contains_tanween(cur.diac)
            alif_t = contains_tanween(nxt.diac)
            if cons_t and alif_t:
                cur.diac = remove_tanween(cur.diac)
            elif cons_t and not alif_t:
                moved = keep_only_tanween(cur.diac)
                cur.diac = remove_tanween(cur.diac)
                nxt.diac = remove_short_vowels(nxt.diac) + moved

        for i in range(n - 1):
            cur = pairs[i]
            nxt = pairs[i + 1]
            if nxt.base == '\u0627' and i + 1 < n - 1:
                if not cur.diac or cur.diac == '\u0652':
                    cur.diac = '\u064E'
                elif '\u0651' in cur.diac and not any(v in cur.diac for v in ('\u064E', '\u064F', '\u0650')):
                    cur.diac = cur.diac + '\u064E'

        out_all.append(''.join(cd.base + cd.diac for cd in pairs))
    return unicodedata.normalize('NFC', ' '.join(out_all))

NAA_MARKS = '\u064B\u064C\u064D\u064E\u064F\u0650\u0651\u0652\u0670'
def is_naa_mark(c): return c in NAA_MARKS
def naa_bare(s): return ''.join(c for c in s if not is_naa_mark(c))

def fix_naa_word(o, d):
    NUN = '\u0646'
    ALIF = '\u0627'
    FATHA = '\u064E'
    SHADDA_CHAR = '\u0651'
    TANWEENS = '\u064B\u064C\u064D'

    ob = naa_bare(o)
    if not ob.endswith('\u0646\u0627'):
        return d

    chars = list(d)
    n = len(chars)
    ai = -1
    for k in range(n - 1, -1, -1):
        if chars[k] == ALIF:
            ai = k
            break
    if ai < 0:
        return d

    tail = ''.join(chars[k] for k in range(ai + 1, n) if not is_naa_mark(chars[k]))
    j = ai - 1
    while j >= 0 and is_naa_mark(chars[j]):
        j -= 1
    if j < 0:
        return d

    had_shadda = any(chars[k] == SHADDA_CHAR for k in range(j + 1, ai))
    nun_marks = (SHADDA_CHAR if had_shadda else '') + FATHA

    out = []
    if chars[j] != NUN:
        for k in range(0, j + 1):
            if chars[k] not in TANWEENS:
                out.append(chars[k])
        out.append(NUN)
        out.append(nun_marks)
        out.append(ALIF)
        out.append(tail)
        return ''.join(out)

    for k in range(0, j):
        if chars[k] not in TANWEENS:
            out.append(chars[k])
    out.append(NUN)
    out.append(nun_marks)
    out.append(ALIF)
    out.append(tail)
    return ''.join(out)

def fix_naa_pronoun(original, diacritized):
    if not original or not diacritized:
        return diacritized
    o_words = original.split(' ')
    d_words = diacritized.split(' ')
    if len(o_words) != len(d_words):
        if len(o_words) == 1 and len(d_words) == 1:
            return fix_naa_word(original, diacritized)
        return diacritized
    return ' '.join(fix_naa_word(ow, dw) for ow, dw in zip(o_words, d_words))

def swap_tanween_fatha_alif(text):
    if not text:
        return text
    res = re.sub(r'(?<![\u0648\u0648\u0651])\u064B\u0627', '\u0627\u064B', text)
    res = res.replace('\u064B\u0649', '\u0649\u064B')
    return res

def swap_shadda_harakah(text):
    """
    يعكس ترتيب الشدة والحركة أينما وجدتا معاً:
    إذا كانت الشدة متبوعة بحركة -> تصبح الحركة أولاً ثم الشدة.
    إذا كانت الحركة متبوعة بشدة -> تصبح الشدة أولاً ثم الحركة.
    """
    if not text:
        return text
    return re.sub(
        r'(\u0651)([\u064B-\u0650\u0652\u0670])|([\u064B-\u0650\u0652\u0670])(\u0651)',
        lambda m: (m.group(2) + m.group(1)) if m.group(1) is not None else (m.group(4) + m.group(3)),
        text
    )

def shadda_before_harakah(text):
    """
    يضمن أن الشدة تسبق الحركة دائماً: [حركة][شدة] -> [شدة][حركة].
    """
    if not text:
        return text
    return re.sub(r'([\u064B-\u0650\u0652\u0670])(\u0651)', r'\2\1', text)

def harakah_before_shadda(text):
    """
    يضمن أن الحركة تسبق الشدة دائماً: [شدة][حركة] -> [حركة][شدة].
    """
    if not text:
        return text
    return re.sub(r'(\u0651)([\u064B-\u0650\u0652\u0670])', r'\2\1', text)

def sukoonize_end_of_sentence(text):
    if not text or not text.strip():
        return text

    if re.search(r'\u064B(\s*[\.\,\،\؛\;\?\؟\!\:\-\_]*\s*)$', text):
        return text

    ta_pattern = r'ة[\u064B-\u0652\u0670]*(\s*[\.\,\،\؛\;\?\؟\!\:\-\_]*\s*)$'
    if re.search(ta_pattern, text):
        return re.sub(ta_pattern, r'\1', text)

    diac_pattern = r'([\u0621-\u064A\u0671])(\u0651?)[\u064C\u064D\u064E\u064F\u0650\u0652\u0670]+(\s*[\.\,\،\؛\;\?\؟\!\:\-\_]*\s*)$'
    return re.sub(diac_pattern, r'\1\2\3', text)

class LatinTransliterator:
    SINGLE_LETTER_MAP = {
        'a': 'إِيهْ', 'A': 'إِيهْ', 'b': 'بِي', 'B': 'بِي', 'c': 'سِي', 'C': 'سِي',
        'd': 'دِي', 'D': 'دِي', 'e': 'إِي', 'E': 'إِي', 'f': 'إِفْ', 'F': 'إِفْ',
        'g': 'جِي', 'G': 'جِي', 'h': 'إِيتْشْ', 'H': 'إِيتْشْ', 'i': 'آيْ', 'I': 'آيْ',
        'j': 'جَي', 'J': 'جَي', 'k': 'كَي', 'K': 'كَي', 'l': 'إِلْ', 'L': 'إِلْ',
        'm': 'إِمْ', 'M': 'إِمْ', 'n': 'إِنْ', 'N': 'إِنْ', 'o': 'أُو', 'O': 'أُو',
        'p': 'بِي', 'P': 'بِي', 'q': 'كِيُو', 'Q': 'كِيُو', 'r': 'آرْ', 'R': 'آرْ',
        's': 'إِسْ', 'S': 'إِسْ', 't': 'تِي', 'T': 'تِي', 'u': 'يُو', 'U': 'يُو',
        'v': 'فِي', 'V': 'فِي', 'w': 'دَبْلِيُو', 'W': 'دَبْلِيُو', 'x': 'إِكْسْ', 'X': 'إِكْسْ',
        'y': 'وَايْ', 'Y': 'وَايْ', 'z': 'زِدْ', 'Z': 'زِدْ'
    }

    LATIN_CHAR_MAP = {
        'a': 'َا', 'b': 'ب', 'c': 'ك', 'd': 'د', 'e': 'ِي', 'f': 'ف', 'g': 'ج',
        'h': 'ه', 'i': 'ِي', 'j': 'ج', 'k': 'ك', 'l': 'ل', 'm': 'م', 'n': 'ن',
        'o': 'ُو', 'p': 'ب', 'q': 'ق', 'r': 'ر', 's': 'س', 't': 'ت', 'u': 'ُو',
        'v': 'ف', 'w': 'و', 'x': 'كْس', 'y': 'ي', 'z': 'ز'
    }

    LATIN_COMBOS = [
        ('tion', 'شِن'), ('sion', 'شِن'), ('ture', 'تْشَر'), ('cious', 'شَس'),
        ('tious', 'شَس'), ('cial', 'شَل'), ('tial', 'شَل'), ('ment', 'مِنْت'),
        ('able', 'أَبِل'), ('ible', 'إِبِل'), ('less', 'لِس'), ('ness', 'نِس'),
        ('ship', 'شِب'), ('board', 'بُورْد'), ('card', 'كَارْد'), ('hard', 'هَارْد'),
        ('port', 'بُورْت'), ('ford', 'فُورْد'), ('land', 'لَانْد'), ('wood', 'وُود'),
        ('good', 'جُود'), ('book', 'بُوك'), ('look', 'لُوك'), ('cook', 'كُوك'),
        ('walk', 'وُوك'), ('talk', 'تُوك'), ('work', 'وِيرْك'), ('word', 'وِرْد'),
        ('aight', 'َايت'), ('eight', 'إِيت'), ('ight', 'َايت'), ('ought', 'أُوت'),
        ('aught', 'أُوت'), ('sch', 'سْك'), ('tch', 'تْش'), ('ch', 'تْش'),
        ('sh', 'ش'), ('th', 'ث'), ('ph', 'ف'), ('kh', 'خ'), ('gh', 'غ'),
        ('dh', 'ذ'), ('zh', 'ج'), ('wh', 'و'), ('wr', 'ر'), ('kn', 'ن'),
        ('ps', 'س'), ('qu', 'كُو'), ('ck', 'ك'), ('ng', 'نْج'), ('eau', 'أُو'),
        ('eigh', 'إِي'), ('air', 'إِير'), ('ear', 'إِير'), ('eer', 'إِير'),
        ('oor', 'أُور'), ('our', 'أُور'), ('oar', 'أُور'), ('ee', 'ِي'),
        ('oo', 'ُو'), ('ea', 'ِي'), ('oa', 'ُو'), ('ai', 'َاي'), ('ay', 'َاي'),
        ('ey', 'ِي'), ('ei', 'ِي'), ('oi', 'ُوي'), ('oy', 'ُوي'), ('ou', 'َاو'),
        ('ow', 'َاو'), ('au', 'أُو'), ('aw', 'أُو'), ('ie', 'ِي'), ('ue', 'ُو'),
        ('ui', 'ُو'), ('bb', 'ب'), ('cc', 'ك'), ('dd', 'د'), ('ff', 'ف'),
        ('gg', 'ج'), ('ll', 'ل'), ('mm', 'م'), ('nn', 'ن'), ('pp', 'ب'),
        ('rr', 'ر'), ('ss', 'س'), ('tt', 'ت'), ('vv', 'ف'), ('zz', 'ز')
    ]

    KNOWN_NAMES = {
        'google': 'جُوجِل', 'android': 'أَنْدْرُويْد', 'apple': 'أَبْل',
        'microsoft': 'مَايْكْرُوسُوفْت', 'windows': 'وِينْدُوز', 'linux': 'لِينُكْس',
        'ubuntu': 'أُوبُونْتُو', 'meta': 'مِيتَا', 'facebook': 'فَيْسْبُوك',
        'whatsapp': 'وَاتْسَاب', 'telegram': 'تِلِيجْرَام', 'instagram': 'إِنْسْتِغْرَام',
        'twitter': 'تُوِيتَر', 'x': 'إِكْسْ', 'tiktok': 'تِيكْ تُوك',
        'snapchat': 'سْنَابْ شَات', 'youtube': 'يُوتْيُوب', 'netflix': 'نِتْفْلِيكْس',
        'spotify': 'سْبُوتِيفَاي', 'amazon': 'أَمَازُون', 'uber': 'أُوبَر',
        'zoom': 'زُوم', 'skype': 'سْكَايْب', 'discord': 'دِيسْكُورْد',
        'github': 'جِيتْ هَاب', 'gmail': 'جِيمِيل', 'chrome': 'كْرُوم',
        'firefox': 'فَايِرْفُوكْس', 'edge': 'إِيدْج', 'safari': 'سَفَارِي',
        'wikipedia': 'وِيكِيبِيدْيَا', 'tesla': 'تِسْلاَ', 'samsung': 'سَامْسُونْج',
        'huawei': 'هَوَاوِي', 'xiaomi': 'شَاوْمِي', 'sony': 'سُونِي',
        'intel': 'إِينْتِل', 'nvidia': 'إِنْفِيدْيَا', 'amd': 'إِيهْ إِمْ دِي',
        'python': 'بَايْثُون', 'java': 'جَافَا', 'javascript': 'جَافَاسْكْرِبْت',
        'typescript': 'تَايِبْسْكْرِبْت', 'php': 'بِي إِتْشْ بِي', 'html': 'إِتْشْ تِي إِمْ إِل',
        'css': 'سِي إِسْ إِس', 'sql': 'إِسْ كِيُو إِل', 'api': 'إِيهْ بِي آي',
        'sdk': 'إِسْ دِي كَي', 'apk': 'إِيهْ بِي كَي', 'pdf': 'بِي دِي إِف',
        'ram': 'رَام', 'rom': 'رُوم', 'cpu': 'سِي بِي يُو', 'gpu': 'جِي بِي يُو',
        'gps': 'جِي بِي إِس', 'sim': 'سِيم', 'vpn': 'فِي بِي إِن', 'wifi': 'وَايْفَاي',
        'usb': 'يُو إِسْ بِي', 'sms': 'إِسْ إِمْ إِس', 'url': 'يُو آرْ إِل',
        'ip': 'آيْ بِي', 'mac': 'مَاك', 'ios': 'آيْ أُو إِس', 'pc': 'بِي سِي',
        'tv': 'تِي فِي', 'ai': 'إِيهْ آي', 'ok': 'أُوكِي', 'app': 'آب',
        'apps': 'آبْس', 'bot': 'بُوت', 'link': 'لِينْك', 'online': 'أُونْلاِين',
        'offline': 'أُوفْلاِين', 'email': 'إِيمِيل', 'e-mail': 'إِيمِيل',
        'site': 'سَايْت', 'web': 'وِيب', 'chat': 'تْشَات', 'chatgpt': 'تْشَاتْ جِي بِي تِي',
        'gemini': 'جِيمِينَاي', 'deepmind': 'دِيبْ مَايْنْد', 'openai': 'أُوبْنْ إِيهْ آي',
        'antigravity': 'أَنْتِي جْرَافِيتِي', 'rhvoice': 'آرْ إِتْشْ فُويْس',
        'talkback': 'تُوكْ بَاك', 'nvda': 'إِنْ فِي دِي إِيه', 'cancel': 'كَانْسِل',
        'download': 'دَاوْنْلُود', 'upload': 'أَبْلُود', 'update': 'أَبْدَيْت',
        'install': 'إِنْسْتُول', 'uninstall': 'أَنْإِنْسْتُول', 'setup': 'سِيتْأَب',
        'settings': 'سِيتِينْغْز', 'options': 'أُوبْشِنْز', 'menu': 'مِنْيُو',
        'login': 'لُوجِين', 'logout': 'لُوجْآوت', 'sign': 'سَايْن',
        'signin': 'سَايْنْ إِن', 'signup': 'سَايْنْ أَب', 'password': 'بَاسْوِرْد',
        'username': 'يُوزَرْنِيم', 'user': 'يُوزَر', 'admin': 'أَدْمِين',
        'status': 'سْتَاتُوس', 'error': 'إِيرُور', 'warning': 'وَارْنِينْج',
        'info': 'إِينْفُو', 'help': 'هِيلْب', 'search': 'سِيرْتْش', 'home': 'هُوم',
        'back': 'بَاك', 'next': 'نِكْسْت', 'play': 'بْلَاي', 'pause': 'بُوز',
        'stop': 'سْتُوب', 'resume': 'رِيزْيُوم', 'mute': 'مْيُوت', 'unmute': 'أَنْمْيُوت',
        'reset': 'رِيسِت', 'restart': 'رِيسْتَارْت', 'power': 'بَاوَر',
        'bluetooth': 'بْلُوتُوث', 'battery': 'بَاتِرِي', 'message': 'مِسِيدْج',
        'messages': 'مِسِيدْجِز', 'call': 'كُول', 'calls': 'كُولْز',
        'video': 'فِيدْيُو', 'audio': 'أُودْيُو', 'music': 'مْيُوزِيك',
        'photo': 'فُوتُو', 'photos': 'فُوتُوز', 'image': 'إِيمِيدْج',
        'camera': 'كَامِيرَا', 'gallery': 'جَالِيرِي', 'file': 'فَايْل',
        'files': 'فَايْلْز', 'folder': 'فُولْدَر', 'document': 'دُوكْيُومِنْت',
        'documents': 'دُوكْيُومِنْتْس', 'page': 'بِيج', 'view': 'فْيُو',
        'edit': 'إِدِيت', 'delete': 'دِلِيت', 'remove': 'رِيمُوف', 'clear': 'كْلِير',
        'copy': 'كُوبِي', 'cut': 'كَات', 'paste': 'بَاسْت', 'share': 'شِير',
        'send': 'سِينْد', 'save': 'سِيف', 'open': 'أُوبِن', 'close': 'كْلُوز',
        'exit': 'إِكْزِت', 'select': 'سِلِكْت', 'all': 'أُول', 'none': 'نَان',
        'yes': 'يَس', 'no': 'نُو', 'true': 'تْرُو', 'false': 'فُولْس',
        'code': 'كُود', 'data': 'دَاتَا', 'mode': 'مُود', 'test': 'تِسْت',
        'version': 'فِيرْشِن', 'excel': 'إِكْسِل', 'word': 'وِرْد',
        'outlook': 'آوتْلُوك', 'teams': 'تِيمْز', 'office': 'أُوفِيس', 'iphone': 'آيْفُون'
    }

    PAT_LATIN_WORD = re.compile(r"\b[A-Za-z][A-Za-z0-9]*(?:[.'\-][A-Za-z0-9]+)*\b")

    @classmethod
    def transliterate_word(cls, word):
        if not word:
            return word
        lower = word.lower()
        if lower in cls.KNOWN_NAMES:
            return cls.KNOWN_NAMES[lower]

        if len(word) == 1 and word in cls.SINGLE_LETTER_MAP:
            return cls.SINGLE_LETTER_MAP[word]

        if 2 <= len(word) <= 4 and word.isupper() and word.isalpha():
            spelled = [cls.SINGLE_LETTER_MAP.get(ch, ch) for ch in word]
            return ' '.join(spelled)

        res = lower
        if len(res) >= 4 and res.endswith('e'):
            pen = res[-2]
            ante = res[-3]
            if pen in 'bcdfghjklmnpqrstvwxyz' and ante in 'aeiouy':
                prefix = res[:-3]
                if ante == 'a': res = prefix + 'ِي' + pen
                elif ante == 'o': res = prefix + 'ُو' + pen
                elif ante == 'i': res = prefix + 'َاي' + pen
                elif ante == 'u': res = prefix + 'ُو' + pen
                else: res = res[:-1]

        for combo, repl in cls.LATIN_COMBOS:
            res = res.replace(combo, repl)

        res = re.sub(r'c(?=[eiy])', 'س', res)
        res = res.replace('c', 'ك')
        res = re.sub(r'g(?=[eiy])', 'ج', res)
        res = res.replace('g', 'ج')
        res = re.sub(r'^x', 'ز', res)
        res = res.replace('x', 'كْس')

        out = ''.join(cls.LATIN_CHAR_MAP.get(ch, ch) for ch in res)
        if out.startswith('َا') or out.startswith('ا'):
            out = 'أَ' + re.sub(r'^[َاا]+', '', out)
        elif out.startswith('ِي') or out.startswith('ي'):
            out = 'إِ' + re.sub(r'^[ِيي]+', '', out)
        elif out.startswith('ُو') or out.startswith('و'):
            out = 'أُ' + re.sub(r'^[ُوو]+', '', out)
        return out

    @classmethod
    def transliterate(cls, text):
        if not text:
            return text
        text = re.sub(r'(?i)\b[vV](\d+(?:\.\d+)*)\b', r'الإِصْدَار \1', text)
        return cls.PAT_LATIN_WORD.sub(lambda m: cls.transliterate_word(m.group(0)), text)


class Num2WordsAr:
    ARABIC_ONES = [
        '', 'وَاحِدٌ', 'اثْنَانِ', 'ثَلَاثَةٌ', 'أَرْبَعَةٌ', 'خَمْسَةٌ', 'سِتَّةٌ', 'سَبْعَةٌ', 'ثَمَانِيَةٌ', 'تِسْعَةٌ',
        'عَشَرَةٌ', 'أَحَدَ عَشَرَ', 'اثْنَا عَشَرَ', 'ثَلَاثَةَ عَشَرَ', 'أَرْبَعَةَ عَشَرَ', 'خَمْسَةَ عَشَرَ',
        'سِتَّةَ عَشَرَ', 'سَبْعَةَ عَشَرَ', 'ثَمَانِيَةَ عَشَرَ', 'تِسْعَةَ عَشَرَ'
    ]
    ARABIC_FEMININE_ONES = [
        '', 'إِحْدَى', 'اثْنَتَانِ', 'ثَلَاثٌ', 'أَرْبَعٌ', 'خَمْسٌ', 'سِتٌّ', 'سَبْعٌ', 'ثَمَانٍ', 'تِسْعٌ',
        'عَشْرٌ', 'إِحْدَى عَشْرَةَ', 'اثْنَتَا عَشْرَةَ', 'ثَلَاثَ عَشْرَةَ', 'أَرْبَعَ عَشْرَةَ',
        'خَمْسَ عَشْرَةَ', 'سِتَّ عَشْرَةَ', 'سَبْعَ عَشْرَةَ', 'ثَمَانِيَ عَشْرَةَ', 'تِسْعَ عَشْرَةَ'
    ]
    ARABIC_ORDINAL = [
        '', 'أَوَّلُ', 'ثَانِي', 'ثَالِثٌ', 'رَابِعٌ', 'خَامِسٌ', 'سَادِسٌ', 'سَابِعٌ', 'ثَامِنٌ',
        'تَاسِعٌ', 'عَاشِرٌ', 'حَادِيَ عَشَرَ', 'ثَانِيَ عَشَرَ', 'ثَالِثَ عَشَرَ', 'رَابِعَ عَشَرَ',
        'خَامِسَ عَشَرَ', 'سَادِسَ عَشَرَ', 'سَابِعَ عَشَرَ', 'ثَامِنَ عَشَرَ', 'تَاسِعَ عَشَرَ'
    ]
    ARABIC_TENS = [
        'عِشْرُونَ', 'ثَلَاثُونَ', 'أَرْبَعُونَ', 'خَمْسُونَ', 'سِتُّونَ', 'سَبْعُونَ', 'ثَمَانُونَ', 'تِسْعُونَ'
    ]
    ARABIC_HUNDREDS = [
        '', 'مِائَةٌ', 'مِائَتَانِ', 'ثَلَاثُمِائَةٍ', 'أَرْبَعُمِائَةٍ', 'خَمْسُمِائَةٍ', 'سِتُّمِائَةٍ',
        'سَبْعُمِائَةٍ', 'ثَمَانِمِائَةٍ', 'تِسْعُمِائَةٍ'
    ]
    ARABIC_APPENDED_TWOS = [
        'مِائَتَا', 'أَلْفَا', 'مِلْيُونَا', 'مِلْيَارَا', 'تِرِيلْيُونَا', 'كُوَادْرِيلْيُونَا',
        'كُوِينْتِلْيُونَا', 'سِكْسْتِيلْيُونَا', 'سَبْتِيلْيُونَا', 'أُوكْتِيلْيُونَا'
    ]
    ARABIC_TWOS = [
        'مِائَتَانِ', 'أَلْفَانِ', 'مِلْيُونَانِ', 'مِلْيَارَانِ', 'تِرِيلْيُونَانِ',
        'كُوَادْرِيلْيُونَانِ', 'كُوِينْتِلْيُونَانِ', 'سِكْسْتِيلْيُونَانِ', 'سَبْتِيلْيُونَانِ',
        'أُوكْتِيلْيُونَانِ'
    ]
    ARABIC_GROUP = [
        'مِائَةٌ', 'أَلْفٌ', 'مِلْيُونٌ', 'مِلْيَارٌ', 'تِرِيلْيُونٌ', 'كُوَادْرِيلْيُونٌ',
        'كُوِينْتِلْيُونٌ', 'سِكْسْتِيلْيُونٌ', 'سَبْتِيلْيُونٌ', 'أُوكْتِيلْيُونٌ'
    ]
    ARABIC_APPENDED_GROUP = [
        '', 'أَلْفاً', 'مِلْيُوناً', 'مِلْيَاراً', 'تِرِيلْيُوناً', 'كُوَادْرِيلْيُوناً',
        'كُوِينْتِلْيُوناً', 'سِكْسْتِيلْيُوناً', 'سَبْتِيلْيُوناً', 'أُوكْتِيلْيُوناً'
    ]
    ARABIC_PLURAL_GROUPS = [
        '', 'آلَافٍ', 'مَلَايِينُ', 'مِلْيَارَاتٌ', 'تِرِيلْيُونَاتٌ', 'كُوَادْرِيلْيُونَاتٌ',
        'كُوِينْتِلْيُونَاتٌ', 'سِكْسْتِيلْيُونَاتٌ', 'سَبْتِيلْيُونَاتٌ', 'أُوكْتِيلْيُونَاتٌ'
    ]

    @classmethod
    def _process_arabic_group(cls, group_number, group_level, remaining_number):
        tens = group_number % 100
        hundreds = group_number // 100
        ret_val = []

        if hundreds > 0:
            if tens == 0 and hundreds == 2 and group_level > 0:
                ret_val.append(cls.ARABIC_APPENDED_TWOS[0])
            else:
                h_str = cls.ARABIC_HUNDREDS[hundreds]
                ret_val.append(h_str)
                if tens != 0:
                    ret_val.append(' وَ')

        if tens > 0:
            if tens < 20:
                if tens == 2 and group_level > 0 and hundreds == 0:
                    pass
                else:
                    ret_val.append(cls.ARABIC_ONES[tens])
            else:
                ones = tens % 10
                t_idx = (tens // 10) - 2
                if ones > 0:
                    ret_val.append(cls.ARABIC_ONES[ones])
                    ret_val.append(' وَ')
                ret_val.append(cls.ARABIC_TENS[t_idx])

        s = ''.join(ret_val).strip()

        if group_level > 0:
            if group_number == 2:
                if group_level < len(cls.ARABIC_TWOS):
                    if remaining_number == 0:
                        s = cls.ARABIC_APPENDED_TWOS[group_level]
                    else:
                        s = cls.ARABIC_TWOS[group_level]
            elif 3 <= group_number <= 10:
                if group_level < len(cls.ARABIC_PLURAL_GROUPS):
                    s = s + ' ' + cls.ARABIC_PLURAL_GROUPS[group_level]
            elif group_number > 0:
                if group_level < len(cls.ARABIC_GROUP):
                    g_name = cls.ARABIC_GROUP[group_level] if remaining_number == 0 else cls.ARABIC_APPENDED_GROUP[group_level]
                    s = s + ' ' + g_name

        return s.strip()

    @classmethod
    def to_cardinal(cls, n):
        n = int(n)
        if n == 0:
            return 'صِفْرٌ'
        if n < 0:
            return 'سَالِب ' + cls.to_cardinal(-n)

        groups = []
        temp = n
        while temp > 0:
            groups.append(temp % 1000)
            temp //= 1000

        result_parts = []
        for level in range(len(groups) - 1, -1, -1):
            g_num = groups[level]
            if g_num == 0:
                continue
            rem = sum(groups[i] * (1000**i) for i in range(level))
            part = cls._process_arabic_group(g_num, level, rem)
            if part:
                result_parts.append(part)

        return ' وَ'.join(result_parts)

    @classmethod
    def to_ordinal(cls, n):
        n = int(n)
        if 1 <= n <= 19:
            return cls.ARABIC_ORDINAL[n]
        card = cls.to_cardinal(n)
        return 'ال' + card if not card.startswith('ال') else card


class ArNorm:
    DIGIT_NAMES = {
        '0': 'صِفْر', '1': 'وَاحِد', '2': 'اثْنَان', '3': 'ثَلاَثَة', '4': 'أَرْبَعَة',
        '5': 'خَمْسَة', '6': 'سِتَّة', '7': 'سَبْعَة', '8': 'ثَمَانِيَة', '9': 'تِسْعَة'
    }
    ORD_UNITS_M = [
        '', 'الأَوَّلُ', 'الثَّانِي', 'الثَّالِثُ', 'الرَّابِعُ', 'الخَامِسُ',
        'السَّادِسُ', 'السَّابِعُ', 'الثَّامِنُ', 'التَّاسِعُ', 'العَاشِرُ'
    ]
    ORD_UNITS_F = [
        '', 'الأُولَى', 'الثَّانِيَةُ', 'الثَّالِثَةُ', 'الرَّابِعَةُ', 'الخَامِسَةُ',
        'السَّادِسَةُ', 'السَّابِعَةُ', 'الثَّامِنَةُ', 'التَّاسِعَةُ', 'العَاشِرَةُ'
    ]
    MONTHS = [
        '', 'يَنَايِرَ', 'فِبْرَايِرَ', 'مَارِسَ', 'أَبْرِيلَ', 'مَايُو', 'يُونِيُو',
        'يُولِيُو', 'أَغُسْطُسَ', 'سِبْتَمْبَرَ', 'أُكْتُوبَرَ', 'نُوفَمْبَرَ', 'دِيسَمْبَرَ'
    ]

    DIAC_ORDINAL_HOURS = [
        '', 'الوَاحِدَةُ', 'الثَّانِيَةُ', 'الثَّالِثَةُ', 'الرَّابِعَةُ', 'الخَامِسَةُ',
        'السَّادِسَةُ', 'السَّابِعَةُ', 'الثَّامِنَةُ', 'التَّاسِعَةُ', 'العَاشِرَةُ',
        'الحَادِيَةَ عَشْرَةَ', 'الثَّانِيَةَ عَشْرَةَ'
    ]

    @classmethod
    def spell_digits(cls, digits):
        return ' '.join(cls.DIGIT_NAMES.get(c, c) for c in digits)

    @classmethod
    def cardinal(cls, n):
        try:
            return Num2WordsAr.to_cardinal(int(n))
        except Exception:
            return str(n)

    @classmethod
    def ordinal_definite(cls, n, feminine=True):
        n = int(n)
        tbl = cls.ORD_UNITS_F if feminine else cls.ORD_UNITS_M
        if 1 <= n <= 10:
            return tbl[n]
        if n == 11:
            return 'الحَادِيَةَ عَشْرَةَ' if feminine else 'الحَادِيَ عَشَرَ'
        if n == 12:
            return 'الثَّانِيَةَ عَشْرَةَ' if feminine else 'الثَّانِيَ عَشَرَ'
        if 13 <= n <= 19:
            u = tbl[n - 10].replace('ال', '', 1)
            return ('ال' + u + ' عَشْرَةَ') if feminine else ('ال' + u + ' عَشَرَ')
        if n == 20: return 'العِشْرُونَ'
        if n == 30: return 'الثَّلَاثُونَ'
        if 21 <= n <= 39:
            u = tbl[n % 10]
            t10 = 'العِشْرُونَ' if (n // 10) == 2 else 'الثَّلَاثُونَ'
            return f'{u} وَ{t10}'
        return Num2WordsAr.to_ordinal(n)

    @classmethod
    def get_diac_minute(cls, mi):
        mi = int(mi)
        if mi == 0: return ''
        if mi == 1: return 'وَدَقِيقَةٌ وَاحِدَةٌ'
        if mi == 2: return 'وَدَقِيقَتَانِ'
        if mi == 15: return 'وَالرُّبْعُ'
        if mi == 20: return 'وَالثُّلْثُ'
        if mi == 30: return 'وَالنِّصْفُ'
        if mi == 40: return 'إِلَّا ثُلْثاً'
        if mi == 45: return 'إِلَّا رُبْعاً'
        if 3 <= mi <= 10:
            return 'وَ' + cls.cardinal(mi) + ' دَقَائِقَ'
        return 'وَ' + cls.cardinal(mi) + ' دَقِيقَةً'

    @classmethod
    def x_dates(cls, t):
        def repl(m):
            d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
            if 1 <= d <= 31 and 1 <= mo <= 12:
                if y < 100:
                    y += 2000 if y < 50 else 1900
                d_str = cls.ordinal_definite(d, feminine=False)
                m_str = cls.MONTHS[mo]
                y_str = cls.cardinal(y)
                return f'{d_str} مِنْ {m_str} {y_str}'
            return m.group(0)
        return re.sub(r'\b(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})\b', repl, t)

    PAT_TIME_COLON = re.compile(
        r'(?:السَّاعَةُ\s+|السَّاعَةَ\s+|الساعة\s+)?(?<!\d)(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(صباحاً|صباحا|مساءً|مساء|a\.m\.|p\.m\.|am|pm|ص\.|م\.|ص(?![\w\u0621-\u064A\u0670\u0671])|م(?![\w\u0621-\u064A\u0670\u0671]))?(?!\d)',
        re.IGNORECASE
    )
    PAT_TIME_SINGLE = re.compile(
        r'(?:السَّاعَةُ|السَّاعَةَ|الساعة)\s+(\d{1,2})\s*(صباحاً|صباحا|مساءً|مساء|a\.m\.|p\.m\.|am|pm|ص\.|م\.|ص(?![\w\u0621-\u064A\u0670\u0671])|م(?![\w\u0621-\u064A\u0670\u0671]))?(?!\d)',
        re.IGNORECASE
    )
    PAT_TIME_STANDALONE = re.compile(
        r'(?<![\d:])\b(1[0-2]|0?[1-9])\s*(صباحاً|صباحا|مساءً|مساء|a\.m\.|p\.m\.|am|pm|ص\.|م\.|ص(?![\w\u0621-\u064A\u0670\u0671])|م(?![\w\u0621-\u064A\u0670\u0671]))(?![\d:])',
        re.IGNORECASE
    )

    SYMBOLS = {
        "&": "وَ", "@": "آت", "#": "رَقْم",
        "%": "بِالْمِئَة", "٪": "بِالْمِئَة", "‰": "بِالْأَلْف",
        "+": "زَائِد", "=": "يُسَاوِي", "≠": "لَا يُسَاوِي",
        "<": "أَصْغَرُ مِنْ", ">": "أَكْبَرُ مِنْ",
        "≤": "أَصْغَرُ أَوْ يُسَاوِي", "≥": "أَكْبَرُ أَوْ يُسَاوِي",
        "±": "زَائِدٌ أَوْ نَاقِص", "×": "ضَرْب", "÷": "قِسْمَة",
        "√": "جَذْر", "∞": "مَا لَا نِهَايَة", "π": "بَاي",
        "°": "دَرَجَة", "µ": "مِيكْرُو", "©": "حُقُوقُ النَّشْر",
        "®": "عَلَامَةٌ مُسَجَّلَة", "™": "عَلَامَةٌ تِجَارِيَّة",
        "§": "فِقْرَة", "¶": "عَلَامَةُ فِقْرَة",
        "†": "عَلَامَةُ إِحَالَة", "•": "نُقْطَةُ تَعْدَاد",
        "·": "نُقْطَةٌ وَسَطِيَّة",
        "★": "نَجْمَة", "☆": "نَجْمَة", "٭": "نَجْمَة",
        "→": "يُؤَدِّي إِلَى",
        "←": "مِنْ", "↔": "ذَهَابًا وَإِيَابًا", "⇒": "إِذَنْ",
        "~": "تَقْرِيبًا", "|": "خَطٌّ عَمُودِيّ",
        "﴿": "قَوْسٌ قُرْآنِيٌّ مَفْتُوح", "﴾": "قَوْسٌ قُرْآنِيٌّ مُغْلَق",
        "۞": "رُبْعُ حِزْب", "۩": "عَلَامَةُ سَجْدَة"
    }

    PAT_VERSION_CLEAN = re.compile(r'(?i)(إصدار|النسخة|نسخة|تطبيق|الإصدار)\s+[vV](\d+)')
    PAT_VERSION_V = re.compile(r'(?i)\b[vV](\d+(?:\.\d+)*)\b')
    PAT_VERSION_MULTIDOT = re.compile(r'\b(\d+(?:\.\d+){2,})\b')
    PAT_DECIMAL = re.compile(r'(\d+)[.,،\u066B](\d+)')
    PAT_INTEGER = re.compile(r'\d+')

    @classmethod
    def x_dates(cls, t):
        def repl(m):
            d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
            if 1 <= d <= 31 and 1 <= mo <= 12:
                if y < 100:
                    y += 2000 if y < 50 else 1900
                d_str = cls.ordinal_definite(d, feminine=False)
                m_str = cls.MONTHS[mo]
                y_str = cls.cardinal(y)
                return f'{d_str} مِنْ {m_str} {y_str}'
            return m.group(0)
        return re.sub(r'\b(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})\b', repl, t)

    @classmethod
    def x_times(cls, t):
        if not t:
            return t

        # 1. Colon Time (e.g. 11:00, 11:15, 11:30, 5:30 م, الساعة 11:00)
        def repl_colon(m):
            h = int(m.group(1))
            mi = int(m.group(2))
            sec = int(m.group(3)) if m.group(3) is not None else -1
            period_str = m.group(4)

            if not (0 <= h <= 23 and 0 <= mi <= 59):
                return m.group(0)

            period = ''
            if period_str:
                p_lower = period_str.lower()
                if 'ص' in p_lower or 'am' in p_lower:
                    period = 'صَبَاحاً'
                elif 'م' in p_lower or 'pm' in p_lower:
                    period = 'مَسَاءً'

            hh = h
            if h == 0:
                hh = 12
                if not period: period = 'مُنْتَصَفِ اللَّيْلِ'
            elif h == 12:
                hh = 12
                if not period: period = 'ظُهْراً'
            elif h > 12:
                hh = h - 12
                if not period: period = 'مَسَاءً'
            elif 1 <= h < 12:
                hh = h
                if not period and m.group(1).startswith('0'):
                    period = 'صَبَاحاً'

            hour_word = cls.DIAC_ORDINAL_HOURS[hh] if 1 <= hh <= 12 else cls.ordinal_definite(hh, feminine=True)
            base = hour_word

            min_word = cls.get_diac_minute(mi)
            if min_word:
                min_word = ' ' + min_word

            sec_word = ''
            if sec > 0:
                sec_word = f' وَ{cls.cardinal(sec)} ثَانِيَةً'

            out = base + min_word + sec_word + ((' ' + period) if period else '')
            return out

        t = cls.PAT_TIME_COLON.sub(repl_colon, t)

        # 2. Explicit "الساعة X" Pattern (e.g. الساعة 11, الساعة 11 صباحاً)
        def repl_single(m):
            h = int(m.group(1))
            period_str = m.group(2)
            if not (1 <= h <= 24):
                return m.group(0)
            period = ''
            if period_str:
                p_lower = period_str.lower()
                if 'ص' in p_lower or 'am' in p_lower:
                    period = 'صَبَاحاً'
                elif 'م' in p_lower or 'pm' in p_lower:
                    period = 'مَسَاءً'
            hh = h
            if hh in (24, 0):
                hh, period = 12, ('مُنْتَصَفِ اللَّيْلِ' if not period else period)
            elif hh == 12:
                hh, period = 12, ('ظُهْراً' if not period else period)
            elif hh > 12:
                hh, period = hh - 12, ('مَسَاءً' if not period else period)
            hour_word = cls.DIAC_ORDINAL_HOURS[hh] if 1 <= hh <= 12 else cls.ordinal_definite(hh, feminine=True)
            out = f'السَّاعَةُ {hour_word}' + ((' ' + period) if period else '')
            return out

        t = cls.PAT_TIME_SINGLE.sub(repl_single, t)

        # 3. Standalone hour with period (e.g. 11 صباحاً, 11 ص, 11 مساءً, 11 م)
        def repl_standalone(m):
            h = int(m.group(1))
            period_str = m.group(2)
            period = ''
            if period_str:
                p_lower = period_str.lower()
                if 'ص' in p_lower or 'am' in p_lower:
                    period = 'صَبَاحاً'
                elif 'م' in p_lower or 'pm' in p_lower:
                    period = 'مَسَاءً'
            hh = h
            hour_word = cls.DIAC_ORDINAL_HOURS[hh] if 1 <= hh <= 12 else cls.ordinal_definite(hh, feminine=True)
            out = f'{hour_word} {period}'.strip()
            return out

        t = cls.PAT_TIME_STANDALONE.sub(repl_standalone, t)
        return t

    @classmethod
    def x_currency(cls, t):
        currencies = dict_mgr.get_currencies()
        keys = sorted(currencies.keys(), key=len, reverse=True)
        for sym in keys:
            esym = re.escape(sym)
            c_name = currencies[sym]
            t = re.sub(r'(\d+(?:\.\d+)?)\s*' + esym, lambda m: f'{cls.cardinal(m.group(1))} {c_name}', t)
            if sym in ('$', '£', '€', '¥'):
                t = re.sub(esym + r'\s*(\d+(?:\.\d+)?)', lambda m: f'{cls.cardinal(m.group(1))} {c_name}', t)
        return t

    @classmethod
    def x_percent(cls, t):
        t = re.sub(r'(\d+(?:\.\d+)?)\s*%', lambda m: f'{cls.cardinal(m.group(1))} بِالْمِئَةِ', t)
        t = re.sub(r'(\d+(?:\.\d+)?)\s*‰', lambda m: f'{cls.cardinal(m.group(1))} بِالْأَلْفِ', t)
        return t

    @classmethod
    def x_math(cls, t):
        t = re.sub(r'(?<=\d)\s*\+\s*(?=\d)', ' زَائِد ', t)
        t = re.sub(r'(?<=\d)\s*[-−]\s*(?=\d)', ' نَاقِص ', t)
        t = re.sub(r'(?<=\d)\s*[×x]\s*(?=\d)', ' ضَرْب ', t)
        t = re.sub(r'(?<=\d)\s*[÷/]\s*(?=\d)', ' قِسْمَة ', t)
        t = re.sub(r'(?<=\d)\s*=\s*', ' يُسَاوِي ', t)
        return t

    @classmethod
    def x_phone(cls, t):
        def repl(m):
            s = m.group(0)
            plus = s.startswith('+')
            digits = re.sub(r'\D', '', s)
            out = cls.spell_digits(digits)
            return ('زَائِد ' + out) if plus else out
        return re.sub(r'\+?\d[\d\s\-]{6,}\d', repl, t)

    @classmethod
    def x_ordinals(cls, t):
        return re.sub(r'الـ\s*(\d{1,3})', lambda m: cls.ordinal_definite(int(m.group(1))), t)

    @classmethod
    def x_roman(cls, t):
        ROMAN = [('XXI', 21), ('XIX', 19), ('XVIII', 18), ('XVII', 17),
                 ('XVI', 16), ('XIV', 14), ('XIII', 13), ('XII', 12),
                 ('XI', 11), ('VIII', 8), ('VII', 7), ('VI', 6),
                 ('XX', 20), ('XV', 15), ('IX', 9), ('IV', 4),
                 ('X', 10), ('V', 5), ('III', 3), ('II', 2), ('I', 1)]
        def repl(m):
            tok = m.group(0)
            for r, v in ROMAN:
                if tok == r: return cls.ordinal_definite(v, feminine=False)
            return tok
        return re.sub(r'\b(?=[IVX]+\b)[IVX]{1,5}\b', repl, t)

    @classmethod
    def cardinal_dec(cls, whole, frac):
        if frac in ('0', '00'):
            return f'{cls.cardinal(whole)} فَاصِلَة صِفْرٌ'
        return f'{cls.cardinal(whole)} فَاصِلَة {cls.spell_digits(frac)}'

    @classmethod
    def x_numbers(cls, t):
        t = cls.PAT_VERSION_CLEAN.sub(r'\1 \2', t)
        t = cls.PAT_VERSION_V.sub(r'الإِصْدَارُ \1', t)

        def repl_v(m):
            raw = m.group(1)
            parts = raw.split('.')
            return ' نُقْطَةٌ '.join(cls.cardinal(p) for p in parts)
        t = cls.PAT_VERSION_MULTIDOT.sub(repl_v, t)

        t = cls.PAT_DECIMAL.sub(lambda m: cls.cardinal_dec(m.group(1), m.group(2)), t)
        t = cls.PAT_INTEGER.sub(lambda m: cls.cardinal(m.group(0)), t)
        return t

    @classmethod
    def x_symbols(cls, t):
        for sym, word in cls.SYMBOLS.items():
            if sym in t:
                t = t.replace(sym, f' {word} ')
        return t

    @classmethod
    def x_abbrev(cls, t):
        abbrevs = dict_mgr.get_abbreviations()
        ar_letter = r'[ء-يٰٱ]'
        for ab, full in sorted(abbrevs.items(), key=lambda kv: -len(kv[0])):
            esc = re.escape(ab)
            pat = r'(?<!' + ar_letter + r')' + esc + r'(?!' + ar_letter + r')'
            t = re.sub(pat, full, t)
        return t

    @classmethod
    def normalize(cls, text):
        if not text or not text.strip():
            return text
        try:
            t = fold_digits(text)
            t = cls.x_abbrev(t)
            t = cls.x_dates(t)
            t = cls.x_times(t)
            t = cls.x_currency(t)
            t = cls.x_percent(t)
            t = cls.x_math(t)
            t = cls.x_roman(t)
            t = cls.x_ordinals(t)
            t = cls.x_phone(t)
            t = cls.x_numbers(t)
            t = cls.x_symbols(t)
            t = re.sub(r'\s{2,}', ' ', t).strip()
            return t or text
        except Exception:
            return text


def normalize(text, is_single_char_request=False):
    if not text or not text.strip():
        return text
    t = text.replace('ـ', '')
    t = normalize_presentation_forms(t)
    t = LatinTransliterator.transliterate(t)
    if is_single_char_request:
        single = get_single_char_name(t)
        if single:
            return single
    return ArNorm.normalize(fold_digits(t))
