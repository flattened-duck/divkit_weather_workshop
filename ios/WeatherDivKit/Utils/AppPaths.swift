import Foundation

enum AppPaths {
    static let cacheFilePrefix = "doc_cache_"
    static func cacheFileName(lang: String) -> String { "\(cacheFilePrefix)\(lang).json" }
    static let bundledSkeletonResource = "zero_ru"
    static let storedValuesFileName = "divkit.values_storage"
}
