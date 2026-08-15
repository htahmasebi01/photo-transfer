// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "MacPhotoTransferPro",
    platforms: [
        .macOS(.v14)
    ],
    dependencies: [
        .package(url: "https://github.com/swhitty/FlyingFox.git", from: "0.20.0")
    ],
    targets: [
        .executableTarget(
            name: "MacPhotoTransferPro",
            dependencies: [
                .product(name: "FlyingFox", package: "FlyingFox")
            ]
        ),
        .testTarget(
            name: "MacPhotoTransferProTests",
            dependencies: ["MacPhotoTransferPro"]
        )
    ]
)
