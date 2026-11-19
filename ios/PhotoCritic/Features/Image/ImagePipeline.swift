import CoreGraphics
import Foundation
import ImageIO
import PhotoCriticCore
import UniformTypeIdentifiers

/// The picked file's format, from its first bytes; the `input_format` of `pc_image_prepared`.
enum InputFormat: String {
    case jpeg, png, webp, gif, heic, avif, other

    static func sniff(_ header: Data) -> InputFormat {
        let bytes = [UInt8](header.prefix(64))
        let text = String(decoding: bytes.map { $0 < 0x80 ? $0 : 0x3F }, as: UTF8.self)
        func at(_ offset: Int, _ s: String) -> Bool {
            bytes.count >= offset + s.utf8.count && Array(bytes[offset ..< offset + s.utf8.count]) == Array(s.utf8)
        }
        if bytes.starts(with: [0xFF, 0xD8, 0xFF]) { return .jpeg }
        if bytes.starts(with: [0x89, 0x50, 0x4E, 0x47]) { return .png }
        if at(0, "GIF8") { return .gif }
        if at(0, "RIFF"), at(8, "WEBP") { return .webp }
        if at(4, "ftyp") {
            // The major brand and the compatible brands, 4 bytes each from offset 8.
            let brands = stride(from: 8, to: max(8, min(bytes.count, 64) - 3), by: 4).map { i in
                String(text[text.index(text.startIndex, offsetBy: i) ..< text.index(text.startIndex, offsetBy: i + 4)])
            }
            if brands.contains(where: { ["avif", "avis"].contains($0) }) { return .avif }
            if brands.contains(where: { ["heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"].contains($0) }) { return .heic }
        }
        return .other
    }
}

enum PrepareResult: Sendable {
    case ready(SendCopyFile, format: InputFormat, prepareMs: Int)
    /// Shown as `image-error` (§7). The reason goes into "Show details".
    case failed(reason: String, format: InputFormat?)
}

/// The JPEG written for one model's size and detail settings (§6).
struct SendCopyFile: Sendable, Equatable {
    let url: URL
    let bytes: Int
    let width: Int
    let height: Int
    /// The model's long-edge limit this copy was made for.
    let targetLongEdgePx: Int
    let detail: String

    var longEdgePx: Int { max(width, height) }

    func load() throws -> SendCopy {
        SendCopy(jpeg: try Data(contentsOf: url), width: width, height: height, imageDetail: detail)
    }
}

/// Builds the send copy (§6, file 35) with ImageIO: decode downsampled to
/// the model's long edge with the EXIF orientation applied, flatten
/// transparency onto white, encode JPEG at `Tuning.jpegQuality` with no
/// metadata; if over `maxBytes`, encode once more at `retryQuality`; if
/// still over, fail. A large photo is never fully decoded.
struct ImagePipeline {
    static let retryQuality = 0.6
    let outDir: URL

    func prepare(_ original: URL, targetLongEdgePx: Int, maxBytes: Int, detail: String) async -> PrepareResult {
        await Task.detached(priority: .userInitiated) { () -> PrepareResult in
            let start = ContinuousClock.now
            guard let header = try? FileHandle(forReadingFrom: original).read(upToCount: 64) else {
                return .failed(reason: "unreadable: open", format: nil)
            }
            let format = InputFormat.sniff(header)
            guard let image = Self.decode(original, maxLongEdgePx: targetLongEdgePx) else {
                return .failed(reason: "unreadable: decode", format: format)
            }
            let flat = Self.flattened(image) ?? image
            guard let jpeg = [Tuning.jpegQuality, Self.retryQuality].lazy
                .compactMap({ Self.encode(flat, quality: $0) })
                .first(where: { $0.count <= maxBytes })
            else { return .failed(reason: "too-large", format: format) }
            do {
                try FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
                let url = outDir.appendingPathComponent("send-\(UUID().uuidString).jpg")
                try jpeg.write(to: url, options: .completeFileProtectionUntilFirstUserAuthentication)
                let copy = SendCopyFile(
                    url: url, bytes: jpeg.count, width: flat.width, height: flat.height, targetLongEdgePx: targetLongEdgePx, detail: detail
                )
                return .ready(copy, format: format, prepareMs: Int((ContinuousClock.now - start) / .milliseconds(1)))
            } catch {
                return .failed(reason: "write-failed", format: format)
            }
        }.value
    }

    /// The original, decoded for the screen (§6): downsampled to at most
    /// `maxLongEdgePx` with the orientation applied, kept in memory only.
    func decodeForDisplay(_ original: URL, maxLongEdgePx: Int) async -> CGImage? {
        await Task.detached(priority: .userInitiated) { Self.decode(original, maxLongEdgePx: maxLongEdgePx) }.value
    }

    /// The thumbnail API downsamples while decoding and applies the EXIF
    /// orientation; it never upscales a smaller photo.
    static func decode(_ url: URL, maxLongEdgePx: Int) -> CGImage? {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxLongEdgePx,
            kCGImageSourceShouldCacheImmediately: true,
        ]
        return CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    }

    /// Transparent pixels become white, not JPEG's black.
    static func flattened(_ image: CGImage) -> CGImage? {
        guard ![.none, .noneSkipFirst, .noneSkipLast].contains(image.alphaInfo) else { return image }
        let rect = CGRect(x: 0, y: 0, width: image.width, height: image.height)
        guard let context = CGContext(
            data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else { return nil }
        context.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        context.fill(rect)
        context.draw(image, in: rect)
        return context.makeImage()
    }

    /// A destination given only the image and a quality writes no EXIF, GPS or other metadata.
    static func encode(_ image: CGImage, quality: Double) -> Data? {
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(data, UTType.jpeg.identifier as CFString, 1, nil) else { return nil }
        CGImageDestinationAddImage(destination, image, [kCGImageDestinationLossyCompressionQuality: quality] as CFDictionary)
        return CGImageDestinationFinalize(destination) ? data as Data : nil
    }
}

/// One photo in a conversation (file 35). The original is a file this app
/// wrote (from the picker or the camera) and is deleted with the conversation.
struct PhotoRecord: Sendable {
    let original: URL
    let imageVersionID = UUID().uuidString
    var sendCopy: SendCopyFile?
}

/// Removes originals and send copies (§6, file 35): everything at launch,
/// and one photo's files when its conversation ends. Photos are never kept
/// in any other cache.
struct Cleanup {
    let originalsDir: URL
    let sendDir: URL

    init(cacheDir: URL) {
        originalsDir = cacheDir.appendingPathComponent("originals")
        sendDir = cacheDir.appendingPathComponent("send")
    }

    func atLaunch() {
        try? FileManager.default.removeItem(at: originalsDir)
        try? FileManager.default.removeItem(at: sendDir)
    }

    func conversationEnded(_ record: PhotoRecord) {
        try? FileManager.default.removeItem(at: record.original)
        if let copy = record.sendCopy { try? FileManager.default.removeItem(at: copy.url) }
    }

    func replaced(_ copy: SendCopyFile) {
        try? FileManager.default.removeItem(at: copy.url)
    }

    /// Writes picked or captured photo data as this conversation's original.
    func storeOriginal(_ data: Data) throws -> URL {
        try FileManager.default.createDirectory(at: originalsDir, withIntermediateDirectories: true)
        let url = originalsDir.appendingPathComponent("original-\(UUID().uuidString)")
        try data.write(to: url, options: .completeFileProtectionUntilFirstUserAuthentication)
        return url
    }
}
