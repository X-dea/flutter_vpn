/**
 * Copyright (C) 2019 Jerry Wang, Jason C.H
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

import Foundation
import NetworkExtension
import Security


enum FlutterVpnState: Int {
    case disconnected = 0;
    case connecting = 1;
    case connected = 2;
    case disconnecting = 3;
    case error = 4;
}


class VpnService {
    // MARK: - Singleton
    static let shared: VpnService = {
        let instance = VpnService()
        return instance
    }()


    // MARK: - Few variables
    var vpnManager: NEVPNManager {
        get {
            return NEVPNManager.shared()
        }
    }
    var vpnStatus: NEVPNStatus {
        get {
            return vpnManager.connection.status
        }
    }
    let kcs = KeychainService()
    var configurationSaved = false


    // MARK: - Init
    init() {
        NotificationCenter.default.addObserver(forName: NSNotification.Name.NEVPNStatusDidChange, object: nil, queue: OperationQueue.main, using: statusChanged)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }


    // MARK: - Methods
    @available(iOS 9.0, *)
    func connect(
        result: FlutterResult,
        type: String,
        server: String,
        username: String,
        password: String,
        secret: String?,
        description: String?
    ) {
        vpnManager.loadFromPreferences { (error) -> Void in
            guard error == nil else {
                let msg = "VPN Preferences error: \(error!.localizedDescription)"
                debugPrint(msg)
                VPNStateHandler.updateState(FlutterVpnState.error.rawValue, errorMessage: msg)
                return;
            }

            let passwordKey = "vpn_\(type)_password"
            let secretKey = "vpn_\(type)_secret"
            self.kcs.save(key: passwordKey, value: password)
            if let secret = secret {
                self.kcs.save(key: secretKey, value: secret)
            }

            if (type == "IPSec") {
                let p = NEVPNProtocolIPSec()
                p.serverAddress = server
                p.username = username
                p.passwordReference = self.kcs.load(key: passwordKey)

                p.authenticationMethod = NEVPNIKEAuthenticationMethod.sharedSecret
                if secret != nil {
                    p.sharedSecretReference = self.kcs.load(key: secretKey)
                }

                p.localIdentifier = ""
                p.remoteIdentifier = ""

                p.useExtendedAuthentication = true
                p.disconnectOnSleep = false
                self.vpnManager.protocolConfiguration = p
            } else {
                let p = NEVPNProtocolIKEv2()
                p.username = username
                p.remoteIdentifier = server
                p.serverAddress = server

                p.passwordReference = self.kcs.load(key: passwordKey)
                p.authenticationMethod = NEVPNIKEAuthenticationMethod.none

                p.useExtendedAuthentication = true
                p.disconnectOnSleep = false
                self.vpnManager.protocolConfiguration = p
            }

            self.vpnManager.localizedDescription = description
            self.vpnManager.isOnDemandEnabled = false
            self.vpnManager.isEnabled = true

            self.vpnManager.saveToPreferences(completionHandler: { (error) -> Void in
                guard error == nil else {
                    let msg = "VPN Preferences error: \(error!.localizedDescription)"
                    debugPrint(msg)
                    VPNStateHandler.updateState(FlutterVpnState.error.rawValue, errorMessage: msg)
                    return;
                }

                self.vpnManager.loadFromPreferences(completionHandler: { error in
                    guard error == nil else {
                        let msg = "VPN Preferences error: \(error!.localizedDescription)"
                        debugPrint(msg)
                        VPNStateHandler.updateState(FlutterVpnState.error.rawValue, errorMessage: msg)
                        return;

                    }

                    self.configurationSaved = true
                    self.startTunnel()
                })
            })
        }
        result(nil)
    }

    func startTunnel() {
        do {
            try self.vpnManager.connection.startVPNTunnel()
        } catch let error as NSError {
            var errorStr = ""
            switch error {
            case NEVPNError.configurationDisabled:
                errorStr = "The VPN configuration associated with the NEVPNManager is disabled."
                break
            case NEVPNError.configurationInvalid:
                errorStr = "The VPN configuration associated with the NEVPNManager object is invalid."
                break
            case NEVPNError.configurationReadWriteFailed:
                errorStr = "An error occurred while reading or writing the Network Extension preferences."
                break
            case NEVPNError.configurationStale:
                errorStr = "The VPN configuration associated with the NEVPNManager object was modified by some other process since the last time that it was loaded from the Network Extension preferences by the app."
                break
            case NEVPNError.configurationUnknown:
                errorStr = "An unspecified error occurred."
                break
            case NEVPNError.connectionFailed:
                errorStr = "The connection to the VPN server failed."
                break
            default:
                errorStr = "Unknown error: \(error.localizedDescription)"
                break
            }

            let msg = "Start error: \(errorStr)"
            debugPrint(msg)
            VPNStateHandler.updateState(FlutterVpnState.error.rawValue, errorMessage: msg)
            return;
        }
    }

    func reconnect(result: FlutterResult) {
        guard self.configurationSaved == true else {
            result(FlutterError(code: "-1",
                                message: "Configuration is not yet saved",
                                details: nil))
            return
        }

        result(nil)
    }

    func disconnect(result: FlutterResult) {
        vpnManager.connection.stopVPNTunnel()
        result(nil)
    }

    func getState(result: FlutterResult) {
        switch vpnStatus {
        case .connecting:
            result(FlutterVpnState.connecting.rawValue)
            break
        case .connected:
            result(FlutterVpnState.connected.rawValue)
            break
        case .disconnecting:
            result(FlutterVpnState.disconnecting.rawValue)
            break
        case .disconnected:
            result(FlutterVpnState.disconnected.rawValue)
            break
        case .invalid:
            result(FlutterVpnState.error.rawValue)
            break
        case .reasserting:
            result(FlutterVpnState.connecting.rawValue)
            break
        @unknown default:
            debugPrint("Unknown switch statement: \(vpnStatus)")
            break
        }
    }


    // MARK: - Event callbacks
    func statusChanged(_: Notification?) {
        switch vpnStatus {
        case .connected:
            VPNStateHandler.updateState(FlutterVpnState.connected.rawValue)
            break

        case .disconnected:
            VPNStateHandler.updateState(FlutterVpnState.disconnected.rawValue)
            break

        case .connecting:
            VPNStateHandler.updateState(FlutterVpnState.connecting.rawValue)
            break

        case .disconnecting:
            VPNStateHandler.updateState(FlutterVpnState.disconnecting.rawValue)
            break

        case .invalid:
            VPNStateHandler.updateState(FlutterVpnState.error.rawValue)
            break

        case .reasserting:
            VPNStateHandler.updateState(FlutterVpnState.connecting.rawValue)
            break

        @unknown default:
            debugPrint("Unknown switch statement: \(vpnStatus)")
            break
        }
    }
}
