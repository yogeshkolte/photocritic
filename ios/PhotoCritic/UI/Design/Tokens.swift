import SwiftUI

/// Design tokens (design §12). The Android app holds the same values.
enum Tokens {
    static let bg = Color(red: 0x0F / 255, green: 0x10 / 255, blue: 0x12 / 255)
    static let surface = Color(red: 0x17 / 255, green: 0x18 / 255, blue: 0x1B / 255)
    static let field = Color(red: 0x1C / 255, green: 0x1D / 255, blue: 0x21 / 255)
    static let text = Color(red: 0xF4 / 255, green: 0xF4 / 255, blue: 0xF2 / 255)
    static let muted = text.opacity(0.68)
    static let accent = Color(red: 0xF5 / 255, green: 0xB1 / 255, blue: 0x3D / 255)
    static let accentOnLight = Color(red: 0x8A / 255, green: 0x5A / 255, blue: 0x00 / 255)
    static let onAccent = Color(red: 0x1A / 255, green: 0x12 / 255, blue: 0x06 / 255)
    static let danger = Color(red: 0xFF / 255, green: 0x8A / 255, blue: 0x7A / 255)
}
