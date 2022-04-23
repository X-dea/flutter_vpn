/// Copyright (C) 2018-2022 Jason C.H
///
/// This library is free software; you can redistribute it and/or
/// modify it under the terms of the GNU Lesser General Public
/// License as published by the Free Software Foundation; either
/// version 2.1 of the License, or (at your option) any later version.
///
/// This library is distributed in the hope that it will be useful,
/// but WITHOUT ANY WARRANTY; without even the implied warranty of
/// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
/// Lesser General Public License for more details.
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'flutter_vpn_method_channel.dart';
import 'state.dart';

abstract class FlutterVpnPlatform extends PlatformInterface {
  /// Constructs a FlutterVpnPlatform.
  FlutterVpnPlatform() : super(token: _token);

  static final Object _token = Object();

  static FlutterVpnPlatform _instance = MethodChannelFlutterVpn();

  /// The default instance of [FlutterVpnPlatform] to use.
  ///
  /// Defaults to [MethodChannelFlutterVpn].
  static FlutterVpnPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [FlutterVpnPlatform] when
  /// they register themselves.
  static set instance(FlutterVpnPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Receive state change from VPN service.
  ///
  /// Can only be listened once. If have more than one subscription, only the
  /// last subscription can receive events.
  Stream<FlutterVpnState> get onStateChanged => throw UnimplementedError();

  /// Get current state.
  Future<FlutterVpnState> get currentState async => throw UnimplementedError();

  /// Get current error state from `VpnStateService`. (Android only)
  /// When [FlutterVpnState.error] is received, details of error can be
  /// inspected by [CharonErrorState]. Returns [null] on non-android platform.
  Future<CharonErrorState?> get charonErrorState async =>
      throw UnimplementedError();

  /// Prepare for vpn connection. (Android only)
  ///
  /// For first connection it will show a dialog to ask for permission.
  /// When your connection was interrupted by another VPN connection,
  /// you should prepare again before reconnect.
  Future<bool> prepare() async => throw UnimplementedError();

  /// Check if vpn connection has been prepared. (Android only)
  Future<bool> get prepared async => throw UnimplementedError();

  /// Disconnect and stop VPN service.
  Future<void> disconnect() async => throw UnimplementedError();

  /// Connect to VPN. (IKEv2-EAP)
  ///
  /// This will create a background VPN service.
  /// MTU is only available on android.
  Future<void> connectIkev2EAP({
    required String server,
    required String username,
    required String password,
    String? name,
    int? mtu,
    int? port,
  }) async =>
      throw UnimplementedError();
}
