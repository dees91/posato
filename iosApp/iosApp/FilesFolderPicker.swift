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

/// Shares a pause set export as a temporary file through the share sheet,
/// deleting it once the sheet closes, and opens a copy of a chosen export.
final class FilesPauseSetPicker: NSObject, IosPauseSetFilePicker, UIDocumentPickerDelegate {
    weak var presenter: UIViewController?
    private var pickCompletion: ((Data?) -> Void)?
    private var pickLimit = 0

    func exportFile(data: Data, fileName: String, completion: @escaping (KotlinBoolean) -> Void) {
        guard let presenter, presenter.presentedViewController == nil else {
            completion(false)
            return
        }
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        let url = directory.appendingPathComponent(fileName)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try data.write(to: url, options: [.completeFileProtection])
        } catch {
            completion(false)
            return
        }
        let sheet = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        var finished = false
        sheet.completionWithItemsHandler = { activity, completed, _, _ in
            // A cancelled activity, such as Save to Files closed without saving, leaves the sheet open for
            // another choice; only a finished activity or the sheet closing ends the export, once.
            guard !finished, completed || activity == nil else { return }
            finished = true
            try? FileManager.default.removeItem(at: directory)
            completion(KotlinBoolean(value: completed))
        }
        if let popover = sheet.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.midY, width: 0, height: 0)
            popover.permittedArrowDirections = []
        }
        presenter.present(sheet, animated: true)
    }

    func pickFile(maxBytes: Int32, completion: @escaping (Data?) -> Void) {
        guard pickCompletion == nil, let presenter, presenter.presentedViewController == nil else {
            completion(nil)
            return
        }
        pickCompletion = completion
        pickLimit = Int(maxBytes)
        let types = [UTType(exportedAs: "app.posato.pause-sets"), .data]
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
        picker.delegate = self
        picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        let limit = pickLimit
        let data = urls.first.flatMap { url -> Data? in
            defer { try? FileManager.default.removeItem(at: url) }
            guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
            defer { try? handle.close() }
            return try? handle.read(upToCount: limit)
        }
        finishPick(data)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finishPick(nil)
    }

    private func finishPick(_ data: Data?) {
        let callback = pickCompletion
        pickCompletion = nil
        callback?(data)
    }
}
