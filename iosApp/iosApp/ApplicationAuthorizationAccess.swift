import PosatoShared

enum ApplicationAuthorizationAnswer {
    case approved
    case notDetermined
    case denied
    case restricted
    case unavailable
}

struct ApplicationAuthorizationAccess {
    static func access(for answer: ApplicationAuthorizationAnswer) -> IosApplicationMappingsAccess {
        switch answer {
        case .approved:
            return .ready
        case .notDetermined:
            return .authorizationRequired
        case .denied:
            return .authorizationDenied
        case .restricted:
            return .restricted
        case .unavailable:
            return .unavailable
        }
    }
}
