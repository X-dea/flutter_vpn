## 0.2.0
Add `getVpnState` and `getCharonState` for Android.
**Breaking Change**  
Old `FlutterVpnState` has been renamed to `CharonVpnState`. This method is for Android only.  
New `FlutterVpnState` is designed for both Android and iOS platform.

## 0.1.0
### Support `arm64-v8a` for android
We have added the libraries for `arm64-v8a`.
Please follow `README` to configure abiFilter for NDK.
### Migrate to AndroidX
**Breaking Change**  
Migrate from the deprecated original Android Support Library to AndroidX. This shouldn't result in any functional changes, but it requires any Android apps using this plugin to also migrate if they're using the original support library.
Follow [Official documents](https://developer.android.com/jetpack/androidx/migrate) to migrate.

## 0.0.4
Add iOS support without status broadcast.

## 0.0.3
Add `onStateChanged` to receive state changes from charon.

## 0.0.2
(Deprecated)
Implemented simplest IkeV2-eap VPN service.
Automatically download native libs before building.
