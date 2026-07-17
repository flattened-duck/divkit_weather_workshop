import UIKit

extension UIView {
    /// BFS: the OUTERMOST UICollectionView (main vertical gallery). Nested horizontals are descendants.
    func firstCollectionView() -> UICollectionView? {
        var queue: [UIView] = [self]
        while !queue.isEmpty {
            let v = queue.removeFirst()
            if let cv = v as? UICollectionView { return cv }
            queue.append(contentsOf: v.subviews)
        }
        return nil
    }
}
