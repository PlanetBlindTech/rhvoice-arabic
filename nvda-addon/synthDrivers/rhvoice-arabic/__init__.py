# Copyright (C) 2010, 2011, 2012, 2013, 2018, 2019  Olga Yakovleva <yakovleva.o.v@gmail.com>
# Copyright (C) 2019, 2023, 2025  Beka Gozalishvili <beqaprogger@gmail.com>
# Copyright (C) 2022 Alexander Linkov <kvark128@yandex.ru>

# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 2 of the License, or
# (at your option) any later version.

# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.

# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.

import sys
import os.path
import queue
from collections import OrderedDict, defaultdict
import threading
import ctypes
from ctypes import c_char_p, c_wchar_p, c_void_p, c_short, c_int, c_uint, c_double, POINTER, Structure, sizeof, string_at, CFUNCTYPE, byref, cast
import re
import copy

from io import StringIO

import config
import globalVars
import nvwave
from logHandler import log
import speechXml
from synthDriverHandler import (
    SynthDriver,
    synthDoneSpeaking,
    synthIndexReached,
    VoiceInfo
)
from speech.commands import (
    CharacterModeCommand,
    IndexCommand,
    LangChangeCommand,
    PitchCommand,
    RateCommand,
    VolumeCommand
)
import languageHandler
import addonHandler

import re

try:
    from . import _libboot
except ImportError:
    import _libboot
if not _libboot.boot():
    log.warning("RHVoice Arabic: _libboot.boot() failed. Dependencies may not load correctly.")

try:
    try:
        from . import audio_filter
    except Exception:
        import audio_filter
except Exception as _e:
    audio_filter = None
    log.warning("RHVoice Arabic: audio post-filter unavailable (%s); audio will be unfiltered." % _e)

_diacritizer = None

def get_diacritizer():
    global _diacritizer
    if _diacritizer is None:
        import sys
        import os
        addon_dir = os.path.dirname(__file__)
        
        try:
            try:
                from . import text_processing
            except Exception:
                import text_processing

            onnx_path = os.path.join(addon_dir, 'model', 'diacritizer.onnx')
            vocab_path = os.path.join(addon_dir, 'model', 'input_vocab_to_int.json')
            output_vocab_path = os.path.join(addon_dir, 'model', 'output_int_to_vocab.json')
            if not os.path.exists(onnx_path) or not os.path.exists(vocab_path) or not os.path.exists(output_vocab_path):
                log.error("Arabic diacritization model or vocab missing")
                _diacritizer = False
                return _diacritizer

            _diacritizer = text_processing.ShikkeelDiacritizer(onnx_path, vocab_path, output_vocab_path)
            log.info("Direct ONNXRuntime Arabic diacritizer loaded successfully: %s", onnx_path)
        except Exception as e:
            log.error(f"Failed to load Direct ONNXRuntime Arabic diacritizer: {e}")
            import traceback
            log.error(traceback.format_exc())
            _diacritizer = False
    return _diacritizer

def process_arabic_text(text, read_emojis=True, pause_sukoon=True, is_single_char_request=False):
    if not isinstance(text, str) or not text.strip():
        return text
    try:
        try:
            from . import text_processing
        except Exception:
            import text_processing

        diac = get_diacritizer()
        return text_processing.process_arabic_text(
            text,
            diacritizer=diac if diac else None,
            read_emojis=read_emojis,
            pause_sukoon=pause_sukoon,
            is_single_char_request=is_single_char_request
        )
    except Exception as e:
        log.error(f"Error during Arabic text processing: {e}", exc_info=True)
        return text



import addonAPIVersion
api_version = addonAPIVersion.CURRENT
import buildVersion

module_dir = os.path.dirname(__file__)
config_path = os.path.join(globalVars.appArgs.configPath, "RHVoice-config")

data_addon_name_pattern = re.compile("^RHVoice-.*(voice|language).*")

