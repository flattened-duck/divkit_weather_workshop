import XCTest
@testable import WeatherDivKit

final class AdapterGoldenTests: XCTestCase {
    // MARK: - IOSDivKitJsonAdapter.normalize

    func test_normalize_gallery_startEnd_to_leftRight() {
        let input: [String: Any] = [
            "type": "gallery",
            "paddings": ["start": 10, "end": 20],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        let paddings = result["paddings"] as! [String: Any]
        XCTAssertEqual(paddings["left"] as? Int, 10)
        XCTAssertEqual(paddings["right"] as? Int, 20)
    }

    func test_normalize_gallery_existingLeft_notOverwritten() {
        let input: [String: Any] = [
            "type": "gallery",
            "paddings": ["left": 5, "start": 10],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        let paddings = result["paddings"] as! [String: Any]
        XCTAssertEqual(paddings["left"] as? Int, 5)
        XCTAssertNil(paddings["right"])
    }

    func test_normalize_noId_transition_dropped() {
        let input: [String: Any] = [
            "type": "container",
            "transition_in": ["type": "fade"],
            "transition_out": ["type": "fade"],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        XCTAssertNil(result["transition_in"])
        XCTAssertNil(result["transition_out"])
    }

    func test_normalize_idPresent_transition_preserved() {
        let input: [String: Any] = [
            "type": "container",
            "id": "my_id",
            "transition_in": ["type": "fade"],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        XCTAssertNotNil(result["transition_in"])
    }

    func test_normalize_nestedGallery_in_array() {
        let input: [String: Any] = [
            "items": [
                ["type": "gallery", "paddings": ["start": 4, "end": 8]],
            ],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        let items = result["items"] as! [Any]
        let nested = items[0] as! [String: Any]
        let paddings = nested["paddings"] as! [String: Any]
        XCTAssertEqual(paddings["left"] as? Int, 4)
        XCTAssertEqual(paddings["right"] as? Int, 8)
    }

    func test_normalize_nonGallery_paddings_untouched() {
        let input: [String: Any] = [
            "type": "container",
            "paddings": ["start": 10, "end": 20],
        ]
        let result = IOSDivKitJsonAdapter.normalize(input) as! [String: Any]
        let paddings = result["paddings"] as! [String: Any]
        XCTAssertNil(paddings["left"])
        XCTAssertNil(paddings["right"])
        XCTAssertEqual(paddings["start"] as? Int, 10)
        XCTAssertEqual(paddings["end"] as? Int, 20)
    }

    // MARK: - DocumentEnvelopeAdapter.makeSources

    func test_makeSources_valid_containsMain() throws {
        let root: [String: Any] = [
            "templates": [String: Any](),
            "screens": [
                "main": ["type": "container", "id": "main_root"],
            ],
        ]
        let sources = try DocumentEnvelopeAdapter.makeSources(from: root)
        XCTAssertNotNil(sources[.main])
    }

    func test_makeSources_missingScreensMain_throwsMalformed() {
        let root: [String: Any] = [
            "screens": [
                "settings": ["type": "container"],
            ],
        ]
        XCTAssertThrowsError(try DocumentEnvelopeAdapter.makeSources(from: root)) { error in
            guard case DocumentLoaderError.malformed = error else {
                XCTFail("expected .malformed, got \(error)")
                return
            }
        }
    }

    func test_makeSources_empty_throwsMalformed() {
        let root: [String: Any] = [:]
        XCTAssertThrowsError(try DocumentEnvelopeAdapter.makeSources(from: root)) { error in
            guard case DocumentLoaderError.malformed = error else {
                XCTFail("expected .malformed, got \(error)")
                return
            }
        }
    }

    // MARK: - CitySearchPatchAdapter.makePatch

    func test_makePatch_valid_changesShaped_noThrow() {
        let root: [String: Any] = [
            "changes": [
                ["id": "test_id"],
            ],
        ]
        XCTAssertNoThrow(try CitySearchPatchAdapter.makePatch(from: root))
    }

    func test_makePatch_garbage_changesNotArray_throws() {
        let root: [String: Any] = [
            "changes": "notArray",
        ]
        XCTAssertThrowsError(try CitySearchPatchAdapter.makePatch(from: root))
    }
}
