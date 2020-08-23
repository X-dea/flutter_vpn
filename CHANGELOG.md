## 0.9.0
- Upgrade to StrongSwan 5.9.0.
- Add `prepared` for checking vpn preparation on android.

## 0.8.0
- Fix crash on launch caused by abiFilters. (#45)

**Breaking Changes**
- Support Android embedding v2 (v1 is nolonger supported).
- Please update abiFilters according to the description in README.

## 0.7.0
- Add MTU for Android.
- Fix service unbinding. (#27)

## 0.6.0
- Update to StrongSwan 5.8.1.
- Use original notification from StrongSwan frontend.
- Automatically retry when a error occured.

**BreakingChange**

- In order to compatible with original `VpnStateService`, `CharonVpnState` has been changed to `CharonErrorState` that shows detail kind of error when a generic error is received.

## 0.5.0
- Fix (#15) event handler for android (Flutter 1.6+).

## 0.4.0
- Fix state error if disconnect while connecting.
- Add iOS state handler.

## 0.3.0
- Add `getVpnState` for iOS.

## 0.2.0
- Add `getVpnState` and `getCharonState` for Android.

**Breaking Change**

- Old `FlutterVpnState` has been renamed to `CharonVpnState` which is for Android only. New `FlutterVpnState` is designed for both Android and iOS platform.

## 0.1.0
- Support `arm64-v8a` for android. Please follow `README` to configure abiFilter for NDK.

**Breaking Change**

- Migrate to AndroidX

> Migrate from the deprecated original Android Support Library to AndroidX. This shouldn't result in any functional changes, but it requires any Android apps using this plugin to also migrate if they're using the original support library.
> Follow [Official documents](https://developer.android.com/jetpack/androidx/migrate) to migrate.

## 0.0.4
- Add iOS support without status broadcast.

## 0.0.3
- Add `onStateChanged` to receive state changes from charon.

## 0.0.2 (Deprecated)
- Implemented simplest IkeV2-eap VPN service.
- Automatically download native libs before building.
