import PosatoShared
import UIKit
import UniformTypeIdentifiers

/// Shows the Files folder picker for the folder workspace (ADR 0010) and
/// hands back a base64 bookmark to the chosen folder.
final class FilesFolderPicker: NSObject, IosFolderPicker, UIDocumentPickerDelegate {
    weak var presenter: UIViewController?
    private var completion: ((String?) -> Void)?

    func pickFolder(completion: @escaping (String?) -> Void) {
        guard self.completion == nil, let presenter, presenter.presentedViewController == nil else {
            completion(nil)
            return
        }
        self.completion = completion
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
        picker.delegate = self
        picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        finish(urls.first.flatMap(bookmark(for:)))
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish(nil)
    }

    private func bookmark(for url: URL) -> String? {
        let accessing = url.startAccessingSecurityScopedResource()
        defer {
            if accessing {
                url.stopAccessingSecurityScopedResource()
            }
        }
        return try? url.bookmarkData(options: [], includingResourceValuesForKeys: nil, relativeTo: nil).base64EncodedString()
    }

    private func finish(_ token: String?) {
        let callback = completion
        completion = nil
        callback?(token)
    }
}
