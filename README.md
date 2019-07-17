# Flutter VPN plugin

<a href="https://pub.dartlang.org/packages/flutter_vpn">
    <img src="https://img.shields.io/pub/v/flutter_vpn.svg"
    alt="Pub Package" />
</a>
<a href="https://github.com/Solido/awesome-flutter">
   <img alt="Awesome Flutter" src="https://img.shields.io/badge/Awesome-Flutter-blue.svg?longCache=true&style=flat-square" />
</a>

This plugin help developers to access VPN service in their flutter app.  
本插件帮助开发者在自己的应用内调用 VPN 服务。

The Android part was implemented by [strongswan](https://www.strongswan.org/) which support ikev2 protocol.  
The iOS part was implemented by NEVPNManager.

Issues and PRs are welcome!

## Installation

### For Android

Modify your `app/build.gradle` to use abiFilter because flutter doesn't apply abiFilter for target platform yet.
```gradle
android {
    ...
    buildTypes {
        ...
        release {
            ...
            ndk {
                    if (project.hasProperty('target-platform')) {
                        if (project.property('target-platform') == 'android-arm,android-arm64')
                            abiFilters 'armeabi-v7a', 'arm64-v8a'
                        else if (project.property('target-platform') == 'android-arm')
                            abiFilters 'armeabi-v7a'
                        else if (project.property('target-platform') == 'android-arm64')
                            abiFilters 'arm64-v8a'
                    }
                }
            }
    }
}
```
The plugin will automatically download pre-build native libraries from [here](https://github.com/X-dea/Flutter_VPN/releases) if they haven't been downloaded.

### For iOS

You need to open `Personal VPN` and `Network Extensions` capabilities in Xcode: see Project->Capabilities.

VPN connection errors are handled in swift code, you need to use Xcode to see connection errors if there is any.