SPLIT_SENTENCE_PATTERN = re.compile(
    r'(?<=(?<!\d)[.،؛؟!?,;\n]|(?<=\d)[؛؟!؟;\n]|(?<!\d)\.(?!\d)|(?<!\d)[,،](?!\d)|(?<!\d):(?!\d))(?=[^.،؛؟!?,;\n])'
)

def is_punctuation_only(text):
    if not text:
        return True
    return not any(c.isalnum() for c in text)

def split_sequence_into_chunks(speechSequence):
    has_splittable_text = False
    for item in speechSequence:
        if isinstance(item, str) and len(item.strip()) > 1:
            chunks = SPLIT_SENTENCE_PATTERN.split(item)
            non_empty = [c for c in chunks if c and c.strip() and not is_punctuation_only(c)]
            if len(non_empty) > 1:
                has_splittable_text = True
                break

    if not has_splittable_text:
        return [speechSequence]

    result_sequences = []
    current_seq = []

    for item in speechSequence:
        if isinstance(item, str):
            stripped = item.strip()
            if len(stripped) <= 1:
                current_seq.append(item)
            else:
                chunks = SPLIT_SENTENCE_PATTERN.split(item)
                valid_chunks = [c for c in chunks if c and c.strip() and not is_punctuation_only(c)]
                if len(valid_chunks) <= 1:
                    current_seq.append(item)
                else:
                    for i, chunk in enumerate(valid_chunks):
                        if i == 0:
                            current_seq.append(chunk)
                            result_sequences.append(current_seq)
                            current_seq = []
                        elif i < len(valid_chunks) - 1:
                            result_sequences.append([chunk])
                        else:
                            current_seq.append(chunk)
        else:
            current_seq.append(item)

    if current_seq:
        result_sequences.append(current_seq)

    return result_sequences if result_sequences else [speechSequence]


class RHVoice_tts_engine_struct(Structure):
    pass


RHVoice_tts_engine = POINTER(RHVoice_tts_engine_struct)


class RHVoice_message_struct(Structure):
    pass
RHVoice_message = POINTER(RHVoice_message_struct)

class RHVoice_callback_types:
    set_sample_rate = CFUNCTYPE(c_int, c_int, c_void_p)
    play_speech = CFUNCTYPE(c_int, POINTER(c_short), c_uint, c_void_p)
    process_mark = CFUNCTYPE(c_int, c_char_p, c_void_p)
    word_starts = CFUNCTYPE(c_int, c_uint, c_uint, c_void_p)
    word_ends = CFUNCTYPE(c_int, c_uint, c_uint, c_void_p)
    sentence_starts = CFUNCTYPE(c_int, c_uint, c_uint, c_void_p)
    sentence_ends = CFUNCTYPE(c_int, c_uint, c_uint, c_void_p)
    play_audio = CFUNCTYPE(c_int, c_char_p, c_void_p)
    done = CFUNCTYPE(None, c_void_p)

class RHVoice_callbacks(Structure):
    _fields_ = [
        ("set_sample_rate", RHVoice_callback_types.set_sample_rate),
        ("play_speech", RHVoice_callback_types.play_speech),
        ("process_mark", RHVoice_callback_types.process_mark),
        ("word_starts", RHVoice_callback_types.word_starts),
        ("word_ends", RHVoice_callback_types.word_ends),
        ("sentence_starts", RHVoice_callback_types.sentence_starts),
        ("sentence_ends", RHVoice_callback_types.sentence_ends),
        ("play_audio", RHVoice_callback_types.play_audio),
        ("done", RHVoice_callback_types.done)
    ]

class RHVoice_init_params(Structure):
    _fields_ = [("data_path", c_char_p),
              ("config_path", c_char_p),
              ("resource_paths", POINTER(c_char_p)),
              ("callbacks", RHVoice_callbacks),
              ("options", c_uint)]

class RHVoice_message_type:
    text = 0
    ssml = 1
    characters = 2

class RHVoice_voice_gender:
    unknown = 0
    male = 1
    female = 2

class RHVoice_voice_info(Structure):
    _fields_ = [("language", c_char_p),
              ("name", c_char_p),
              ("gender", c_int),
              ("country", c_char_p)]

