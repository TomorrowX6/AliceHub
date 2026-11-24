import SwiftUI

struct GameDisplayScaleRows: View {
    let gameID: String
    var live = false
    @State private var editing: GameDisplayScale?
    @State private var saved: [GameDisplayScale: Int] = [:]

    var body: some View {
        Group {
            if live {
                ForEach(GameDisplayScale.allCases) { scale in
                    NavigationLink(tag: scale, selection: $editing) {
                        editor(for: scale)
                            .navigationBarBackButtonHidden(true)
                    } label: {
                        row(for: scale)
                    }
                }
            } else {
                ForEach(GameDisplayScale.allCases) { scale in
                    Button { editing = scale } label: { row(for: scale) }
                        .buttonStyle(.plain)
                }
                .sheet(item: $editing) { scale in
                    NavigationView {
                        editor(for: scale)
                    }
                    .navigationViewStyle(.stack)
                    .modifier(HubAppearanceModifier())
                    .interactiveDismissDisabled()
                }
            }
        }
        .onAppear {
            saved = Dictionary(uniqueKeysWithValues: GameDisplayScale.allCases.map { ($0, $0.percent(for: gameID)) })
        }
    }

    private func row(for scale: GameDisplayScale) -> some View {
        HStack {
            Label(scale.title, systemImage: scale.symbol)
            Spacer()
            Text("\(percent(scale))%").monospacedDigit()
        }
        .frame(minHeight: 44)
        .contentShape(Rectangle())
    }

    private func editor(for scale: GameDisplayScale) -> some View {
        GameDisplayScaleEditor(scale: scale, initialPercent: percent(scale), preview: { value in
            if live { ModifierBridge.applyDisplayScale(for: gameID, preview: (scale, value)) }
        }, finish: { value in
            if let value {
                scale.save(value, for: gameID)
                saved[scale] = value
            }
            if live { ModifierBridge.applyDisplayScale(for: gameID) }
            editing = nil
        })
    }

    private func percent(_ scale: GameDisplayScale) -> Int {
        saved[scale] ?? scale.percent(for: gameID)
    }
}

private struct GameDisplayScaleEditor: View {
    let scale: GameDisplayScale
    let preview: (Int) -> Void
    let finish: (Int?) -> Void
    @State private var percent: Int

    init(scale: GameDisplayScale, initialPercent: Int, preview: @escaping (Int) -> Void, finish: @escaping (Int?) -> Void) {
        self.scale = scale
        self.preview = preview
        self.finish = finish
        _percent = State(initialValue: initialPercent)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                HStack {
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
                Button("恢复默认") { finish(GameDisplayScale.defaultPercent) }
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
            ToolbarItem(placement: .cancellationAction) { Button("取消") { finish(nil) } }
            ToolbarItem(placement: .confirmationAction) { Button("确定") { finish(percent) } }
        }
    }

    private func select(_ value: Int) {
        percent = scale.clamped(value)
        preview(percent)
    }
}

private extension GameDisplayScale {
    var title: String { self == .font ? "字体大小" : "滚动条" }
    var symbol: String { self == .font ? "textformat.size" : "scroll" }
}
