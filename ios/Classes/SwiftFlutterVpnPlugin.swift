import Flutter
import UIKit

public class SwiftFlutterVpnPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_vpn", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterVpnPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    channel.setMethodCallHandler({
            (call: FlutterMethodCall, result: FlutterResult) -> Void in
            if ("connect" == call.method) {
                connectVPN(result: result, usrname: call.arguments['username'], pwd: call.arguments['password'], add: call.arguments['address'])
            } else if ("disconnect" == call.method) {
                stopVPN(result: result)
            }
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}
