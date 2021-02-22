/**
 * Copyright (C) 2018-2020 Jason C.H
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
import androidx.annotation.NonNull
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import org.strongswan.android.logic.VpnStateService
import io.flutter.plugin.common.PluginRegistry

/** FlutterVpnPlugin */
class FlutterVpnPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private lateinit var activityBinding: ActivityPluginBinding

    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel
    private lateinit var eventChannel: EventChannel

    private var vpnStateService: VpnStateService? = null
    private val _serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            vpnStateService = (service as VpnStateService.LocalBinder).service
            VpnStateHandler.vpnStateService = vpnStateService
            vpnStateService?.registerListener(VpnStateHandler)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            vpnStateService = null
            VpnStateHandler.vpnStateService = null
        }
    }

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        // Load charon bridge
        System.loadLibrary("androidbridge")

        // Register method channel.
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "flutter_vpn")
        channel.setMethodCallHandler(this);

        // Register event channel to handle state change.
        eventChannel = EventChannel(flutterPluginBinding.binaryMessenger, "flutter_vpn_states")
        eventChannel.setStreamHandler(VpnStateHandler)

        flutterPluginBinding.applicationContext.bindService(
                Intent(flutterPluginBinding.applicationContext, VpnStateService::class.java),
                _serviceConnection,
                Service.BIND_AUTO_CREATE
        )
    }

    override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        eventChannel.setStreamHandler(null)
    }


    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivity() {
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activityBinding = binding
    }

    override fun onDetachedFromActivityForConfigChanges() {
    }

    override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
        when (call.method) {
            "prepare" -> {
                val intent = VpnService.prepare(activityBinding.activity.applicationContext)
                if (intent != null) {
                    var listener: PluginRegistry.ActivityResultListener? = null
                    listener = PluginRegistry.ActivityResultListener { req, res, _ ->
                        if (req == 0 && res == RESULT_OK) {
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                        listener?.let { activityBinding.removeActivityResultListener(it) };
                        true
                    }
                    activityBinding.addActivityResultListener(listener)
                    activityBinding.activity.startActivityForResult(intent, 0)
                } else {
                    // If intent is null, already prepared
                    result.success(true)
                }
            }
            "prepared" -> {
                val intent = VpnService.prepare(activityBinding.activity.applicationContext)
                result.success(intent == null)
            }
            "connect" -> {
                val intent = VpnService.prepare(activityBinding.activity.applicationContext)
                if (intent != null) {
                    // Not prepared yet
                    result.success(false)
                    return
                }

                val map = call.arguments as HashMap<*, *>

                val profileInfo = Bundle()
                profileInfo.putString("VpnType", "ikev2-eap")
                profileInfo.putString("Name", map["name"] as String)
                profileInfo.putString("Server", map["server"] as String)
                profileInfo.putString("Username", map["username"] as String)
                profileInfo.putString("Password", map["password"] as String)
                profileInfo.putInt("MTU", map["mtu"] as? Int ?: 1400)
                if (map.containsKey("port"))
                    profileInfo.putInt("Port", map["port"] as Int)

                vpnStateService?.connect(profileInfo, true)
                result.success(true)
            }
            "getCurrentState" -> {
                if (vpnStateService?.errorState != VpnStateService.ErrorState.NO_ERROR)
                    result.success(4)
                else
                    result.success(vpnStateService?.state?.ordinal)
            }
            "getCharonErrorState" -> result.success(vpnStateService?.errorState?.ordinal)
            "disconnect" -> vpnStateService?.disconnect()
            else -> result.notImplemented()
        }
    }
}
