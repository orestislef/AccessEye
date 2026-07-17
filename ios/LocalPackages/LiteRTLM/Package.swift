// swift-tools-version: 5.9
//
// Local package wrapping the prebuilt LiteRT-LM xcframework (v0.12.0 binary +
// v0.13.0 Swift wrapper, the official pairing). We vendor it locally instead of
// using the remote SwiftPM package because the upstream git repo is ~2 GB to
// clone, while the prebuilt framework is ~116 MB. This is faster, reproducible,
// and offline. iOS-only (the app targets iPhone).
//
// To update: download the matching CLiteRTLM.xcframework.zip release asset into
// Frameworks/, and refresh swift/ from the matching tag.

import PackageDescription

let package = Package(
    name: "LiteRTLM",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "LiteRTLM", targets: ["LiteRTLM"])
    ],
    targets: [
        .binaryTarget(
            name: "CLiteRTLM",
            url: "https://github.com/google-ai-edge/LiteRT-LM/releases/download/v0.12.0/CLiteRTLM.xcframework.zip",
            checksum: "3c2a11ecc8511d1e74efa7ca308dc7130c95223325c33212337ffb0563b79cde"
        ),
        .target(
            name: "LiteRTLM",
            dependencies: ["CLiteRTLM"],
            path: "swift",
            linkerSettings: [
                .unsafeFlags(["-Xlinker", "-all_load"])
            ]
        )
    ]
)