class RHVoice_punctuation_mode:
    default = 0
    none = 1
    all = 2
    some = 3

class RHVoice_capitals_mode:
    default = 0
    off = 1
    word = 2
    pitch = 3
    sound = 4

class RHVoice_synth_flag:
    dont_clip_rate = 1

class RHVoice_synth_params(Structure):
    _fields_ = [("voice_profile", c_char_p),
              ("absolute_rate", c_double),
              ("absolute_pitch", c_double),
              ("absolute_volume", c_double),
              ("relative_rate", c_double),
              ("relative_pitch", c_double),
              ("relative_volume", c_double),
              ("punctuation_mode", c_int),
              ("punctuation_list", c_char_p),
              ("capitals_mode", c_int),
              ("flags", c_int)]

def getLibraryPath():
    arch = "x64" if sys.maxsize > 2**32 else "x86"
    return os.path.join(module_dir, "lib", arch, "RHVoice.dll")

def load_tts_library():
    lib = ctypes.CDLL(getLibraryPath())
    lib.RHVoice_get_version.restype = c_char_p
    lib.RHVoice_new_tts_engine.argtypes = (POINTER(RHVoice_init_params),)
    lib.RHVoice_new_tts_engine.restype = RHVoice_tts_engine
    lib.RHVoice_delete_tts_engine.argtypes = (RHVoice_tts_engine,)
    lib.RHVoice_delete_tts_engine.restype = None
    lib.RHVoice_get_number_of_voices.argtypes = (RHVoice_tts_engine,)
    lib.RHVoice_get_number_of_voices.restype = c_uint
    lib.RHVoice_get_voices.argtypes = (RHVoice_tts_engine,)
    lib.RHVoice_get_voices.restype = POINTER(RHVoice_voice_info)
    lib.RHVoice_get_number_of_voice_profiles.argtypes = (RHVoice_tts_engine,)
    lib.RHVoice_get_number_of_voice_profiles.restype = c_uint
    lib.RHVoice_get_voice_profiles.argtypes = (RHVoice_tts_engine,)
    lib.RHVoice_get_voice_profiles.restype = POINTER(c_char_p)
    lib.RHVoice_are_languages_compatible.argtypes = (RHVoice_tts_engine, c_char_p, c_char_p)
    lib.RHVoice_are_languages_compatible.restype = c_int
    lib.RHVoice_new_message.argtypes = (RHVoice_tts_engine, c_char_p, c_uint, c_int, POINTER(RHVoice_synth_params), c_void_p)
    lib.RHVoice_new_message.restype = RHVoice_message
    lib.RHVoice_delete_message.arg_types = (RHVoice_message,)
    lib.RHVoice_delete_message.restype = None
    lib.RHVoice_speak.argtypes = (RHVoice_message,)
    lib.RHVoice_speak.restype = c_int
    return lib


