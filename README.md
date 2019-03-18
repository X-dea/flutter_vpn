# Flutter VPN plugin

This plugin help developers to access VPN service in their flutter app.  
本插件帮助开发者在自己的应用内调用 VPN 服务。

The Android part was implemented by [strongswan](https://www.strongswan.org/) which support ikev2 protocol.  
The iOS part was implemented by NEVPNManager. (Preview)

### Warning
This plugin is still under development.
Issues and PRs are welcome!

## Installation

### For Android

Modify your `app/build.gradle` to use abiFilter because flutter doesn't support multi-architecture in single apk [#18494](https://github.com/flutter/flutter/issues/18494).
```gradle
android {
    ...
    buildTypes {
        ...
        release {
            ...
            ndk {
                    if (project.hasProperty('target-platform') &&
                            project.property('target-platform') == 'android-arm64') {
                        abiFilters 'arm64-v8a'
                    } else {
                        abiFilters 'armeabi-v7a'
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
