#import "FlutterVpnPlugin.h"
#if __has_include(<flutter_vpn/flutter_vpn-Swift.h>)
#import <flutter_vpn/flutter_vpn-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "flutter_vpn-Swift.h"
#endif

@implementation FlutterVpnPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftFlutterVpnPlugin registerWithRegistrar:registrar];
}
@end
