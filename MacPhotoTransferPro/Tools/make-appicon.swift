// Builds Resources/AppIcon.png and Resources/AppIcon.icns from a square source render.
//
//   swift Tools/make-appicon.swift <source.png> <output-dir>
//
// The source render is expected to show the icon tile on a flat backdrop. The tile is
// located by scanning for pixels that differ from the corner colour, then redrawn onto a
// transparent 1024x1024 canvas as an 824x824 rounded tile, which is the macOS icon grid.

import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

private struct Failure: Error, CustomStringConvertible {
    let description: String
}

private let canvasSide = 1024
private let tileSide = 824.0
private let tileInset = 100.0
private let tileCornerRadius = 185.0

/// Saturation above which a pixel counts as tile rather than backdrop. Saturation rather than
/// brightness distance, because the render's contact shadow is a neutral grey that a brightness
/// test would mistake for tile and bake into the crop.
private let tileSaturationThreshold = 40

/// The tile is drawn this much larger than the mask so the mask always bites into tile colour.
/// Without it, any mismatch between the render's corner curve and the macOS radius below leaves
/// slivers of backdrop in the corners.
private let tileOverfill = 0.06

private let iconSetEntries: [(name: String, side: Int)] = [
    ("icon_16x16", 16),
    ("icon_16x16@2x", 32),
    ("icon_32x32", 32),
    ("icon_32x32@2x", 64),
    ("icon_128x128", 128),
    ("icon_128x128@2x", 256),
    ("icon_256x256", 256),
    ("icon_256x256@2x", 512),
    ("icon_512x512", 512),
    ("icon_512x512@2x", 1024),
]

private func loadImage(at path: String) throws -> CGImage {
    let url = URL(fileURLWithPath: path) as CFURL
    guard let source = CGImageSourceCreateWithURL(url, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil)
    else {
        throw Failure(description: "cannot read an image from \(path)")
    }
    return image
}

private func makeContext(side: Int) throws -> CGContext {
    guard let space = CGColorSpace(name: CGColorSpace.sRGB),
          let context = CGContext(
              data: nil,
              width: side,
              height: side,
              bitsPerComponent: 8,
              bytesPerRow: 0,
              space: space,
              bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
          )
    else {
        throw Failure(description: "cannot create a \(side)x\(side) bitmap context")
    }
    context.interpolationQuality = .high
    return context
}

