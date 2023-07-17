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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.LinkedList

class VolumeView : View {
  private var volume = 0.0
  private var maxVolume = 0.0
  private var paint: Paint? = null
  private var volumeHistory: LinkedList<Double>? = null

  constructor(context: Context?) : super(context) {
    init()
  }

  constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
    init()
  }

  constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    init()
  }

  private fun init() {
    volume = 0.0
    maxVolume = 0.25
    paint = Paint()
    volumeHistory = LinkedList()
    paint!!.color = Color.rgb(255, 127, 0)
  }

  fun onAudioData(data: ByteArray) {
    var sum = 0.0
    for (i in data.indices) {
      val rel = data[i] / 128.0
      sum += Math.pow(rel, 2.0)
    }
    volume = sum / data.size
    if (volume > maxVolume) {
      maxVolume = volume
    }
    volumeHistory!!.addLast(volume)
    while (volumeHistory!!.size > MAX_HISTORY) {
      volumeHistory!!.removeFirst()
    }
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    val height = height
    val width = width
    var relativeBrightness = 0.0
    val normalizedVolume = volume / maxVolume
    relativeBrightness = Math.max(0.3, normalizedVolume)
    val blue: Int
    val rest: Int
    if (relativeBrightness > 0.5) {
      blue = 255
      rest = (2 * 255 * (relativeBrightness - 0.5)).toInt()
    } else {
      blue = (255 * (relativeBrightness - 0.2) / 0.3).toInt()
      rest = 0
    }
    val rgb = Color.rgb(rest, rest, blue)
    canvas.drawColor(rgb)
    val margins = height * 0.1
    val graphHeight = height - 2 * margins
    val leftMost = Math.max(0, volumeHistory!!.size - width)
    var yPrev = (graphHeight - margins).toInt()
    var i = leftMost
    while (i < volumeHistory!!.size && i - leftMost < width) {
      val xNext = i - leftMost
      val yNext = (margins + graphHeight - volumeHistory!![i] / maxVolume * graphHeight).toInt()
      var xPrev: Int = if (i == leftMost) {
        xNext
      } else {
        xNext - 1
      }
      if (i == leftMost && i > 0) {
        yPrev = (margins + graphHeight - volumeHistory!![i - 1] / maxVolume * graphHeight).toInt()
      }
      canvas.drawLine(xPrev.toFloat(), yPrev.toFloat(), xNext.toFloat(), yNext.toFloat(), paint!!)
      yPrev = yNext
      i++
    }
  }

  companion object {
    private const val MAX_HISTORY = 10000
  }
}