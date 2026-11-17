// swift-tools-version: 6.0
// PhotoCriticCore: providers, network, session, config, consent and report
// logic. Foundation only; no UI and no Firebase (design §2, file 45).
import PackageDescription

let package = Package(
    name: "PhotoCriticCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "PhotoCriticCore", targets: ["PhotoCriticCore"]),
    ],
    targets: [
        .target(
            name: "PhotoCriticCore",
            resources: [.copy("Resources/prompts")]
        ),
        .testTarget(
            name: "PhotoCriticCoreTests",
            dependencies: ["PhotoCriticCore"]
        ),
    ],
    swiftLanguageModes: [.v6]
)
