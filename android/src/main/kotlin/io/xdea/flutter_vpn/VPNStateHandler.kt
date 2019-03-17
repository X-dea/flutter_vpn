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

package io.xdea.flutter_vpn

import io.flutter.plugin.common.EventChannel

/*
  States for Charon VPN service.
	STATE_CHILD_SA_UP = 1;
	STATE_CHILD_SA_DOWN = 2;
	STATE_AUTH_ERROR = 3;
	STATE_PEER_AUTH_ERROR = 4;
	STATE_LOOKUP_ERROR = 5;
	STATE_UNREACHABLE_ERROR = 6;
	STATE_CERTIFICATE_UNAVAILABLE = 7;
	STATE_GENERIC_ERROR = 8;

	States for Flutter VPN plugin.
	These states will be sent to event channel / getStatus.
	DISCONNECTED = 0;
	CONNECTING = 1;
	CONNECTED = 2;
	DISCONNECTING = 3;
	GENERIC_ERROR = 4;
 */

class VPNStateHandler : EventChannel.StreamHandler {

  companion object {
    /**
     * The charon VPN service will update state through the sink if not `null`.
     */
    var eventHandler: EventChannel.EventSink? = null
    var currentCharonState = 0
    var currentState = 0

    fun updateState(newCharonState: Int) {
      currentCharonState = newCharonState

      // Map Charon state to Normal state.
      when (newCharonState) {
        1 -> currentState = 2
        2 -> currentState = 0
        else -> currentState = 4
      }

      eventHandler?.success(currentState)
    }
  }

  override fun onListen(p0: Any?, sink: EventChannel.EventSink) {
    eventHandler = sink
  }

  override fun onCancel(p0: Any?) {
    eventHandler = null
  }
}
