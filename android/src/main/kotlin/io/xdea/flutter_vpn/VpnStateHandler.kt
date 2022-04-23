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

import android.os.Handler
import android.os.Looper
import io.flutter.plugin.common.EventChannel
import org.strongswan.android.logic.VpnStateService

/*
	States for Flutter VPN plugin.
	These states will be sent to event channel / getState.
	DISCONNECTED = 0;
	CONNECTING = 1;
	CONNECTED = 2;
	DISCONNECTING = 3;
	ERROR = 4;

	Charon error states.
	Details of error when state shows GENERIC_ERROR.
    NO_ERROR = 0
    AUTH_FAILED = 1
    PEER_AUTH_FAILED = 2
    LOOKUP_FAILED = 3
    UNREACHABLE = 4
    GENERIC_ERROR = 5
    PASSWORD_MISSING = 6
    CERTIFICATE_UNAVAILABLE = 7
 */

object VpnStateHandler : EventChannel.StreamHandler, VpnStateService.VpnStateListener {
    // Handle event in main thread.
    private val handler = Handler(Looper.getMainLooper())
    // The charon VPN service will update state through the sink if not `null`.
    private var eventSink: EventChannel.EventSink? = null
    // Will be registered when service bound successfully.
    var vpnStateService: VpnStateService? = null

    override fun stateChanged() {
        if (vpnStateService?.errorState != VpnStateService.ErrorState.NO_ERROR)
            handler.post { eventSink?.success(4) }
        else
            eventSink?.success(vpnStateService?.state?.ordinal)
    }

    override fun onListen(p0: Any?, sink: EventChannel.EventSink) {
        eventSink = sink
    }

    override fun onCancel(p0: Any?) {
        eventSink = null
    }
}
