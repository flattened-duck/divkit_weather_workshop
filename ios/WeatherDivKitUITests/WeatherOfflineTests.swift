import XCTest

/// Proves the offline last-resort fallback (bundled zero_ru.json skeleton) renders when the
/// network is unreachable AND there is no cache. `resetState: true` wipes doc_cache_* so phase 1
/// of coldStart falls all the way through to `loadBundledSkeleton()`. Mirrors WeatherOfflineTest.kt
/// + WeatherZeroSkeletonTest.kt (`zero_skeleton_whenOfflineNoCache`). No requireBackendUp: this
/// suite deliberately does not depend on :8080.
final class WeatherOfflineTests: BaseUITest {

    override func setUp() {
        super.setUp()
        launch(baseURL: "http://127.0.0.1:1") // dead port; reset ON wipes doc caches
    }

    func test_offline_fallsBackToSkeleton() {
        waitForDiv("main_scroll")
        assertDivExists("header")
        // Skeleton marker id — proves the bundled zero_ru.json rendered, not real data.
        assertDivExists("zero_skeleton")
    }
}
