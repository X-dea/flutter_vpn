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

import 'dart:io';

import 'package:flutter/services.dart';

const _channel = const MethodChannel('flutter_vpn');
const _eventChannel = const EventChannel('flutter_vpn_states');

/// The generic VPN state for all platforms.
enum FlutterVpnState {
  disconnected,
  connecting,
  connected,
  disconnecting,
  genericError
}

/// The VPN state for `CharonVpnService`.
/// Only available for Android device.
enum CharonVpnState {
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
  /// If have more than one subscription, only the last subscription can receive
  /// events.
  static Stream<FlutterVpnState> get onStateChanged =>
      _eventChannel.receiveBroadcastStream().map((e) => _mapState(e));

  /// Get current state.
  static Future<FlutterVpnState> get currentState async {
    var currentState = await _channel.invokeMethod('getCurrentState');
    return _mapState(currentState);
  }

  /// Get current state from `CharonVpnService`.
  /// Only available for Android devices.
  static Future<CharonVpnState> get currentCharonState async {
    if (!Platform.isAndroid) throw Exception('Unsupport Platform');
    var currentState = await _channel.invokeMethod('getCharonState');
    return _mapCharonState(currentState);
  }

  /// Prepare for vpn connection. (Android only)
  ///
  /// For first connection it will show a dialog to ask for permission.
  /// When your connection was interrupted by another VPN connection,
  /// you should prepare again before reconnect.
  ///
  /// Do nothing in iOS.
  static Future<Null> prepare() async {
    if (!Platform.isAndroid) return Null;
    return await _channel.invokeMethod('prepare');
  }

  /// Connect to VPN.
  ///
  /// Use given credentials to connect VPN (ikev2-eap).
  /// This will create a background VPN service.
  static Future<Null> simpleConnect(String address, String username,
      String password) async {
    await _channel.invokeMethod('connect',
        {'address': address, 'username': username, 'password': password});
  }

  /// Disconnect will stop current VPN service.
  static Future<Null> disconnect() async {
    await _channel.invokeMethod('disconnect');
  }

  /// Map state code to `FlutterVpnState`.
  static FlutterVpnState _mapState(int state) {
    switch (state) {
      case 0:
        return FlutterVpnState.disconnected;
      case 1:
        return FlutterVpnState.connecting;
      case 2:
        return FlutterVpnState.connected;
      case 3:
        return FlutterVpnState.disconnecting;
      default:
        return FlutterVpnState.genericError;
    }
  }

  /// Map Charon state code to `CharonVpnState`.
  static CharonVpnState _mapCharonState(int charonState) {
    switch (charonState) {
      case 1:
        return CharonVpnState.up;
      case 2:
        return CharonVpnState.down;
      case 3:
        return CharonVpnState.authError;
      case 4:
        return CharonVpnState.peerAuthError;
      case 5:
        return CharonVpnState.lookUpError;
      case 6:
        return CharonVpnState.unreachableError;
      case 7:
        return CharonVpnState.certificateUnavailable;
      default:
        return CharonVpnState.genericError;
    }
  }
}
