import Flutter
import UIKit

@available(iOS 9.0, *)
public class SwiftFlutterVpnPlugin: NSObject, FlutterPlugin {
  public static func register(with registrar: FlutterPluginRegistrar) {
    let channel = FlutterMethodChannel(name: "flutter_vpn", binaryMessenger: registrar.messenger())
    let instance = SwiftFlutterVpnPlugin()
    registrar.addMethodCallDelegate(instance, channel: channel)
    channel.setMethodCallHandler({
            (call: FlutterMethodCall, result: FlutterResult) -> Void in
            if ("connect" == call.method) {
                let args = call.arguments! as! Dictionary<NSString, NSString>
                
                connectVPN(result: result, usrname: args["username"]!, pwd: args["password"]!, add: args["address"]!)
            } else if ("disconnect" == call.method) {
                stopVPN(result: result)
            }
    })
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    result("iOS " + UIDevice.current.systemVersion)
  }
}