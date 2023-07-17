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

import android.app.Activity
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.RegistrationListener
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class MonitorActivity : Activity() {
  private var nsdManager: NsdManager? = null
  private var registrationListener: RegistrationListener? = null
  private var currentSocket: ServerSocket? = null
  private var connectionToken: Any? = null
  private var currentPort = 0
  private fun serviceConnection(socket: Socket) {
    runOnUiThread {
      val statusText = findViewById<View>(R.id.textStatus) as TextView
      statusText.setText(R.string.streaming)
    }
    val frequency: Int = AudioCodecDefines.Companion.FREQUENCY
    val channelConfiguration: Int = AudioCodecDefines.Companion.CHANNEL_CONFIGURATION_IN
    val audioEncoding: Int = AudioCodecDefines.Companion.ENCODING
    val bufferSize = AudioRecord.getMinBufferSize(frequency, channelConfiguration, audioEncoding)
    val audioRecord = AudioRecord(
      MediaRecorder.AudioSource.MIC,
      frequency,
      channelConfiguration,
      audioEncoding,
      bufferSize
    )
    val byteBufferSize = bufferSize * 2
    val buffer = ByteArray(byteBufferSize)
    try {
      audioRecord.startRecording()
      val out = socket.getOutputStream()
      socket.sendBufferSize = byteBufferSize
      Log.d(TAG, "Socket send buffer size: " + socket.sendBufferSize)
      while (socket.isConnected && !Thread.currentThread().isInterrupted) {
        val read = audioRecord.read(buffer, 0, bufferSize)
        out.write(buffer, 0, read)
      }
    } catch (e: IOException) {
      Log.e(TAG, "Connection failed", e)
    } finally {
      audioRecord.stop()
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    Log.i(TAG, "ChildMonitor start")
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_monitor)
    nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
    currentPort = 10000
    currentSocket = null
    val currentToken = Any()
    connectionToken = currentToken
    Thread {
      while (connectionToken == currentToken) {
        try {
          ServerSocket(currentPort).use { serverSocket ->
            currentSocket = serverSocket
            // Store the chosen port.
            val localPort = serverSocket.localPort

            // Register the service so that parent devices can
            // locate the child device
            registerService(localPort)
            serverSocket.accept().use { socket ->
              Log.i(TAG, "Connection from parent device received")

              // We now have a client connection.
              // Unregister so no other clients will
              // attempt to connect
              unregisterService()
              serviceConnection(socket)
            }
          }
        } catch (e: IOException) {
          // Just in case
          currentPort++
          Log.e(TAG, "Failed to open server socket. Port increased to $currentPort", e)
        }
      }
    }.start()
    val addressText = findViewById<TextView>(R.id.address)
    val listenAddresses = listenAddresses
    if (!listenAddresses.isEmpty()) {
      val sb = StringBuilder()
      for (i in listenAddresses.indices) {
        val listenAddress = listenAddresses[i]
        sb.append(listenAddress)
        if (i != listenAddresses.size - 1) {
          sb.append("\n\n")
        }
      }
      addressText.text = sb.toString()
    } else {
      addressText.setText(R.string.notConnected)
    }
  }

  private val listenAddresses: List<String>
    private get() {
      val service = CONNECTIVITY_SERVICE
      val cm = getSystemService(service) as ConnectivityManager
      val listenAddresses: MutableList<String> = ArrayList()
      for (network in cm.allNetworks) {
        val networkInfo = cm.getNetworkInfo(network)
        val connected = networkInfo!!.isConnected
        if (connected) {
          val linkAddresses = cm.getLinkProperties(network)!!.linkAddresses
          for (linkAddress in linkAddresses) {
            val address = linkAddress.address
            if (!address.isLinkLocalAddress && !address.isLoopbackAddress) {
              listenAddresses.add(address.hostAddress + " (" + networkInfo.typeName + ")")
            }
          }
        }
      }
      return listenAddresses
    }

  override fun onDestroy() {
    Log.i(TAG, "ChildMonitor stop")
    unregisterService()
    connectionToken = null
    if (currentSocket != null) {
      try {
        currentSocket!!.close()
      } catch (e: IOException) {
        Log.e(TAG, "Failed to close active socket on port $currentPort")
      }
    }
    super.onDestroy()
  }

  private fun registerService(port: Int) {
    val serviceInfo = NsdServiceInfo()
    serviceInfo.serviceName = "ChildMonitor"
    serviceInfo.serviceType = "_childmonitor._tcp."
    serviceInfo.port = port
    registrationListener = object : RegistrationListener {
      override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
        // Save the service name.  Android may have changed it in order to
        // resolve a conflict, so update the name you initially requested
        // with the name Android actually used.
        val serviceName = nsdServiceInfo.serviceName
        Log.i(TAG, "Service name: $serviceName")
        runOnUiThread {
          val statusText = findViewById<View>(R.id.textStatus) as TextView
          statusText.setText(R.string.waitingForParent)
          val serviceText = findViewById<View>(R.id.textService) as TextView
          serviceText.text = serviceName
          val portText = findViewById<View>(R.id.port) as TextView
          portText.text = port.toString()
        }
      }

      override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        // Registration failed!  Put debugging code here to determine why.
        Log.e(TAG, "Registration failed: $errorCode")
      }

      override fun onServiceUnregistered(arg0: NsdServiceInfo) {
        // Service has been unregistered.  This only happens when you call
        // NsdManager.unregisterService() and pass in this listener.
        Log.i(TAG, "Unregistering service")
      }

      override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        // Unregistration failed.  Put debugging code here to determine why.
        Log.e(TAG, "Unregistration failed: $errorCode")
      }
    }
    nsdManager!!.registerService(
      serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener
    )
  }

  /**
   * Uhregistered the service and assigns the listener
   * to null.
   */
  private fun unregisterService() {
    if (registrationListener != null) {
      Log.i(TAG, "Unregistering monitoring service")
      nsdManager!!.unregisterService(registrationListener)
      registrationListener = null
    }
  }

  companion object {
    const val TAG = "ChildMonitor"
  }
}