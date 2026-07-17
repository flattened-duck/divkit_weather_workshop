import os

enum Log {
    private static let logger = Logger(subsystem: "com.example.weatherdivkit", category: "app")
    static func info(_ m: String) { logger.info("\(m, privacy: .public)") }
    static func warn(_ m: String) { logger.warning("\(m, privacy: .public)") }
    static func error(_ m: String) { logger.error("\(m, privacy: .public)") }
}
