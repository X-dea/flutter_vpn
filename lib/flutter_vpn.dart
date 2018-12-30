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

import 'dart:async';

import 'package:flutter/services.dart';

const _channel = const MethodChannel('flutter_vpn');
const _eventChannel = const EventChannel('flutter_vpn_states');

enum FlutterVpnState {
  up,
  down,
  authError,
  peerAuthError,
  lookUpError,
  unreachableError,
  certificateUnavailable,
  genericError
}

class FlutterVpn {
  /// Receive state change from charon VPN service.
  ///
  /// Can only be listened once.
  /// If more than one, only the last subscription will receive events.
  static Stream<FlutterVpnState> get onStateChanged =>
      _eventChannel.receiveBroadcastStream().map((event) {
        switch (event) {
          case 1:
            return FlutterVpnState.up;
          case 2:
            return FlutterVpnState.down;
          case 3:
            return FlutterVpnState.authError;
          case 4:
            return FlutterVpnState.peerAuthError;
          case 5:
            return FlutterVpnState.lookUpError;
          case 6:
            return FlutterVpnState.unreachableError;
          case 7:
            return FlutterVpnState.certificateUnavailable;
          case 8:
            return FlutterVpnState.genericError;
        }
      });

  /// Prepare for vpn connection.
  ///
  /// For first connection it will show a dialog to ask for permission.
  /// When your connection was interrupted by another VPN connection,
  /// you should prepare again before reconnect.
  static Future<bool> prepare() async {
    return await _channel.invokeMethod('prepare');
  }

  /// Connect to VPN.
  ///
  /// Use given credentials to connect VPN (ikev2-eap).
  /// This will create a background VPN service.
  static Future<Null> simpleConnect(
      String address, String username, String password) async {
    await _channel.invokeMethod('connect',
        {'address': address, 'username': username, 'password': password});
  }

  /// Disconnect will stop current VPN service.
  static Future<Null> disconnect() async {
    await _channel.invokeMethod('disconnect');
  }
}
