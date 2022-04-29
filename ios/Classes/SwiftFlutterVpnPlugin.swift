/**
 * Copyright (C) 2019 Jerry Wang, Jason C.H
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

import Flutter
import UIKit

@available(iOS 9.0, *)
public class SwiftFlutterVpnPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_vpn", binaryMessenger: registrar.messenger())
    let stateChannel = FlutterEventChannel(name: "flutter_vpn_states", binaryMessenger: registrar.messenger())

    let instance = SwiftFlutterVpnPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    stateChannel.setStreamHandler((VPNStateHandler() as! FlutterStreamHandler & NSObjectProtocol))

    channel.setMethodCallHandler {
      (call: FlutterMethodCall, result: FlutterResult) -> Void in
      if call.method == "connect" {
        let args = call.arguments! as! [NSString: NSString]
        VpnService.shared.connect(
          result: result,
          type: (args["Type"] as? String ?? "IKEv2"),
          server: args["Server"]! as String,
          username: args["Username"]! as String,
          password: args["Password"]! as String,
          secret: args["Secret"] as? String,
          description: args["Name"] as? String
        )
      } else if call.method == "reconnect" {
        VpnService.shared.reconnect(result: result)
      } else if call.method == "disconnect" {
        VpnService.shared.disconnect(result: result)
      } else if call.method == "getCurrentState" {
        VpnService.shared.getState(result: result)
      }
    }
  }
}
