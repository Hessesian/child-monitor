/*
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
 * along with Child Monitor. If not, see <http://www.gnu.org/licenses/>.
 */
package de.rochefort.childmonitor

import android.app.Activity
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.io.IOException
import java.net.Socket

@UnstableApi
class ListenActivity : Activity() {
  val TAG = "ChildMonitor"
  var _address: String? = null
  var _port = 0
  var _name: String? = null
  var _mNotifyMgr: NotificationManagerCompat? = null
  var _listenThread: Thread? = null
  private val frequency: Int = AudioCodecDefines.Companion.FREQUENCY
  private val channelConfiguration: Int = AudioCodecDefines.Companion.CHANNEL_CONFIGURATION_OUT
  private val audioEncoding: Int = AudioCodecDefines.Companion.ENCODING
  private val bufferSize = AudioTrack.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
  private val byteBufferSize = bufferSize * 2

  @Throws(IllegalArgumentException::class, IllegalStateException::class, IOException::class)
  private fun streamAudio(socket: Socket, listener: AudioListener) {
    val new = true
    if(!new){
      runOnUiThread {
        // Global settings.
        val player =
          ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setLiveTargetOffsetMs(5000))
            .build()

        // Per MediaItem settings.
        val mediaItem =
          MediaItem.Builder()
            .setUri(Uri.parse("http://$_address:$_port"))
            .setLiveConfiguration(
              MediaItem.LiveConfiguration.Builder().setMaxPlaybackSpeed(1.02f).build()
            )
            .build()
        player.setMediaItem(mediaItem)
        player.play()
      }
    } else {
      Log.i(TAG, "Setting up stream")
      val audioTrack = AudioTrack(
        AudioManager.STREAM_MUSIC,
        frequency,
        channelConfiguration,
        audioEncoding,
        bufferSize,
        AudioTrack.MODE_STREAM
      )

      volumeControlStream = AudioManager.STREAM_MUSIC
      val `is` = socket.getInputStream()
      var read = 0
      audioTrack.play()
      try {
        val buffer = ByteArray(byteBufferSize)
        while (socket.isConnected && read != -1 && !Thread.currentThread().isInterrupted) {
          read = `is`.read(buffer)
          if (read > 0) {
            audioTrack.write(buffer, 0, read)
            val readBytes = ByteArray(read)
            System.arraycopy(buffer, 0, readBytes, 0, read)
            listener.onAudio(readBytes)
          }
        }
      } finally {
        audioTrack.stop()
        socket.close()
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val b = intent.extras
    _address = b!!.getString("address")
    _port = b.getInt("port")
    _name = b.getString("name")
    // Gets an instance of the NotificationManager service
    _mNotifyMgr = NotificationManagerCompat.from(this)
    setContentView(R.layout.activity_listen)
    val mBuilder = NotificationCompat.Builder(this@ListenActivity)
      .setOngoing(true)
      .setSmallIcon(R.drawable.listening_notification)
      .setContentTitle(getString(R.string.app_name))
      .setContentText(getString(R.string.listening))
    _mNotifyMgr?.notify(mNotificationId, mBuilder.build())
    val connectedText = findViewById<View>(R.id.connectedTo) as TextView
    connectedText.text = _name
    val statusText = findViewById<View>(R.id.textStatus) as TextView
    statusText.setText(R.string.listening)
    val volumeView = findViewById<View>(R.id.volume) as VolumeView
    val listener = object : AudioListener {
      override fun onAudio(audioBytes: ByteArray) {
        runOnUiThread { volumeView.onAudioData(audioBytes) }
      }
    }
    _listenThread = Thread {
      try {
        val socket = Socket(_address, _port)
        streamAudio(socket, listener)
      } catch (e: IOException) {
        Log.e(TAG, "Failed to stream audio", e)
      }
      if (!Thread.currentThread().isInterrupted) {
        // If this thread has not been interrupted, likely something
        // bad happened with the connection to the child device. Play
        // an alert to notify the user that the connection has been
        // interrupted.
        playAlert()
        runOnUiThread {
          val connectedText1 = findViewById<View>(R.id.connectedTo) as TextView
          connectedText1.text = ""
          val statusText1 = findViewById<View>(R.id.textStatus) as TextView
          statusText1.setText(R.string.disconnected)
          val mBuilder1 = NotificationCompat.Builder(this@ListenActivity)
            .setOngoing(false)
            .setSmallIcon(R.drawable.listening_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.disconnected))
          _mNotifyMgr!!.notify(mNotificationId, mBuilder1.build())
        }
      }
    }
    _listenThread!!.start()
  }

  public override fun onDestroy() {
    _listenThread!!.interrupt()
    _listenThread = null
    super.onDestroy()
  }

  private fun playAlert() {
    val mp = MediaPlayer.create(this, R.raw.upward_beep_chromatic_fifths)
    if (mp != null) {
      Log.i(TAG, "Playing alert")
      mp.setOnCompletionListener { obj: MediaPlayer -> obj.release() }
      mp.start()
    } else {
      Log.e(TAG, "Failed to play alert")
    }
  }

  companion object {
    // Sets an ID for the notification
    const val mNotificationId = 1
  }
}