import SwiftUI
import AppKit
import CupboardCanvas

// MARK: - Movie and Audio

/// The Movie and Audio segments of the Format tab. One section for both: a sound
/// is a movie with no picture, so it drops the Web URL and the Poster and keeps
/// the rest.
extension EditorView {
    /// What the movie or sound is called, the bytes behind it, the window of it
    /// that plays, how loud, and whether it plays again and by itself. Same
    /// contract as the Image section: the title, the file, the url and the
    /// poster are the primary's alone, and the playing settings go to every
    /// movie and sound in the selection.
    @ViewBuilder func mediaSection(_ media: MediaFormat, movie: Bool) -> some View {
        VStack(alignment: .leading, spacing: 9) {
            StringField(placeholder: "Title", value: media.title, palette: palette) {
                host.setMediaTitle(text: $0)
            }

            // A web video has no file yet, and choosing one makes it a movie in
            // the deck: the same verb as Replace, which is why it is one button.
            HStack(spacing: 8) {
                rowLabel("File")
                Spacer(minLength: 0)
                arrangeButton(media.hasAsset ? "Replace..." : "Choose...", enabled: true) {
                    Media.replaceMedia(in: host, movie: movie)
                }
                .frame(width: Inspect.field * 2)
            }

            if movie && !media.hasAsset {
                StringField(placeholder: "Web URL", value: media.webUrl, palette: palette) {
                    host.setVideoWebUrl(url: $0)
                }
            }

            if movie {
                HStack(spacing: 8) {
                    rowLabel("Poster")
                    Spacer(minLength: 0)
                    arrangeButton("Choose...", enabled: true) { Media.choosePoster(in: host) }
                        .frame(width: Inspect.field + 20)
                    arrangeButton("Clear", enabled: media.hasPoster) { host.clearMoviePoster() }
                        .frame(width: Inspect.field)
                }
            }

            palette.divider.frame(height: 1)

            sectionLabel("Trim")

            // Seconds to a tenth, the way the durations elsewhere in the panel
            // read; the model holds milliseconds. An empty End plays to the end.
            HStack(spacing: 8) {
                Spacer(minLength: 0)
                captionedField("Start") {
                    ValueField(
                        label: "",
                        value: media.trimStart,
                        palette: palette,
                        unit: "s",
                        decimals: 1,
                        step: 0.1,
                        minimum: 0
                    ) { host.setMediaTrimStart(ms: Self.milliseconds($0)) }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
                captionedField("End") {
                    ValueField(
                        label: "",
                        value: media.trimEnd,
                        palette: palette,
                        unit: "s",
                        decimals: 1,
                        step: 0.1,
                        minimum: 0,
                        placeholder: "End"
                    ) { host.setMediaTrimEnd(ms: Self.milliseconds($0)) }
                    .frame(width: Inspect.field + Inspect.stepper + 5)
                }
            }

            palette.divider.frame(height: 1)

            adjustRow("Volume", media.volume, in: 0...1) {
                host.setMediaVolume(volume: Float($0), commit: false)
            } commit: {
                commitMediaVolume(movie: movie)
            }

            palette.divider.frame(height: 1)

            checkRow("Loop", on: media.loop) { host.setMediaLoop(on: !media.loop) }
            checkRow("Start on Reveal", on: media.autoplay) {
                host.setMediaAutoplay(on: !media.autoplay)
            }
        }
    }

    /// The volume as the loop now holds it, committed: what the slider's release
    /// and the field both end at, the way the image corrections do.
    func commitMediaVolume(movie: Bool) {
        guard let live = movie ? host.selectedVideo() : host.selectedAudio() else { return }
        host.setMediaVolume(volume: live.volume, commit: true)
    }

    /// Seconds off a field as the model's whole milliseconds.
    static func milliseconds(_ seconds: Double) -> Int32 {
        Int32((max(seconds, 0) * 1000).rounded())
    }
}
