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
import org.strongswan.android.logic.VpnStateService

class FlutterVpnPlugin(private val registrar: Registrar) : MethodCallHandler {
    private var _shouldUnbind: Boolean = false
    private var _vpnStateService: VpnStateService? = null
    private val _serviceConnection = object : ServiceConnection {
        override fun onServiceDisconnected(name: ComponentName) {
            _vpnStateService = null
            VpnStateHandler.vpnStateService = null
        }

        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            _vpnStateService = (service as VpnStateService.LocalBinder).service
            VpnStateHandler.vpnStateService = _vpnStateService
            _vpnStateService?.registerListener(VpnStateHandler)
        }
    }

    init {
        // Load charon bridge
        System.loadLibrary("androidbridge")

        // Start and bind VpnStateService to current context.
        if (registrar.activeContext().bindService(
                        Intent(registrar.activeContext(), VpnStateService::class.java),
                        _serviceConnection,
                        Service.BIND_AUTO_CREATE
                )) {
            _shouldUnbind = true
        }
        registrar.addViewDestroyListener {
            if (_shouldUnbind) {
                registrar.activeContext().unbindService(_serviceConnection)
                _shouldUnbind = false
            }
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
            eventChannel.setStreamHandler(VpnStateHandler)
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

                val map = call.arguments as HashMap<String, String>

                val profileInfo = Bundle()
                profileInfo.putString("Address", map["address"])
                profileInfo.putString("UserName", map["username"])
                profileInfo.putString("Password", map["password"])
                profileInfo.putString("VpnType", "ikev2-eap")
                profileInfo.putInt("MTU", map["mtu"]?.toInt() ?: 1400)

                _vpnStateService?.connect(profileInfo, true)
                result.success(null)
            }
            "getCurrentState" -> {
                if (_vpnStateService?.errorState != VpnStateService.ErrorState.NO_ERROR)
                    result.success(4)
                else
                    result.success(_vpnStateService?.state?.ordinal)
            }
            "getCharonErrorState" -> result.success(_vpnStateService?.errorState?.ordinal)
            "disconnect" -> _vpnStateService?.disconnect()
            else -> result.notImplemented()
        }
    }
}