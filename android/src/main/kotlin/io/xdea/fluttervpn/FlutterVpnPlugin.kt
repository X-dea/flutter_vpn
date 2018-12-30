/**
 * Copyright (C) 2018 Jason C.H
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package io.xdea.fluttervpn

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.support.v4.content.ContextCompat
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.strongswan.android.logic.CharonVpnService
import java.util.*

class FlutterVpnPlugin(private val registrar: Registrar) : MethodCallHandler {
  init {
    // Load charon bridge
    System.loadLibrary("androidbridge")
  }

  companion object {
    @JvmStatic
    fun registerWith(registrar: Registrar) {
      // Register method channel.
      val channel = MethodChannel(registrar.messenger(), "flutter_vpn")
      channel.setMethodCallHandler(FlutterVpnPlugin(registrar))

      // Register event channel to handle state change.
      val eventChannel = EventChannel(registrar.messenger(), "flutter_vpn_states")
      eventChannel.setStreamHandler(VPNStateHandler())
    }

    fun onPrepareResult(requestCode: Int, resultCode: Int, result: Result): Boolean {
      if (requestCode == 0 && resultCode == RESULT_OK)
        result.success(true)
      else
        result.error("PrepareError", "Failed to prepare", false)
      return true
    }
  }


  override fun onMethodCall(call: MethodCall, result: Result) {
    when (call.method) {
      "prepare" -> {
        val intent = VpnService.prepare(registrar.activeContext())
        if (intent != null) {
          registrar.addActivityResultListener { req, res, _ -> onPrepareResult(req, res, result) }
          registrar.activity().startActivityForResult(intent, 0)
        }
      }
      "connect" -> {
        val intent = VpnService.prepare(registrar.activeContext())
        if (intent != null) {
          result.error("PrepareError", "Not prepared", false)
          return
        }
        val map = call.arguments as HashMap<String, String>
        val address = map["address"]
        val username = map["username"]
        val password = map["password"]
        connect(address, username, password)
        result.success(null)
      }
      "disconnect" -> {
        disconnect()
        result.success(null)
      }
      else -> result.notImplemented()
    }
  }

  private fun connect(address: String?, username: String?, password: String?) {
    val profileInfo = Bundle()
    profileInfo.putString("address", address)
    profileInfo.putString("username", username)
    profileInfo.putString("password", password)

    val intent = Intent(registrar.activeContext(), CharonVpnService::class.java)
            .putExtras(profileInfo)
    ContextCompat.startForegroundService(registrar.activeContext(), intent)
  }

  private fun disconnect() {
    /* as soon as the TUN device is created by calling establish() on the
     * VpnService.Builder object the system binds to the service and keeps
     * bound until the file descriptor of the TUN device is closed.  thus
     * calling stopService() here would not stop (destroy) the service yet,
     * instead we call startService() with a specific action which shuts down
     * the daemon (and closes the TUN device, if any) */
    val intent = Intent(registrar.activeContext(), CharonVpnService::class.java)
    intent.action = CharonVpnService.DISCONNECT_ACTION
    registrar.activeContext().startService(intent)
  }
}