class AudioPlayer:
    def __init__(self, synth, cancel_flag):
        self.__synth = synth
        self.__cancel_flag = cancel_flag
        self.__sample_rate = 0
        self.__players = {}
        self.__lock = threading.Lock()
        self.__closed = False
        # DC-block + soft-limit post-filter that removes the low-end ear
        # pressure and the transient clicks/clipping on د and final ت.
        self.__post_filter = audio_filter.AudioPostFilter() if audio_filter else None

    def do_get_player(self):
        if self.__closed:
            return None
        if self.__sample_rate == 0:
            return None
        player = self.__players.get(self.__sample_rate, None)
        if player is None:
            player = nvwave.WavePlayer(channels=1, samplesPerSec=self.__sample_rate, bitsPerSample=16, outputDevice=self.__synth.outputDeviceSection["outputDevice"])
            self.__players[self.__sample_rate] = player
        return player

    def get_player(self):
        with self.__lock:
            return self.do_get_player()

    def close(self):
        with self.__lock:
            self.__closed = True
            players = list(self.__players.values())
        for p in players:
            p.close()

    def set_sample_rate(self, sr):
        with self.__lock:
            if self.__closed:
                return
            if self.__sample_rate == 0:
                self.__sample_rate = sr
                return
            if self.__sample_rate == sr:
                return
            old_player = self.__players.get(self.__sample_rate, None)
        if old_player is not None:
            old_player.idle()
        with self.__lock:
            self.__sample_rate = sr

    def play(self, data):
        player = self.get_player()
        if player is not None and not self.__cancel_flag.is_set():
            if self.__post_filter is not None:
                data = self.__post_filter.process(data)
            player.feed(data)
            if self.__cancel_flag.is_set():
                player.stop()

    def stop(self):
        player = self.get_player()
        if player is not None:
            player.stop()

    def pause(self, switch):
        player = self.get_player()
        if player is not None:
            player.pause(switch)

    def idle(self):
        player = self.get_player()
        if player is not None:
            player.idle()

    def on_new_message(self):
        # Reset filter memory so the high-pass does not carry a tail from the
        # previous utterance into the next one.
        if self.__post_filter is not None:
            self.__post_filter.reset()

    def on_done(self):
        player = self.get_player()
        if player is not None:
            player.idle()

    def on_index(self, index):
        player = self.get_player()
        if player is not None and not self.__cancel_flag.is_set():
            player.feed(b"", onDone=lambda next_index=index: synthIndexReached.notify(synth=self.__synth, index=next_index))


class SampleRateCallback:
    def __init__(self, lib, player):
        self.__lib = lib
        self.__player = player

    def __call__(self, sample_rate, user_data):
        try:
            self.__player.set_sample_rate(sample_rate)
            return 1
        except Exception:
            log.error("RHVoice sample rate callback", exc_info=True)
            return 0


class SpeechCallback:
    def __init__(self, lib, player, cancel_flag):
        self.__lib = lib
        self.__player = player
        self.__cancel_flag = cancel_flag

    def __call__(self, samples, count, user_data):
        try:
            if self.__cancel_flag.is_set():
                return 0
            try:
                self.__player.play(string_at(samples, count*sizeof(c_short)))
            except Exception:
                log.debugWarning("Error feeding audio to nvWave", exc_info=True)
            return 1
        except Exception:
            log.error("RHVoice speech callback", exc_info=True)
            return 0


class MarkCallback:
    def __init__(self, lib, player):
        self.__lib = lib
        self.__player = player
        self.__lock = threading.Lock()
        self.__index = None

    @property
    def index(self):
        with self.__lock:
            return self.__index

    @index.setter
    def index(self, value):
        with self.__lock:
            self.__index = value

    def __call__(self, name, user_data):
        try:
            index = int(name)
            self.__player.on_index(index)
            return 1
        except Exception:
            log.error("RHVoice mark callback", exc_info=True)
            return 0


class DoneCallback:
    def __init__(self, synth, lib, player, cancel_flag):
        self.__synth = synth
        self.__lib = lib
        self.__player = player
        self.__cancel_flag = cancel_flag

    def __call__(self, user_data):
        try:
            if self.__cancel_flag.is_set():
                return
            self.__player.on_done()
            self.__player.idle()
            if self.__cancel_flag.is_set():
                return
            synthDoneSpeaking.notify(synth=self.__synth)
        except Exception:
            log.error("RHVoice done callback", exc_info=True)


