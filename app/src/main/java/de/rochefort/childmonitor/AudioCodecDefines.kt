/**
 * This file is part of the Child Monitor.
 *
 * Child Monitor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Child Monitor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Child Monitor. If not, see <http:></http:>//www.gnu.org/licenses/>.
 */
package de.rochefort.childmonitor

import android.media.AudioFormat

class AudioCodecDefines private constructor() {
  init {
    throw IllegalStateException("Do not instantiate!")
  }

  companion object {
    const val FREQUENCY = 11025
    const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    const val CHANNEL_CONFIGURATION_IN = AudioFormat.CHANNEL_IN_MONO
    const val CHANNEL_CONFIGURATION_OUT = AudioFormat.CHANNEL_OUT_MONO
  }
}