import Foundation
import NetworkExtension
import Security

// Identifiers
let serviceIdentifier = "MySerivice"
let userAccount = "authenticatedUser"
let accessGroup = "MySerivice"

// Arguments for the keychain queries
var kSecAttrAccessGroupSwift = NSString(format: kSecClass)

let kSecClassValue = kSecClass as CFString
let kSecAttrAccountValue = kSecAttrAccount as CFString
let kSecValueDataValue = kSecValueData as CFString
let kSecClassGenericPasswordValue = kSecClassGenericPassword as CFString
let kSecAttrServiceValue = kSecAttrService as CFString
let kSecMatchLimitValue = kSecMatchLimit as CFString
let kSecReturnDataValue = kSecReturnData as CFString
let kSecMatchLimitOneValue = kSecMatchLimitOne as CFString
let kSecAttrGenericValue = kSecAttrGeneric as CFString
let kSecAttrAccessibleValue = kSecAttrAccessible as CFString

class KeychainService: NSObject {
    func save(key:String, value:String) {
        let keyData: Data = key.data(using: String.Encoding(rawValue: String.Encoding.utf8.rawValue), allowLossyConversion: false)!
        let valueData: Data = value.data(using: String.Encoding(rawValue: String.Encoding.utf8.rawValue), allowLossyConversion: false)!
        
        let keychainQuery = NSMutableDictionary();
        keychainQuery[kSecClassValue as! NSCopying] = kSecClassGenericPasswordValue
        keychainQuery[kSecAttrGenericValue as! NSCopying] = keyData
        keychainQuery[kSecAttrAccountValue as! NSCopying] = keyData
        keychainQuery[kSecAttrServiceValue as! NSCopying] = "VPN"
        keychainQuery[kSecAttrAccessibleValue as! NSCopying] = kSecAttrAccessibleAlwaysThisDeviceOnly
        keychainQuery[kSecValueData as! NSCopying] = valueData;
        // Delete any existing items
        SecItemDelete(keychainQuery as CFDictionary)
        SecItemAdd(keychainQuery as CFDictionary, nil)
    }
    
    func load(key: String)->Data {
        
        let keyData: Data = key.data(using: String.Encoding(rawValue: String.Encoding.utf8.rawValue), allowLossyConversion: false)!
        let keychainQuery = NSMutableDictionary();
        keychainQuery[kSecClassValue as! NSCopying] = kSecClassGenericPasswordValue
        keychainQuery[kSecAttrGenericValue as! NSCopying] = keyData
        keychainQuery[kSecAttrAccountValue as! NSCopying] = keyData
        keychainQuery[kSecAttrServiceValue as! NSCopying] = "VPN"
        keychainQuery[kSecAttrAccessibleValue as! NSCopying] = kSecAttrAccessibleAlwaysThisDeviceOnly
        keychainQuery[kSecMatchLimit] = kSecMatchLimitOne
        keychainQuery[kSecReturnPersistentRef] = kCFBooleanTrue
        
        var result: AnyObject?
        let status = withUnsafeMutablePointer(to: &result) { SecItemCopyMatching(keychainQuery, UnsafeMutablePointer($0)) }
        
        
        if status == errSecSuccess {
            if let data = result as! NSData? {
                if let value = NSString(data: data as Data, encoding: String.Encoding.utf8.rawValue) {
                }
                return data as Data;
            }
        }
        return "".data(using: .utf8)!;
    }
}

@available(iOS 9.0, *)
func connectVPN(result: FlutterResult, usrname: NSString, pwd: NSString, add: NSString){
    let vpnManager = NEVPNManager.shared()
    let kcs = KeychainService()
    vpnManager.loadFromPreferences { (error) -> Void in
    
        if((error) != nil) {
            print("VPN Preferences error: 1")
        }
        else {
            
            let p = NEVPNProtocolIKEv2()
            
            p.username = usrname as String
            p.remoteIdentifier = add as String
            p.serverAddress = add as String
            
            kcs.save(key: "password", value: pwd as String)
            p.passwordReference = kcs.load(key: "password")
            p.authenticationMethod = NEVPNIKEAuthenticationMethod.none
            
            p.useExtendedAuthentication = true
            p.disconnectOnSleep = false
            
            vpnManager.protocolConfiguration = p
            vpnManager.isEnabled = true
            
            vpnManager.saveToPreferences(completionHandler: { (error) -> Void in
                if((error) != nil) {
                    print("VPN Preferences error: 2")
                }
                else {
                    
                    vpnManager.loadFromPreferences(completionHandler: { (error) in
                        
                        if((error) != nil) {
                            
                            print("VPN Preferences error: 2")
                        }
                        else {
                            
                            var startError: NSError?
                            
                            do {
                                try vpnManager.connection.startVPNTunnel()
                            }
                            catch let error as NSError {
                                startError = error
                                print(startError)
                            }
                            catch {
                                print("Fatal Error")
                                fatalError()
                            }
                            if((startError) != nil) {
                                print("VPN Preferences error: 3")
                                print(startError)
                            }
                            else {
                                print("VPN started successfully..")
                            }
                        }
                    })
                }
            })
        }
    }
}

func stopVPN(result: FlutterResult){
    let vpnManager = NEVPNManager.shared()
    vpnManager.connection.stopVPNTunnel()
    result(0)
}

func getVPNState(result: FlutterResult) {
    let vpnManager = NEVPNManager.shared()
    let status = vpnManager.connection.status
    switch status {
    case .connecting:
        result(1)
        break
    case .connected:
        result(2)
        break
    case .disconnecting:
        result(3)
        break
    case .disconnected:
        result(0)
        break
    case .invalid:
        result(4)
        break
    case .reasserting:
        result(4)
        break
    }
}
