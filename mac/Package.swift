// swift-tools-version: 5.10
import PackageDescription

let package = Package(
    name: "PhotoReceiver",
    platforms: [
        .macOS(.v14)
    ],
    dependencies: [
        .package(url: "https://github.com/swhitty/FlyingFox.git", from: "0.20.0")
    ],
    targets: [
        .executableTarget(
            name: "PhotoReceiver",
            dependencies: [
                .product(name: "FlyingFox", package: "FlyingFox")
            ]
        ),
        .testTarget(
            name: "PhotoReceiverTests",
            dependencies: ["PhotoReceiver"]
        )
    ]
)
