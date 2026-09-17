# -*- coding: utf-8 -*-
"""
Realtime audio post-filter for the RHVoice Arabic voice.

The bundled HTS voice ("Zayd") is a parametric (vocoder) voice with a very
strong low end. Two artefacts came out of that:

  * a constant sub-audible pressure on the ears — DC offset and sub-20 Hz
    rumble riding under the speech ("equaliser gone wrong"); and
  * intermittent clicks / crackle on high-energy voiced stops (د) and a
    mangled release on some final ت — transient peaks that overshot the
    16-bit ceiling and hard-clipped.

This module cleans both up in the audio path itself, so it works no matter
which phoneme or which upstream setting produced the peak:

  1. A one-pole DC-blocking high-pass (~18 Hz at 24 kHz) removes the DC/rumble
     that causes the ear pressure, without touching any speech energy.
  2. A soft-knee limiter replaces hard clipping with smooth saturation, so a
     transient that would have clipped is rounded over instead of producing a
     click.

Both stages are cheap and streaming: filter state is carried across the small
buffers RHVoice hands us within one utterance, and reset at the start of each
new utterance. If numpy is unavailable for any reason the filter degrades to a
transparent pass-through so audio is never lost.
"""

import struct

try:
    import numpy as np
    _HAVE_NUMPY = True
except Exception:  # pragma: no cover - numpy ships with the add-on
    _HAVE_NUMPY = False

try:
    from scipy.signal import lfilter as _lfilter
    _HAVE_SCIPY = True
except Exception:
    _HAVE_SCIPY = False


# ---- tuning ---------------------------------------------------------------
# One-pole high-pass pole. Closer to 1.0 = lower cutoff. 0.9965 @ 24 kHz is
# roughly an 18 Hz cutoff: it kills DC and subsonic rumble but leaves even the
# deepest male fundamental (~80-100 Hz) untouched.
_HP_POLE = 0.9965

# Soft limiter. Below the knee the signal passes through linearly; above it,
# it is compressed smoothly toward the ceiling so it can never hard-clip.
_LIMIT_KNEE = 0.80      # fraction of full scale where soft compression starts
_LIMIT_CEIL = 0.985     # highest fraction of full scale the output can reach
_INT16_MAX = 32767.0


class AudioPostFilter:
    """Stateful, streaming DC-block + soft-limit filter over int16 PCM."""

    def __init__(self):
        # DC-blocking high-pass  H(z) = (1 - z^-1) / (1 - pole z^-1).
        self._b = [1.0, -1.0]
        self._a = [1.0, -_HP_POLE]
        # Persistent filter delay state (scipy path) and scalar state (fallback).
        self._zi = None
        self._x1 = 0.0
        self._y1 = 0.0

    def reset(self):
        """Clear filter memory. Call at the start of every new utterance so a
        tail from the previous one cannot bleed into the next."""
        self._zi = None
        self._x1 = 0.0
        self._y1 = 0.0

    # -- internal helpers ---------------------------------------------------
    @staticmethod
    def _soft_limit(x):
        """Vectorised soft-knee limiter on a float array scaled to [-1, 1].

        Values under the knee are returned unchanged. Values above it are
        mapped through a smooth curve that asymptotically approaches the
        ceiling, so peaks are rounded instead of clipped.
        """
        knee = _LIMIT_KNEE
        ceil = _LIMIT_CEIL
        span = ceil - knee
        if span <= 0:
            return np.clip(x, -ceil, ceil)

        sign = np.sign(x)
        mag = np.abs(x)
        over = mag > knee
        # For the part above the knee, compress the excess with tanh so it can
        # never exceed the ceiling. tanh(0)=0 keeps the curve continuous at the
        # knee, and its slope there (=1) matches the linear region, so there is
        # no audible kink.
        excess = (mag[over] - knee) / span
        mag[over] = knee + span * np.tanh(excess)
        return sign * mag

    def _highpass(self, x):
        """One-pole DC-blocking high-pass with state carried across buffers."""
        if _HAVE_SCIPY:
            if self._zi is None:
                # Start the filter from rest, scaled to the first sample so we
                # don't emit a startup thump on the leading edge.
                self._zi = np.zeros(max(len(self._a), len(self._b)) - 1, dtype=np.float64)
            y, self._zi = _lfilter(self._b, self._a, x, zi=self._zi)
            return y
        # Pure-python fallback: y[n] = x[n] - x[n-1] + pole*y[n-1]
        pole = _HP_POLE
        y = np.empty_like(x)
        x1 = self._x1
        y1 = self._y1
        for i in range(x.shape[0]):
            xi = x[i]
            yi = xi - x1 + pole * y1
            y[i] = yi
            x1 = xi
            y1 = yi
        self._x1 = float(x1)
        self._y1 = float(y1)
        return y

    def _process_numpy(self, samples):
        x = samples.astype(np.float64)

        y = self._highpass(x)

        # --- soft limiter --------------------------------------------------
        y /= _INT16_MAX
        y = self._soft_limit(y)
        y *= _INT16_MAX

        return np.clip(np.rint(y), -32768, 32767).astype(np.int16)

    # -- public API ---------------------------------------------------------
    def process(self, data):
        """Filter a bytes buffer of little-endian int16 mono PCM, return bytes.

        On any error, returns the input unchanged so audio is never dropped.
        """
        if not data:
            return data
        if not _HAVE_NUMPY:
            return data
        try:
            samples = np.frombuffer(data, dtype="<i2")
            if samples.size == 0:
                return data
            out = self._process_numpy(samples)
            return out.tobytes()
        except Exception:
            return data
