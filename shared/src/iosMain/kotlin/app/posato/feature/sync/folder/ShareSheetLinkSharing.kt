package app.posato.feature.sync.folder

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

/**
 * Passes a pairing link on through the iOS share sheet (AirDrop, Messages, Mail), presented over whatever Posato shows
 * now; on iPad the sheet points at the middle of the screen.
 */
internal object ShareSheetLinkSharing : LinkSharing {
    override val usesShareSheet: Boolean = true

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun share(link: String): Boolean {
        return withContext(Dispatchers.Main) {
            val presenter = topViewController() ?: return@withContext false
            val item: Any = NSURL.URLWithString(link) ?: link
            val sheet = UIActivityViewController(activityItems = listOf(item), applicationActivities = null)
            sheet.popoverPresentationController?.let { popover ->
                val view = presenter.view
                popover.sourceView = view
                view.bounds.useContents { popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 1.0, 1.0) }
            }
            presenter.presentViewController(sheet, animated = true, completion = null)
            true
        }
    }

    private fun topViewController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { window -> window.isKeyWindow() }
        var controller = window?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}
