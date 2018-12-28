# Flutter VPN plugin

This plugin help developers to provide VPN service in their flutter app.  
本插件帮助开发者在自己的应用内提供 VPN 服务。

The Android part was implemented by [strongswan](https://www.strongswan.org/) which support ikev2 protocol.  
The iOS part not implemented yet.

### Warning
This plugin is still under initial development. DO NOT use in production.

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

Add native libs inside your `android/app/src/main/libs` .  
The native libs can be build from strongswan. 
You can also download the prebuild native libs [here](https://github.com/X-dea/Flutter_VPN/releases).

