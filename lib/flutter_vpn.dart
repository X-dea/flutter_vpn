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

class FlutterVpn {
  static const MethodChannel _channel = const MethodChannel('flutter_vpn');

  /// Prepare for vpn connection.
  ///
  /// For first connection it will show a dialog to ask for permission.
  /// When your connection was interrupted by another VPN connection,
  /// you should prepare again before reconnection.
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
