import SwiftUI

struct GameDisplayScaleRows: View {
    let gameID: String
    var openPreview: ((GameDisplayScale) -> Void)?
    @State private var editing: GameDisplayScale?
    @State private var saved: [GameDisplayScale: Int] = [:]

    var body: some View {
        if let openPreview {
            rows(select: openPreview)
        } else {
            rows { editing = $0 }
                .sheet(item: $editing) { scale in
                    NavigationView { editor(for: scale) }
                        .navigationViewStyle(.stack)
                        .modifier(HubAppearanceModifier())
                        .interactiveDismissDisabled()
                }
        }
    }

    private func rows(select: @escaping (GameDisplayScale) -> Void) -> some View {
        ForEach(GameDisplayScale.allCases) { scale in
            Button { select(scale) } label: {
                HStack {
                    Label(scale.title, systemImage: scale.symbol)
                    Spacer()
                    Text("\(percent(scale))%").monospacedDigit()
                    if openPreview != nil {
                        Image(systemName: "chevron.right").foregroundColor(.secondary)
                    }
                }
                .frame(minHeight: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .onAppear {
            saved = Dictionary(uniqueKeysWithValues: GameDisplayScale.allCases.map { ($0, $0.percent(for: gameID)) })
        }
    }

    private func editor(for scale: GameDisplayScale) -> some View {
        GameDisplayScaleEditor(scale: scale, initialPercent: percent(scale)) { value in
            if let value {
                scale.save(value, for: gameID)
                saved[scale] = value
            }
            editing = nil
        }
    }

    private func percent(_ scale: GameDisplayScale) -> Int {
        saved[scale] ?? scale.percent(for: gameID)
    }
}

struct GameDisplayScaleEditor: View {
    let scale: GameDisplayScale
    let live: Bool
    let preview: (Int) -> Void
    let finish: (Int?) -> Void
    @State private var percent: Int

    init(scale: GameDisplayScale, initialPercent: Int, live: Bool = false,
         preview: @escaping (Int) -> Void = { _ in }, finish: @escaping (Int?) -> Void) {
        self.scale = scale
        self.live = live
        self.preview = preview
        self.finish = finish
        _percent = State(initialValue: initialPercent)
    }

    var body: some View {
        if live {
            ScrollView {
                VStack(spacing: 8) {
                    controls
                    HStack {
                        resetButton
                        Spacer()
                        cancelButton
                        confirmButton.buttonStyle(.borderedProminent)
                    }
                }
                .buttonStyle(.bordered)
                .padding(12)
                .background(HubTheme.surface, in: RoundedRectangle(cornerRadius: 16))
                .shadow(radius: 6)
                .padding(8)
            }
            .frame(maxWidth: 340)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        } else {
            detailEditor
        }
    }

    private var detailEditor: some View {
        ScrollView {
            VStack(spacing: 16) {
                controls
                resetButton
            }
            .buttonStyle(.bordered)
            .modifier(HubCard())
            .padding(20)
            .frame(maxWidth: 560)
            .frame(maxWidth: .infinity)
        }
        .background(HubTheme.background.ignoresSafeArea())
        .navigationTitle(scale.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { cancelButton }
            ToolbarItem(placement: .confirmationAction) { confirmButton }
        }
    }

    @ViewBuilder private var controls: some View {
        HStack {
            if live { Text(scale.title).font(.headline) }
            Text("\(percent)%").font(.title2.bold()).monospacedDigit()
            Spacer()
            Button("−\(scale.step)%") { select(percent - scale.step) }
                .disabled(percent == scale.range.lowerBound)
            Button("+\(scale.step)%") { select(percent + scale.step) }
                .disabled(percent == scale.range.upperBound)
        }
        Slider(value: Binding(get: { Double(percent) }, set: { percent = Int($0) }),
               in: Double(scale.range.lowerBound)...Double(scale.range.upperBound), step: 1,
               onEditingChanged: { editing in if !editing { preview(percent) } },
               label: { Text(scale.title) })
        .accessibilityValue(Text("\(percent)%"))
        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3)) {
            ForEach(scale.presets, id: \.self) { preset in
                Button("\(preset)%") { select(preset) }
                    .frame(maxWidth: .infinity)
            }
        }
    }

    private var resetButton: some View { Button("恢复默认") { finish(GameDisplayScale.defaultPercent) } }
    private var cancelButton: some View { Button("取消") { finish(nil) } }
    private var confirmButton: some View { Button("确定") { finish(percent) } }

    private func select(_ value: Int) {
        percent = scale.clamped(value)
        preview(percent)
    }
}

private extension GameDisplayScale {
    var title: String { self == .font ? "字体大小" : "滚动条" }
    var symbol: String { self == .font ? "textformat.size" : "scroll" }
}
