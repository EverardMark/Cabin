import UIKit

/// Every screen hides the system navigation bar to draw the design's own
/// header, which also switches off the edge swipe-to-go-back gesture. This
/// re-enables it whenever there is somewhere to go back to.
extension UINavigationController: @retroactive UIGestureRecognizerDelegate {
    override open func viewDidLoad() {
        super.viewDidLoad()
        interactivePopGestureRecognizer?.delegate = self
    }

    public func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        viewControllers.count > 1
    }
}