private func readPixels(_ image: CGImage) throws -> (pixels: [UInt8], width: Int, height: Int) {
    let width = image.width
    let height = image.height
    var pixels = [UInt8](repeating: 0, count: width * height * 4)
    guard let space = CGColorSpace(name: CGColorSpace.sRGB) else {
        throw Failure(description: "cannot create the sRGB colour space")
    }
    let created = pixels.withUnsafeMutableBytes { buffer -> Bool in
        guard let context = CGContext(
            data: buffer.baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: space,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else {
            return false
        }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return true
    }
    guard created else {
        throw Failure(description: "cannot rasterise the source image")
    }
    return (pixels, width, height)
}

/// Bounding box of the tile, in CGImage coordinates (origin at the top-left).
private func tileBounds(of image: CGImage) throws -> CGRect {
    let (pixels, width, height) = try readPixels(image)

    func channels(x: Int, y: Int) -> (Int, Int, Int) {
        let offset = (y * width + x) * 4
        return (Int(pixels[offset]), Int(pixels[offset + 1]), Int(pixels[offset + 2]))
    }

    var minX = width, maxX = -1, minY = height, maxY = -1

    for y in 0..<height {
        for x in 0..<width {
            let pixel = channels(x: x, y: y)
            let saturation = max(pixel.0, pixel.1, pixel.2) - min(pixel.0, pixel.1, pixel.2)
            guard saturation > tileSaturationThreshold else { continue }
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
    }

    guard maxX >= minX, maxY >= minY else {
        throw Failure(description: "no saturated tile was found in the source render")
    }

    // Rows were rasterised bottom-up; flip back into CGImage coordinates. The box is used
    // as-is rather than padded out to a square, so that all four tile edges land on the mask
    // edges and no backdrop survives along the shorter axis.
    return CGRect(
        x: Double(minX),
        y: Double(height - 1 - maxY),
        width: Double(maxX - minX + 1),
        height: Double(maxY - minY + 1)
    )
}

private func makeMasterIcon(from image: CGImage) throws -> CGImage {
    let bounds = try tileBounds(of: image)
    guard let tile = image.cropping(to: bounds) else {
        throw Failure(description: "cannot crop the source render to \(bounds)")
    }
    print("detected tile: \(Int(bounds.width))x\(Int(bounds.height)) at (\(Int(bounds.minX)), \(Int(bounds.minY)))")

    let context = try makeContext(side: canvasSide)
    let tileRect = CGRect(x: tileInset, y: tileInset, width: tileSide, height: tileSide)
    context.addPath(
        CGPath(
            roundedRect: tileRect,
            cornerWidth: tileCornerRadius,
            cornerHeight: tileCornerRadius,
            transform: nil
        )
    )
    context.clip()
    context.draw(tile, in: tileRect.insetBy(dx: -tileSide * tileOverfill / 2, dy: -tileSide * tileOverfill / 2))

    guard let master = context.makeImage() else {
        throw Failure(description: "cannot render the masked icon")
    }
    return master
}

private func resized(_ image: CGImage, toSide side: Int) throws -> CGImage {
    let context = try makeContext(side: side)
    context.draw(image, in: CGRect(x: 0, y: 0, width: side, height: side))
    guard let resized = context.makeImage() else {
        throw Failure(description: "cannot resize the icon to \(side)x\(side)")
    }
    return resized
}

private func writePNG(_ image: CGImage, to url: URL) throws {
    guard let destination = CGImageDestinationCreateWithURL(
        url as CFURL,
        UTType.png.identifier as CFString,
        1,
        nil
    ) else {
        throw Failure(description: "cannot write a PNG to \(url.path)")
    }
    CGImageDestinationAddImage(destination, image, nil)
    guard CGImageDestinationFinalize(destination) else {
        throw Failure(description: "cannot finalise the PNG at \(url.path)")
    }
}

private func runIconUtil(iconSet: URL, output: URL) throws {
    let process = Process()
    process.executableURL = URL(fileURLWithPath: "/usr/bin/iconutil")
    process.arguments = ["--convert", "icns", iconSet.path, "--output", output.path]
    try process.run()
    process.waitUntilExit()
    guard process.terminationStatus == 0 else {
        throw Failure(description: "iconutil failed with status \(process.terminationStatus)")
    }
}

do {
    let arguments = CommandLine.arguments
    guard arguments.count == 3 else {
        throw Failure(description: "usage: swift Tools/make-appicon.swift <source.png> <output-dir>")
    }

    let outputDirectory = URL(fileURLWithPath: arguments[2], isDirectory: true)
    try FileManager.default.createDirectory(at: outputDirectory, withIntermediateDirectories: true)

    let master = try makeMasterIcon(from: try loadImage(at: arguments[1]))
    try writePNG(master, to: outputDirectory.appendingPathComponent("AppIcon.png"))

    let iconSet = URL(fileURLWithPath: NSTemporaryDirectory())
        .appendingPathComponent("AppIcon-\(UUID().uuidString).iconset", isDirectory: true)
    try FileManager.default.createDirectory(at: iconSet, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: iconSet) }

    for entry in iconSetEntries {
        let image = entry.side == canvasSide ? master : try resized(master, toSide: entry.side)
        try writePNG(image, to: iconSet.appendingPathComponent("\(entry.name).png"))
    }

    try runIconUtil(iconSet: iconSet, output: outputDirectory.appendingPathComponent("AppIcon.icns"))
    print("wrote AppIcon.png and AppIcon.icns to \(outputDirectory.path)")
} catch {
    FileHandle.standardError.write(Data("error: \(error)\n".utf8))
    exit(1)
}
