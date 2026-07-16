import DivKit
import XCTest
@testable import WeatherDivKit

private final class FakeDocumentLoading: DocumentLoading {
    private(set) var calls: [String] = []
    private(set) var lastNetworkArgs: [String]?
    private(set) var lastCacheLang: String?

    var networkResult: DocumentBundle?
    var cacheResult: DocumentBundle?
    var skeletonResult: DocumentBundle?
    var citySearchResult: DivPatch?

    func load(lang: String, lat: String?, lon: String?, name: String?) async throws -> DocumentBundle {
        calls.append("network")
        lastNetworkArgs = [lang, lat ?? "", lon ?? "", name ?? ""]
        guard let networkResult else { throw DocumentLoaderError.malformed("no network result") }
        return networkResult
    }

    func loadCache(lang: String) -> DocumentBundle? {
        calls.append("cache")
        lastCacheLang = lang
        return cacheResult
    }

    func loadBundledSkeleton() -> DocumentBundle? {
        calls.append("skeleton")
        return skeletonResult
    }

    func loadCitySearch(query: String, lang: String) async -> DivPatch? {
        calls.append("search:\(query):\(lang)")
        return citySearchResult
    }
}

final class DocumentRepositoryTests: XCTestCase {
    private func bundle(_ tag: UInt8) -> DocumentBundle {
        DocumentBundle(sources: [:], rawBody: Data([tag]))
    }

    private func makeSUT() -> (DocumentRepository, FakeDocumentLoading) {
        let loader = FakeDocumentLoading()
        return (DocumentRepository(loader: loader), loader)
    }

    func test_coldStartLocal_cacheHit_returnsCacheWithoutSkeleton() {
        let (sut, loader) = makeSUT()
        loader.cacheResult = bundle(2)

        let result = sut.coldStartLocal(lang: "ru")

        XCTAssertEqual(result?.rawBody, Data([2]))
        XCTAssertEqual(loader.calls, ["cache"])
    }

    func test_coldStartLocal_cacheMiss_fallsBackToSkeleton() {
        let (sut, loader) = makeSUT()
        loader.cacheResult = nil
        loader.skeletonResult = bundle(3)

        let result = sut.coldStartLocal(lang: "ru")

        XCTAssertEqual(result?.rawBody, Data([3]))
        XCTAssertEqual(loader.calls, ["cache", "skeleton"])
    }

    func test_coldStartLocal_bothNil_returnsNil() {
        let (sut, loader) = makeSUT()
        loader.cacheResult = nil
        loader.skeletonResult = nil

        let result = sut.coldStartLocal(lang: "ru")

        XCTAssertNil(result)
        XCTAssertEqual(loader.calls, ["cache", "skeleton"])
    }

    func test_fetch_success_returnsNetworkResult() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = bundle(1)

        let result = await sut.fetch(lang: "ru", lat: "1", lon: "2", name: "X")

        XCTAssertEqual(result?.rawBody, Data([1]))
        XCTAssertEqual(loader.calls, ["network"])
    }

    func test_fetch_throws_returnsNilAndSwallowsError() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = nil

        let result = await sut.fetch(lang: "ru", lat: nil, lon: nil, name: nil)

        XCTAssertNil(result)
        XCTAssertEqual(loader.calls, ["network"])
    }

    func test_fetch_forwardsArgsToLoader() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = bundle(1)

        _ = await sut.fetch(lang: "ru", lat: "1", lon: "2", name: "X")

        XCTAssertEqual(loader.lastNetworkArgs, ["ru", "1", "2", "X"])
    }

    func test_fetchOrCache_networkHit_doesNotTouchCache() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = bundle(1)
        loader.cacheResult = bundle(2)

        let result = await sut.fetchOrCache(lang: "ru", lat: nil, lon: nil, name: nil)

        XCTAssertEqual(result?.rawBody, Data([1]))
        XCTAssertEqual(loader.calls, ["network"])
    }

    func test_fetchOrCache_networkFails_fallsBackToCache() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = nil
        loader.cacheResult = bundle(2)

        let result = await sut.fetchOrCache(lang: "ru", lat: nil, lon: nil, name: nil)

        XCTAssertEqual(result?.rawBody, Data([2]))
        XCTAssertEqual(loader.calls, ["network", "cache"])
        XCTAssertEqual(loader.lastCacheLang, "ru")
    }

    func test_fetchOrCache_bothNil_returnsNil() async {
        let (sut, loader) = makeSUT()
        loader.networkResult = nil
        loader.cacheResult = nil

        let result = await sut.fetchOrCache(lang: "ru", lat: nil, lon: nil, name: nil)

        XCTAssertNil(result)
        XCTAssertEqual(loader.calls, ["network", "cache"])
    }

    func test_citySearchPatch_forwardsToLoader() async {
        let (sut, loader) = makeSUT()

        _ = await sut.citySearchPatch(query: "moscow", lang: "ru")

        XCTAssertEqual(loader.calls, ["search:moscow:ru"])
    }
}
