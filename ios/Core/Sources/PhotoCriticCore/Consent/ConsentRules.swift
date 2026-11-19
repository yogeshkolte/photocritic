import Foundation

/// The consent gate (G13, file 31): a provider whose data leaves the device
/// needs consent for the current consent-text version before a send.
public enum ConsentRules {
    public static func canSend(_ capabilities: Capabilities, grantedVersion: Int?, requiredVersion: Int) -> Bool {
        !capabilities.dataLeavesDevice || (grantedVersion ?? 0) >= requiredVersion
    }
}