class SpeakText:
    def __init__(self, lib, tts_engine, text, cancel_flag, player):
        self.__lib = lib
        self.__tts_engine = tts_engine
        self.__text = text.encode("utf-8", errors="ignore")
        self.__cancel_flag = cancel_flag
        self.__player = player
        self.__synth_params = RHVoice_synth_params(
            voice_profile=None,
            absolute_rate=0,
            relative_rate=1,
            absolute_pitch=0,
            relative_pitch=1,
            absolute_volume=0,
            relative_volume=1,
            punctuation_mode=RHVoice_punctuation_mode.default,
            punctuation_list=None,
            capitals_mode=RHVoice_capitals_mode.default,
            flags=0
        )

    def set_rate(self, rate):
        self.__synth_params.absolute_rate = rate/50.0-1

    def set_pitch(self, pitch):
        self.__synth_params.absolute_pitch = pitch/50.0-1

    def set_volume(self, volume):
        self.__synth_params.absolute_volume = volume/50.0-1

    def set_voice_profile(self, name):
        self.__synth_params.voice_profile = name.encode("utf-8")

    def configure_rate_boost(self, flag):
        if not flag:
            return
        self.__synth_params.relative_rate = 2.5
        self.__synth_params.flags |= RHVoice_synth_flag.dont_clip_rate

    def __call__(self):
        if self.__cancel_flag.is_set():
            return
        msg = self.__lib.RHVoice_new_message(
            self.__tts_engine,
            self.__text,
            len(self.__text),
            RHVoice_message_type.ssml,
            byref(self.__synth_params),
            None
        )
        if msg:
            self.__player.on_new_message()
            self.__lib.RHVoice_speak(msg)
            self.__player.idle()
            self.__lib.RHVoice_delete_message(msg)


class TTSThread(threading.Thread):
    def __init__(self, tts_queue):
        self.__queue = tts_queue
        threading.Thread.__init__(self)
        self.daemon = True

    def run(self):
        while True:
            try:
                task = self.__queue.get()
                if task is None:
                    break
                else:
                    task()
            except Exception:
                log.error("RHVoice: error while executing a tts task", exc_info=True)


class SsmlConverter(speechXml.SsmlConverter):
    """ This class removes xml:lang attribute from ssml string to make profiles work correctly."""

    def __init__(self, synth, defaultLanguage):
        self.synth = synth
        super().__init__(defaultLanguage)

    def generateBalancerCommands(self, speechSequence):
        attrs = OrderedDict((("version", "1.0"), ("xmlns", "http://www.w3.org/2001/10/synthesis")))
        yield speechXml.EncloseAllCommand("speak", attrs)
        for command in super(speechXml.SsmlConverter, self).generateBalancerCommands(speechSequence):
            yield command

    def convertLangChangeCommand(self, command):
        # Prevent RHVoice from switching to foreign/garbled voice profiles when typing or in character mode
        return None

    def convertCharacterModeCommand(self, command):
        # Prevent NVDA from sending <say-as interpret-as="characters"> to RHVoice C++ core,
        # which was causing RHVoice to re-spell already normalized character names (e.g., "شِين" -> "ش-ي-ن").
        return None


