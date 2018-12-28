#import "FlutterVpnPlugin.h"
#import <flutter_vpn/flutter_vpn-Swift.h>

@implementation FlutterVpnPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterVpnPlugin registerWithRegistrar:registrar];
}
@end
