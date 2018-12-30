# Flutter VPN plugin

This plugin help developers to access VPN service in their flutter app.  
本插件帮助开发者在自己的应用内调用 VPN 服务。

The Android part was implemented by [strongswan](https://www.strongswan.org/) which support ikev2 protocol.  
The iOS part not implemented yet.

### Warning
This plugin is still under initial development.  
Issues and PRs are welcome!

## Installation

### For Android

Add the service in the `application` part your `AndroidManifest.xml`.
```xml
<application
        ...
    <activity
        ...
    </activity>
    <service android:name="org.strongswan.android.logic.CharonVpnService"
        android:permission="android.permission.BIND_VPN_SERVICE"/>
</application>
```
Modify your `app/build.gradle` to use Java 8 and avoid [#22397](https://github.com/flutter/flutter/issues/22397).
```gradle
android {
    ...
    lintOptions {
        ...
        // To avoid error.
        checkReleaseBuilds false
    }

    // Plugin requires Java 8.
    compileOptions {
        targetCompatibility 1.8
        sourceCompatibility 1.8
    }
}
```
The plugin will automatically download pre-build native libraries from [here](https://github.com/X-dea/Flutter_VPN/releases) if they haven't been downloaded.