class SynthDriver(SynthDriver):
    name = "rhvoice-arabic"
    description = "RHVoice Arabic"

    supportedSettings = [
        SynthDriver.VoiceSetting(),
        SynthDriver.RateSetting(),
        SynthDriver.RateBoostSetting(),
        SynthDriver.PitchSetting(),
        SynthDriver.VolumeSetting()
    ]

    supportedCommands = {
        CharacterModeCommand,
        IndexCommand,
        LangChangeCommand,
        PitchCommand,
        RateCommand,
        VolumeCommand
	}
    supportedNotifications = {synthIndexReached, synthDoneSpeaking}

    @classmethod
    def check(cls):
        return any(
            re.match(r"RHVoice.*-voice", addon.name) or addon.name == "rhvoice-arabic"
            for addon in addonHandler.getRunningAddons()
        )

    def __languages_match(self, code1, code2, full=True):
        lang1 = code1.split("_")
        lang2 = code2.split("_")
        if lang1[0] != lang2[0]:
            return False
        if len(lang1) < 2 or len(lang2) < 2:
            return True
        if not full:
            return True
        return (lang1[1] == lang2[1])

    def __get_resource_paths(self):
        return [os.path.join(addon.path, name).replace(os.sep, "/").encode("utf-8")
            for addon in addonHandler.getRunningAddons()
            if data_addon_name_pattern.match(addon.name)
            or addon.name == "rhvoice-arabic"
            for name in ["data", "langdata", "lang2data"]
            if os.path.isdir(os.path.join(addon.path, name))]

    def __init__(self):
        self.__lib = load_tts_library()
        self.__cancel_flag = threading.Event()
        self.outputDeviceSection = config.conf["speech"] if buildVersion.version_year < 2025 else config.conf["audio"]
        self.__player = AudioPlayer(self, self.__cancel_flag)
        self.__sample_rate_callback = SampleRateCallback(self.__lib, self.__player)
        self.__c_sample_rate_callback = RHVoice_callback_types.set_sample_rate(self.__sample_rate_callback)
        self.__speech_callback = SpeechCallback(self.__lib,self.__player, self.__cancel_flag)
        self.__c_speech_callback = RHVoice_callback_types.play_speech(self.__speech_callback)
        self.__mark_callback = MarkCallback(self.__lib, self.__player)
        self.__c_mark_callback = RHVoice_callback_types.process_mark(self.__mark_callback)
        self.__done_callback = DoneCallback(self, self.__lib, self.__player, self.__cancel_flag)
        self.__c_done_callback = RHVoice_callback_types.done(self.__done_callback)
        resource_paths = self.__get_resource_paths()
        c_resource_paths = (c_char_p*(len(resource_paths)+1))(*(resource_paths+[None]))
        init_params = RHVoice_init_params(
            None,
            config_path.replace("\\", "/").encode("utf-8"),
            c_resource_paths,
            RHVoice_callbacks(
                self.__c_sample_rate_callback,
                self.__c_speech_callback,
                self.__c_mark_callback,
                cast(None, RHVoice_callback_types.word_starts),
                cast(None, RHVoice_callback_types.word_ends),
                cast(None, RHVoice_callback_types.sentence_starts),
                cast(None, RHVoice_callback_types.sentence_ends),
                cast(None, RHVoice_callback_types.play_audio),
                self.__c_done_callback
            ),
            0
        )
        try:
            os.makedirs(config_path, exist_ok=True)
            try:
                from . import dictionary_manager as dict_mgr
            except Exception:
                import dictionary_manager as dict_mgr
            dict_mgr.load_default()
            dict_mgr.load_user_dictionary(config_path)
        except Exception:
            pass
            
        self.__tts_engine = self.__lib.RHVoice_new_tts_engine(byref(init_params))
        if not self.__tts_engine:
            raise RuntimeError("RHVoice: initialization error")
        nvda_language = languageHandler.getLanguage()
        number_of_voices = self.__lib.RHVoice_get_number_of_voices(self.__tts_engine)
        native_voices = self.__lib.RHVoice_get_voices(self.__tts_engine)
        self.__voice_languages = dict()
        self.__languages = set()
        for i in range(number_of_voices):
            native_voice = native_voices[i]
            voice_language = native_voice.language.decode("utf-8")
            if voice_language.lower() == "arabic":
                voice_language = "ar"
            if native_voice.country:
                self.__languages.add(voice_language)
                voice_language = voice_language+"_"+native_voice.country.decode("utf-8")
            self.__voice_languages[native_voice.name.decode("utf-8")] = voice_language
            self.__languages.add(voice_language)
        self.__profile = None
        self.__profiles = list()
        number_of_profiles = self.__lib.RHVoice_get_number_of_voice_profiles(self.__tts_engine)
        native_profile_names = self.__lib.RHVoice_get_voice_profiles(self.__tts_engine)
        for i in range(number_of_profiles):
            name = native_profile_names[i].decode("utf-8")
            self.__profiles.append(name)
            if (self.__profile is None) and self.__languages_match(nvda_language, self.__voice_languages[name.split("+")[0]]):
                self.__profile = name
        if self.__profile is None:
            self.__profile = self.__profiles[0]
        self.__rate = 50
        self.__pitch = 50
        self.__volume = 50
        self.__rate_boost = False
        self.__generation_id = 0
        self.__tts_queue = queue.Queue()
        self.__tts_thread = TTSThread(self.__tts_queue)
        self.__tts_thread.start()
        log.info("Using RHVoice version {}".format(self.__lib.RHVoice_get_version().decode()))

    def terminate(self):
        self.cancel()
        self.__tts_queue.put(None)
        self.__tts_thread.join()
        self.__player.close()
        self.__lib.RHVoice_delete_tts_engine(self.__tts_engine)
        self.__tts_engine = None

    def _create_speak_task(self, sequence):
        conv = SsmlConverter(self, self.language)
        text = conv.convertToXml(sequence)
        task = SpeakText(self.__lib, self.__tts_engine, text, self.__cancel_flag, self.__player)
        task.set_voice_profile(self.__profile)
        task.set_rate(self.__rate)
        task.set_pitch(self.__pitch)
        task.set_volume(self.__volume)
        task.configure_rate_boost(self.__rate_boost)
        return task

    def _process_sequence_chunk(self, chunk_seq):
        processed_seq = []
        for item in chunk_seq:
            if isinstance(item, str):
                stripped = item.strip()
                is_single = (len(item) == 1 or len(stripped) == 1 or (len(stripped) <= 3 and ar_textnorm.get_single_char_name(item) is not None))
                processed_seq.append(process_arabic_text(item, is_single_char_request=is_single))
            else:
                processed_seq.append(item)
        return processed_seq

    def speak(self, speechSequence):
        self.__generation_id += 1
        gen_id = self.__generation_id

        # Split speech sequence into sentences/chunks matching the Android App
        chunk_sequences = split_sequence_into_chunks(speechSequence)

        if len(chunk_sequences) <= 1:
            processed = self._process_sequence_chunk(chunk_sequences[0])
            task = self._create_speak_task(processed)
            self.__tts_queue.put(task)
            return

        # 1. Process Chunk 0 immediately and enqueue for instant audio playback
        first_processed = self._process_sequence_chunk(chunk_sequences[0])
        first_task = self._create_speak_task(first_processed)
        self.__tts_queue.put(first_task)

        # 2. Process subsequent chunks in a background streaming worker thread
        remaining_chunks = chunk_sequences[1:]

        def _background_streamer(chunks, gid):
            for seq in chunks:
                if self.__cancel_flag.is_set() or self.__generation_id != gid:
                    break
                proc = self._process_sequence_chunk(seq)
                if self.__cancel_flag.is_set() or self.__generation_id != gid:
                    break
                task = self._create_speak_task(proc)
                if self.__cancel_flag.is_set() or self.__generation_id != gid:
                    break
                self.__tts_queue.put(task)

        worker = threading.Thread(
            target=_background_streamer,
            args=(remaining_chunks, gen_id),
            daemon=True
        )
        worker.start()

    def pause(self, switch):
        self.__player.pause(switch)

    def cancel(self):
        self.__generation_id += 1
        try:
            while True:
                self.__tts_queue.get_nowait()
        except queue.Empty:
            self.__cancel_flag.set()
            self.__tts_queue.put(self.__cancel_flag.clear)
            self.__player.stop()

    def clamp(self, value, minValue=0, maxValue=100):
        return max(minValue, min(maxValue, value))

    def _get_lastIndex(self):
        return self.__mark_callback.index

    def _get_availableVoices(self):
        return OrderedDict((profile, VoiceInfo(profile, profile, self.__voice_languages[profile.split("+")[0]])) for profile in self.__profiles)

    def _get_language(self):
        return self.__voice_languages[self.__profile.split("+")[0]]

    def _get_rate(self):
        return self.__rate

    def _set_rate(self, rate):
        self.__rate = self.clamp(rate)

    def _get_pitch(self):
        return self.__pitch

    def _set_pitch(self, pitch):
        self.__pitch = self.clamp(pitch)

    def _get_volume(self):
        return self.__volume

    def _set_volume(self, volume):
        self.__volume = self.clamp(volume)

    def _get_voice(self):
        return self.__profile

    def _set_voice(self, voice):
        if voice in self.__profiles:
            self.__profile = voice

    def _get_rateBoost(self):
        return self.__rate_boost

    def _set_rateBoost(self, flag):
        self.__rate_boost = flag
