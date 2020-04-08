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

Modify your `app/build.gradle` to use abiFilter since flutter doesn't apply abiFilter for target platform yet.
```gradle
android {
    ...
    buildTypes {
        ...
        release {
            ...
            ndk {
                if (!project.hasProperty('target-platform')) {
                    abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86_64'
                } else {
                    def platforms = project.property('target-platform').split(',')
                    def platformMap = [
                            'android-arm'  : 'armeabi-v7a',
                            'android-arm64': 'arm64-v8a',
                            'android-x86'  : 'x86',
                            'android-x64'  : 'x86_64',
                    ]
                    abiFilters = platforms.stream().map({ e ->
                        platformMap.containsKey(e) ? platformMap[e] : e
                    }).toArray()
                }
            }
    }
}
```
The plugin will automatically download pre-build native libraries from [here](https://github.com/X-dea/Flutter_VPN/releases) if they haven't been downloaded.

### For iOS

You need to open `Personal VPN` and `Network Extensions` capabilities in Xcode: see Project->Capabilities.

VPN connection errors are handled in swift code, you need to use Xcode to see connection errors if there is any.
