/**
 * Copyright (C) 2018 Jason C.H
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package io.xdea.flutter_vpn

import android.app.Activity.RESULT_OK
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import org.strongswan.android.logic.CharonVpnService
import org.strongswan.android.logic.VpnStateService
import java.util.*

class FlutterVpnPlugin(private val registrar: Registrar) : MethodCallHandler {
    private var mService: VpnStateService? = null
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            mService = (service as VpnStateService.LocalBinder).service
        }
    }

    init {
        // Load charon bridge
        System.loadLibrary("androidbridge")

        registrar.activeContext().bindService(
                Intent(registrar.activeContext(), VpnStateService::class.java),
                mServiceConnection,
                Service.BIND_AUTO_CREATE
        )
        registrar.addViewDestroyListener {
            registrar.context().unbindService(mServiceConnection)
            true
        }
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
    }


    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "prepare" -> {
                val intent = VpnService.prepare(registrar.activeContext())
                if (intent != null) {
                    registrar.addActivityResultListener { req, res, _ ->
                        if (req == 0 && res == RESULT_OK)
                            result.success(null)
                        else
                            result.error("PrepareError", "Failed to prepare", false)
                        true
                    }
                    registrar.activity().startActivityForResult(intent, 0)
                }
            }
            "connect" -> {
                val intent = VpnService.prepare(registrar.activeContext())
                if (intent != null) {
                    result.error("PrepareError", "Not prepared", false)
                    return
                }
                VPNStateHandler.updateState(1)

                val map = call.arguments as HashMap<String, String>
                val address = map["address"]
                val username = map["username"]
                val password = map["password"]
                connect(address, username, password)
                result.success(null)
            }
            "getCurrentState" -> result.success(VPNStateHandler.currentState)
            "getCharonState" -> result.success(VPNStateHandler.currentCharonState)
            "disconnect" -> {
                if (VPNStateHandler.currentState == 2)
                    VPNStateHandler.updateState(3)
                disconnect()
                if (VPNStateHandler.currentState == 1)
                    VPNStateHandler.updateState(0)
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun connect(address: String?, username: String?, password: String?) {
        val profileInfo = Bundle()
        profileInfo.putString("Address", address)
        profileInfo.putString("UserName", username)
        profileInfo.putString("Password", password)
        profileInfo.putString("VpnType", "ikev2-eap")

        mService?.connect(profileInfo, true)
    }

    private fun disconnect() {
        mService?.disconnect()
    }
}
