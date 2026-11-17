import Foundation

/// The one generic message and the "Show details" labels (G12, file 25).
public enum UserMessages {
    public static let generic = "Something went wrong. Please try again."

    public static let labelWhere = "Where"
    public static let labelHTTPStatus = "HTTP status"
    public static let labelProviderCode = "Provider code"
    public static let labelRequestID = "Request ID"
    public static let labelDetails = "Details"

    public static func phase(_ phase: PcPhase) -> String {
        switch phase {
        case .beforeResponse: "Before the answer"
        case .midStream: "During the answer"
        case .finish: "At the end of the answer"
        }
    }
}
