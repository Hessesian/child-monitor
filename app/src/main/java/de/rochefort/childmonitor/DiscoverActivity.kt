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
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdManager.DiscoveryListener
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast

class DiscoverActivity : Activity() {
  val TAG = "ChildMonitor"
  var _nsdManager: NsdManager? = null
  var _discoveryListener: DiscoveryListener? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    Log.i(TAG, "ChildMonitor start")
    _nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_discover)
    val discoverChildButton = findViewById<View>(R.id.discoverChildButton) as Button
    discoverChildButton.setOnClickListener { loadDiscoveryViaMdns() }
    val enterChildAddressButton = findViewById<View>(R.id.enterChildAddressButton) as Button
    enterChildAddressButton.setOnClickListener { loadDiscoveryViaAddress() }
  }

  private fun loadDiscoveryViaMdns() {
    setContentView(R.layout.activity_discover_mdns)
    startServiceDiscovery("_childmonitor._tcp.")
  }

  private fun loadDiscoveryViaAddress() {
    setContentView(R.layout.activity_discover_address)
    val connectButton = findViewById<View>(R.id.connectViaAddressButton) as Button
    val addressField = findViewById<View>(R.id.ipAddressField) as EditText
    val portField = findViewById<View>(R.id.portField) as EditText
    val preferredAddress = getPreferences(MODE_PRIVATE).getString(PREF_KEY_CHILD_DEVICE_ADDRESS, null)
    if (!preferredAddress.isNullOrEmpty()) {
      addressField.setText(preferredAddress)
    }
    val preferredPort = getPreferences(MODE_PRIVATE).getInt(PREF_KEY_CHILD_DEVICE_PORT, -1)
    if (preferredPort > 0) {
      portField.setText(preferredPort.toString())
    }
    connectButton.setOnClickListener(View.OnClickListener {
      Log.i(TAG, "Connecting to child device via address")
      val addressString = addressField.text.toString()
      val portString = portField.text.toString()
      if (addressString.isEmpty()) {
        Toast.makeText(this@DiscoverActivity, R.string.invalidAddress, Toast.LENGTH_LONG).show()
        return@OnClickListener
      }
      var port = 0
      port = try {
        portString.toInt()
      } catch (e: NumberFormatException) {
        Toast.makeText(this@DiscoverActivity, R.string.invalidPort, Toast.LENGTH_LONG).show()
        return@OnClickListener
      }
      val preferencesEditor = getPreferences(MODE_PRIVATE).edit()
      preferencesEditor.putString(PREF_KEY_CHILD_DEVICE_ADDRESS, addressString)
      preferencesEditor.putInt(PREF_KEY_CHILD_DEVICE_PORT, port)
      preferencesEditor.apply()
      connectToChild(addressString, port, addressString)
    })
  }

  override fun onDestroy() {
    Log.i(TAG, "ChildMonitoring stop")
    if (_discoveryListener != null) {
      Log.i(TAG, "Unregistering monitoring service")
      _nsdManager!!.stopServiceDiscovery(_discoveryListener)
      _discoveryListener = null
    }
    super.onDestroy()
  }

  fun startServiceDiscovery(serviceType: String) {
    val nsdManager = this.getSystemService(NSD_SERVICE) as NsdManager
    val wifi = this.applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
    val multicastReleaser: Runnable = run {
      val multicastLock = wifi.createMulticastLock("multicastLock")
      multicastLock.setReferenceCounted(true)
      multicastLock.acquire()
      Runnable { multicastLock.release() }
    }
    val serviceTable = findViewById<View>(R.id.ServiceTable) as ListView
    val availableServicesAdapter = ArrayAdapter<ServiceInfoWrapper>(
      this,
      R.layout.available_children_list
    )
    serviceTable.adapter = availableServicesAdapter
    serviceTable.onItemClickListener = OnItemClickListener { parent: AdapterView<*>, view: View?, position: Int, id: Long ->
      val info = parent.getItemAtPosition(position) as ServiceInfoWrapper
      connectToChild(info.address, info.port, info.name)
    }

    // Instantiate a new DiscoveryListener
    _discoveryListener = object : DiscoveryListener {
      //  Called as soon as service discovery begins.
      override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
      }

      override fun onServiceFound(service: NsdServiceInfo) {
        // A service was found!  Do something with it.
        Log.d(TAG, "Service discovery success: $service")
        if (service.serviceType != serviceType) {
          // Service type is the string containing the protocol and
          // transport layer for this service.
          Log.d(TAG, "Unknown Service Type: " + service.serviceType)
        } else if (service.serviceName.contains("ChildMonitor")) {
          val resolver: NsdManager.ResolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
              // Called when the resolve fails.  Use the error code to debug.
              Log.e(TAG, "Resolve failed: error $errorCode for service: $serviceInfo")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
              Log.i(TAG, "Resolve Succeeded: $serviceInfo")
              runOnUiThread { availableServicesAdapter.add(ServiceInfoWrapper(serviceInfo)) }
            }
          }
          _nsdManager!!.resolveService(service, resolver)
        } else {
          Log.d(TAG, "Unknown Service name: " + service.serviceName)
        }
      }

      override fun onServiceLost(service: NsdServiceInfo) {
        // When the network service is no longer available.
        // Internal bookkeeping code goes here.
        Log.e(TAG, "Service lost: $service")
        multicastReleaser.run()
      }

      override fun onDiscoveryStopped(serviceType: String) {
        Log.i(TAG, "Discovery stopped: $serviceType")
        multicastReleaser.run()
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code: $errorCode")
        nsdManager.stopServiceDiscovery(this)
        multicastReleaser.run()
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Discovery failed: Error code: $errorCode")
        nsdManager.stopServiceDiscovery(this)
        multicastReleaser.run()
      }
    }
    nsdManager.discoverServices(
      serviceType, NsdManager.PROTOCOL_DNS_SD, _discoveryListener
    )
  }

  /**
   * Launch the ListenActivity to connect to the given child device
   *
   * @param address
   * @param port
   * @param name
   */
  private fun connectToChild(address: String, port: Int, name: String) {
    val i = Intent(applicationContext, ListenActivity::class.java)
    val b = Bundle()
    b.putString("address", address)
    b.putInt("port", port)
    b.putString("name", name)
    i.putExtras(b)
    startActivity(i)
  }

  companion object {
    private const val PREF_KEY_CHILD_DEVICE_ADDRESS = "childDeviceAddress"
    private const val PREF_KEY_CHILD_DEVICE_PORT = "childDevicePort"
  }
}

internal class ServiceInfoWrapper(private val _info: NsdServiceInfo) {
  val address: String
    get() = _info.host.hostAddress
  val port: Int
    get() = _info.port
  val name: String
    get() {
      // If there is more than one service on the network, it will
      // have a number at the end, but will appear as the following:
      //   "ChildMonitor\\032(number)
      // or
      //   "ChildMonitor\032(number)
      // Replace \\032 and \032 with a " "
      var serviceName = _info.serviceName
      serviceName = serviceName.replace("\\\\032", " ")
      serviceName = serviceName.replace("\\032", " ")
      return serviceName
    }

  override fun toString(): String {
    return name
  }
}