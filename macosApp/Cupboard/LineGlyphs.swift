import AppKit

// MARK: - Line glyphs

/// Small drawn samples of a stroke style and of a line end, for the popups that
/// pick them. Compose draws these with `drawStrokeStylePreview` and
/// `drawLineEndPreview` (`LineEnds.kt`); SwiftUI can't call those, so this is
/// the same geometry again in AppKit. Template images, so a menu row and a
/// popup face tint them alike.
///
/// Keyed by the enums' names as Kotlin hands them over. A name this file
/// doesn't know draws a bare line rather than nothing.
enum LineGlyph {
    /// How big every sample is, in points.
    static let size = NSSize(width: 44, height: 12)

    /// A name as a title: "SquareDots" reads "Square Dots".
    static func title(_ name: String) -> String {
        var title = ""
        for character in name {
            if character.isUppercase, !title.isEmpty { title.append(" ") }
            title.append(character)
        }
        return title
    }

    /// A stretch of line across the middle of the box, dashed the way [name] is.
    static func strokeStyle(_ name: String) -> NSImage {
        image { bounds in
            let weight: CGFloat = 2
            let path = NSBezierPath()
            path.move(to: CGPoint(x: weight, y: bounds.midY))
            path.line(to: CGPoint(x: bounds.maxX - weight, y: bounds.midY))
            path.lineWidth = weight
            path.lineCapStyle = name == "RoundDots" ? .round : .butt
            let pattern = dashes(name, weight: weight)
            if !pattern.isEmpty { path.setLineDash(pattern, count: pattern.count, phase: 0) }
            path.stroke()
        }
    }

    /// The dash pattern [name] lays along a stroke [weight] wide, empty for a
    /// solid one: `StrokeStyle.pathEffect`'s, every length in stroke widths. A
    /// round dot is a dash of next to nothing that a round cap makes a dot.
    static func dashes(_ name: String, weight: CGFloat) -> [CGFloat] {
        switch name {
        case "SquareDots": return [weight, weight]
        case "ShortDash": return [3 * weight, 2 * weight]
        case "LongDash": return [8 * weight, 3 * weight]
        case "RoundDots": return [0.01, 4 * weight]
        default: return []
        }
    }

    /// A short stretch of line with [name] on its right end, or on its left when
    /// [mirrored], which is how Keynote draws the start's popup.
    static func lineEnd(_ name: String, mirrored: Bool) -> NSImage {
        image { bounds in
            let weight: CGFloat = 1.5
            let head = 3.5 * weight + 3
            // In from the edges, so a bar or a hollow head isn't clipped in half.
            let left = CGPoint(x: 2, y: bounds.midY)
            let right = CGPoint(x: bounds.maxX - 2, y: bounds.midY)
            let tip = mirrored ? left : right
            let tail = mirrored ? right : left
            let back = inset(name, size: head, weight: weight)
            let stop = CGPoint(x: tip.x + (mirrored ? back : -back), y: bounds.midY)

            let line = NSBezierPath()
            line.move(to: tail)
            line.line(to: stop)
            line.lineWidth = weight
            line.stroke()

            drawEnd(name, tip: tip, from: tail, weight: weight, size: head)
        }
    }

    /// How wide a circle, a diamond, a reversed arrow and a bar are against an arrow's length.
    private static let circleScale: CGFloat = 0.85
    /// A square's side against an arrow's length.
    private static let squareScale: CGFloat = 0.8
    /// How far back a swept arrow's notch sits, against its length.
    private static let sweptNotch: CGFloat = 0.6
    /// A reversed arrow's length against an arrow's.
    private static let reversedLength: CGFloat = 0.6

    private static func image(_ draw: @escaping (CGRect) -> Void) -> NSImage {
        let image = NSImage(size: size, flipped: true) { bounds in
            NSColor.black.set()
            draw(bounds)
            return true
        }
        image.isTemplate = true
        return image
    }

    /// How far short of the tip the line stops under this head, so a hollow
    /// head stays hollow. `LineEnd.inset`'s numbers.
    private static func inset(_ name: String, size: CGFloat, weight: CGFloat) -> CGFloat {
        switch name {
        case "Arrow": return 0.8 * size
        case "SweptArrow": return 0.5 * size
        case "OpenArrow": return weight
        case "Circle": return circleScale * size / 2
        case "Diamond": return size / 2
        case "Square": return squareScale * size / 2
        case "OpenSquare": return squareScale * size - weight / 2
        case "OpenCircle": return circleScale * size - weight / 2
        case "ReversedArrow": return 0.3 * size
        default: return 0
        }
    }

    /// The head itself at [tip], the line running back towards [from]: its
    /// outermost point on the tip, [size] long. `drawLineEnd`'s geometry.
    private static func drawEnd(
        _ name: String,
        tip: CGPoint,
        from: CGPoint,
        weight: CGFloat,
        size: CGFloat
    ) {
        let length = hypot(tip.x - from.x, tip.y - from.y)
        guard length > 0 else { return }

        // Along points back down the line from the tip; across is square to it.
        let alongX = (from.x - tip.x) / length
        let alongY = (from.y - tip.y) / length
        func at(_ back: CGFloat, _ side: CGFloat) -> CGPoint {
            CGPoint(x: tip.x + alongX * back - alongY * side, y: tip.y + alongY * back + alongX * side)
        }

        func polygon(_ points: [CGPoint]) -> NSBezierPath {
            let path = NSBezierPath()
            path.move(to: points[0])
            for point in points.dropFirst() { path.line(to: point) }
            path.close()
            return path
        }

        func outline(_ path: NSBezierPath) {
            path.lineWidth = weight
            path.lineJoinStyle = .miter
            path.stroke()
        }

        func circle(_ centre: CGPoint, _ radius: CGFloat) -> NSBezierPath {
            NSBezierPath(ovalIn: CGRect(
                x: centre.x - radius,
                y: centre.y - radius,
                width: radius * 2,
                height: radius * 2
            ))
        }

        let half = circleScale * size / 2
        switch name {
        case "Arrow":
            polygon([at(0, 0), at(size, size / 2), at(size, -size / 2)]).fill()

        case "SweptArrow":
            polygon([at(0, 0), at(size, size / 2), at(sweptNotch * size, 0), at(size, -size / 2)]).fill()

        case "OpenArrow":
            let path = NSBezierPath()
            path.move(to: at(size, size / 2))
            path.line(to: at(weight, 0))
            path.line(to: at(size, -size / 2))
            path.lineCapStyle = .butt
            outline(path)

        case "ReversedArrow":
            polygon([at(0, half), at(0, -half), at(reversedLength * size, 0)]).fill()

        case "Diamond":
            polygon([at(0, 0), at(size / 2, half), at(size, 0), at(size / 2, -half)]).fill()

        case "Circle":
            circle(at(half, 0), half).fill()

        case "OpenCircle":
            outline(circle(at(half, 0), half - weight / 2))

        case "Square", "OpenSquare":
            let side = squareScale * size / 2
            let hollow = name == "OpenSquare"
            let inset = hollow ? weight / 2 : 0
            let square = polygon([
                at(inset, side - inset),
                at(2 * side - inset, side - inset),
                at(2 * side - inset, -side + inset),
                at(inset, -side + inset),
            ])
            if hollow { outline(square) } else { square.fill() }

        case "Bar":
            let path = NSBezierPath()
            path.move(to: at(0, half))
            path.line(to: at(0, -half))
            path.lineWidth = weight
            path.stroke()

        default:
            break
        }
    }
}
