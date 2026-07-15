import DivKit
import UIKit

/// Drives the global `header_state` String var ("full"/"collapsed") from the scroll offset of the
/// main vertical gallery carrying `extensions:[{"id":"scroll_state"}]` (div id `main_scroll`).
///
/// iOS DIVERGENCE FROM ANDROID: the extension handler runs at modeling time and never receives the
/// scroll view. We use `accept(div:context:)` only as a "the scroll_state div is being (re)modeled"
/// signal, then walk the rendered DivView tree to the gallery's UICollectionView and observe its
/// `contentOffset` via delegate-safe block KVO. Hysteresis is ported from
/// app/.../divkit/ScrollStateExtensionHandler.kt.
final class ScrollStateExtensionHandler: DivExtensionHandler {
    let id = "scroll_state"

    private let variablesStorage: DivVariablesStorage
    private let hostView: () -> UIView?

    private var cardId = DivCardID(rawValue: "main")
    private var observation: NSKeyValueObservation?
    private weak var observedScrollView: UIScrollView?
    private var lastCollapsed: Bool?
    private var isRetryScheduled = false
    private var retriesLeft = 0

    private let collapsePt: CGFloat = 24       // COLLAPSE_DP in Android (iOS uses points directly)
    private let maxRetries = 25                 // ~2.5s of 0.1s ticks before giving up

    init(variablesStorage: DivVariablesStorage, hostView: @escaping () -> UIView?) {
        self.variablesStorage = variablesStorage
        self.hostView = hostView
    }

    // MARK: DivExtensionHandler (modeling-time signal only)

    func accept(div: DivBase, context: DivBlockModelingContext) {
        let cardId = context.cardId
        DispatchQueue.main.async { [weak self] in
            guard let self else { return }
            self.cardId = cardId
            self.retriesLeft = self.maxRetries
            self.attach()
        }
    }

    // MARK: Attach / observe (main thread only)

    private func attach() {
        guard let root = hostView(),
              let scrollView = firstCollectionView(in: root) else {
            scheduleRetry()
            return
        }
        if scrollView === observedScrollView {
            // Same view re-modeled (e.g. compact toggled off at top): re-derive state
            // deterministically instead of waiting for an inset-shift KVO tick. The change-only
            // guard in evaluate() makes this a no-op when nothing changed. Mirrors Android's
            // bindView() always ending in updateCollapsed().
            evaluate(scrollView)
            return
        }
        // Assigning a new token invalidates the previous one → clean detach, no duplicates.
        observation = scrollView.observe(\.contentOffset, options: [.new]) { [weak self] sv, _ in
            self?.evaluate(sv)                              // KVO on contentOffset fires on main
        }
        observedScrollView = scrollView
        lastCollapsed = nil                                // fresh hysteresis for a new view
        evaluate(scrollView)                               // initial push
    }

    private func scheduleRetry() {
        guard !isRetryScheduled, retriesLeft > 0 else { return }
        isRetryScheduled = true
        retriesLeft -= 1
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            self?.isRetryScheduled = false
            self?.attach()
        }
    }

    /// BFS: the OUTERMOST UICollectionView in the DivView tree is the main vertical gallery
    /// (`main_scroll`). Nested horizontal galleries (e.g. hourly forecast) are its descendants and are
    /// found later, so BFS-first is correct. (Div id is NOT exposed as accessibilityIdentifier —
    /// verified — so we select by tree position, not id.)
    private func firstCollectionView(in root: UIView) -> UIScrollView? {
        var queue = [root]
        while !queue.isEmpty {
            let v = queue.removeFirst()
            if let cv = v as? UICollectionView { return cv }
            queue.append(contentsOf: v.subviews)
        }
        return nil
    }

    // MARK: Hysteresis (ported from Android updateCollapsed)

    private func evaluate(_ scrollView: UIScrollView) {
        let distanceFromTop = scrollView.contentOffset.y + scrollView.adjustedContentInset.top
        let atTop = distanceFromTop <= 0.5
        let forced = currentCompact()
        let prev = lastCollapsed ?? false
        let collapsed: Bool
        if forced {
            collapsed = true
        } else if atTop {
            collapsed = false
        } else if prev {
            collapsed = true
        } else {
            collapsed = distanceFromTop > collapsePt
        }
        guard lastCollapsed != collapsed else { return }
        lastCollapsed = collapsed
        writeHeaderState(collapsed ? "collapsed" : "full")
    }

    private func currentCompact() -> Bool {
        if case let .bool(value) = variablesStorage.getVariableValue(cardId: cardId, name: GlobalVariables.compact) {
            return value
        }
        return false
    }

    private func writeHeaderState(_ value: String) {
        // Change-only vs the ACTUAL stored value (robust to Stage-1 setCompact also writing it).
        if case let .string(current) = variablesStorage.getVariableValue(cardId: cardId, name: GlobalVariables.headerState),
           current == value {
            return
        }
        variablesStorage.append(variables: [GlobalVariables.headerState: .string(value)], triggerUpdate: true)
    }
}
