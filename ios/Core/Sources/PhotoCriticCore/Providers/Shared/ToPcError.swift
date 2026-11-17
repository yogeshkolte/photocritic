import Foundation

/// Builds a `PcError` without classifying it (design §7, file 25).
public enum ToPcError {
    private static let providerCodeLimit = 100
    private static let requestIDLimit = 200

    public static func make(
        phase: PcPhase,
        httpStatus: Int?,
        providerCode: String?,
        requestID: String?,
        message: String,
        apiKey: String
    ) -> PcError {
        PcError(
            phase: phase,
            httpStatus: httpStatus,
            providerCode: providerCode.map { String($0.prefix(providerCodeLimit)) },
            requestID: requestID.map { String($0.prefix(requestIDLimit)) },
            detail: Redact.text(message, userKey: apiKey)
        )
    }
}
