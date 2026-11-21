import SwiftUI

struct GamepadEditor: View {
    let game: String?
    private let store = GamepadProfiles()
    @State private var profile: GamepadProfile
    @State private var override: Bool
    @State private var selectedID: String?
    @State private var dragStart: CGPoint?
    @State private var resetting = false
    @Environment(\.dismiss) private var dismiss

    init(game: String?) {
        let store = GamepadProfiles()
        self.game = game
        _profile = State(initialValue: store.active(game))
        _override = State(initialValue: game.map { store.saved($0) != nil } ?? false)
    }

    var body: some View {
        NavigationView {
            Form {
                Section {
                    HubSectionHeader(title: padText("Gamepad"), subtitle: game ?? padText("Virtual Gamepad"), symbol: "gamecontroller")
                }.listRowBackground(HubTheme.surface)
                if let game {
                    Section(header: Text(game)) {
                        Toggle(padText("Per-game override"), isOn: $override)
                            .onChange(of: override) { enabled in
                                if !enabled {
                                    profile = store.active(nil)
                                    selectedID = nil
                                }
                            }
                    }
                }
                Group {
                    Section(header: Text(padText("Virtual Gamepad"))) {
                        Toggle(padText("Enable Virtual Gamepad"), isOn: $profile.virtual.enabled)
                        layoutPreview
                        Picker(padText("Selected control"), selection: $selectedID) {
                            Text(padText("None")).tag(String?.none)
                            ForEach(profile.virtual.controls) { control in
                                Text(padText(control.label)).tag(Optional(control.id))
                            }
                        }
                        if let index = profile.virtual.controls.firstIndex(where: { $0.id == selectedID }) {
                            controlSettings(at: index)
                        }
                        Menu(padText("Add control")) {
                            addControl("Button", .button)
                            addControl("D-Pad", .dpad)
                            addControl("Left stick", .leftStick)
                            addControl("Right stick", .rightStick)
                        }
                        Menu(padText("Layout Preset")) {
                            ForEach(PadPreset.allCases) { preset in
                                Button(preset.title) {
                                    profile.virtual.usePreset(preset)
                                    selectedID = nil
                                }
                            }
                        }
                        slider("Global scale", value: $profile.virtual.scale, range: 0.6...1.5)
                        slider("Global opacity", value: $profile.virtual.opacity, range: 0.2...1)
                        Toggle(padText("Snap to grid"), isOn: $profile.virtual.snap)
                    }
                    Section(header: Text(padText("Physical Controller"))) {
                        Toggle(padText("Enable Controller"), isOn: $profile.physical.enabled)
                        Toggle(padText("Auto-hide Virtual Gamepad"), isOn: $profile.physical.autoHide)
                        Toggle(padText("Swap A/B and X/Y"), isOn: $profile.physical.swapAbXy)
                        modePicker("Left stick", mode: $profile.physical.leftStickMode)
                        modePicker("Right stick", mode: $profile.physical.rightStickMode)
                        modePicker("D-Pad", mode: $profile.physical.dpadMode, modes: PadMode.allCases.filter { $0 != .wheel })
                        slider("Dead zone", value: $profile.physical.deadzonePct, range: 5...45, fraction: false)
                        slider("Cursor speed", value: $profile.physical.cursorSpeedPct, range: 30...250, fraction: false)
                        NavigationLink(padText("Button Mapping")) {
                            Form {
                                Menu(padText("Layout Preset")) {
                                    Button(PadPreset.visualNovel.title) { profile.physical.bindings = PhysicalPad.defaults }
                                    Button(PadPreset.rpg.title) { profile.physical.bindings = PhysicalPad.rpgBindings }
                                }
                                ForEach(PhysicalPad.slots.indices, id: \.self) { index in
                                    actionPicker(PhysicalPad.slots[index], action: $profile.physical.bindings[index])
                                }
                                Button(padText("Reset Button Mapping"), role: .destructive) {
                                    profile.physical.bindings = PhysicalPad.defaults
                                }
                            }.modifier(HubListStyle()).navigationTitle(padText("Button Mapping"))
                        }
                    }
                    Section {
                        Button(padText("Reset to Default"), role: .destructive) { resetting = true }
                    }
                }
                .disabled(game != nil && !override)
            }
            .modifier(HubListStyle())
            .navigationTitle(padText("Gamepad"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(padText("Cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(padText("Save")) {
                        if let game, !override { store.removeOverride(for: game) }
                        else { store.save(profile, for: game) }
                        dismiss()
                    }
                }
            }
            .confirmationDialog(padText("Reset to Default"), isPresented: $resetting, titleVisibility: .visible) {
                Button(padText("Reset to Default"), role: .destructive) {
                    profile = GamepadProfile()
                    selectedID = nil
                }
            }
        }
        .navigationViewStyle(.stack)
        .modifier(HubAppearanceModifier())
    }

    private var layoutPreview: some View {
        GeometryReader { geometry in
            let bounds = CGRect(origin: .zero, size: geometry.size)
            ZStack {
                RoundedRectangle(cornerRadius: 16)
                    .fill(LinearGradient(colors: [Color(red: 0.12, green: 0.15, blue: 0.25), Color(red: 0.06, green: 0.08, blue: 0.14)], startPoint: .topLeading, endPoint: .bottomTrailing))
                VStack(spacing: 6) {
                    Image(systemName: "hand.draw").font(.title2)
                    Text(padText("Drag controls to move them")).font(.caption2)
                }.foregroundColor(.white.opacity(0.4)).allowsHitTesting(false)
                if profile.virtual.snap {
                    Path { path in
                        for index in 1..<40 {
                            let x = bounds.width * Double(index) / 40
                            let y = bounds.height * Double(index) / 40
                            path.move(to: CGPoint(x: x, y: 0))
                            path.addLine(to: CGPoint(x: x, y: bounds.height))
                            path.move(to: CGPoint(x: 0, y: y))
                            path.addLine(to: CGPoint(x: bounds.width, y: y))
                        }
                    }.stroke(.white.opacity(0.15), lineWidth: 0.5)
                }
                ForEach(profile.virtual.controls) { control in
                    previewControl(control, in: bounds)
                }
            }
            .coordinateSpace(name: "layout")
        }
        .aspectRatio(1.9, contentMode: .fit)
        .padding(.vertical, 6)
    }

    private func previewControl(_ control: PadControl, in bounds: CGRect) -> some View {
        let size = control.size(in: bounds, globalScale: profile.virtual.scale)
        let selected = selectedID == control.id
        let shape = RoundedRectangle(cornerRadius: control.shape == .square ? 6 : size.height / 2)
        return Text(control.kind == .dpad ? "✚" : padText(control.label))
            .font(.caption2.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.45)
            .foregroundColor(.white)
            .padding(.horizontal, 2)
            .frame(width: size.width, height: size.height)
            .background(shape.fill(selected ? Color.accentColor : Color(white: 0.04)))
            .overlay(shape.strokeBorder(selected ? Color.accentColor : .white, lineWidth: selected ? 2 : 1))
            .opacity(control.visible ? max(0.25, profile.virtual.opacity * control.opacity) : 0.22)
            .contentShape(shape)
            .onTapGesture { selectedID = control.id }
            .gesture(DragGesture(coordinateSpace: .named("layout")).onChanged { value in
                guard let index = profile.virtual.controls.firstIndex(where: { $0.id == control.id }) else { return }
                if dragStart == nil { dragStart = control.center(in: bounds) }
                guard let start = dragStart else { return }
                selectedID = control.id
                profile.virtual.controls[index].move(
                    to: CGPoint(x: start.x + value.translation.width, y: start.y + value.translation.height),
                    in: bounds, snap: profile.virtual.snap)
            }.onEnded { _ in dragStart = nil })
            .position(control.center(in: bounds))
    }

    @ViewBuilder
    private func controlSettings(at index: Int) -> some View {
        let binding = $profile.virtual.controls[index]
        Toggle(padText("Visible"), isOn: binding.visible)
        slider("Size", value: binding.scale, range: 0.6...1.6)
        slider("Opacity", value: binding.opacity, range: 0.3...1)
        if binding.wrappedValue.kind == .button {
            TextField(padText("Label"), text: binding.label)
            actionPicker("Action", action: binding.action)
            Picker(padText("Shape"), selection: binding.shape) {
                Text(padText("Circle")).tag(PadControl.Shape.circle)
                Text(padText("Square")).tag(PadControl.Shape.square)
                Text(padText("Pill")).tag(PadControl.Shape.pill)
            }
        } else {
            modePicker("Mode", mode: binding.mode)
        }
        Button(padText("Remove Control"), role: .destructive) {
            profile.virtual.controls.remove(at: index)
            selectedID = nil
        }
    }

    private func addControl(_ title: String, _ kind: PadControl.Kind) -> some View {
        Button(padText(title)) {
            selectedID = profile.virtual.addControl(kind)
        }
        .disabled(profile.virtual.controls.count >= 32 ||
            (kind != .button && profile.virtual.controls.contains { $0.kind == kind }))
    }

    private func modePicker(_ title: String, mode: Binding<PadMode>, modes: [PadMode] = PadMode.allCases) -> some View {
        Picker(padText(title), selection: mode) {
            ForEach(modes) { mode in Text(mode.title).tag(mode) }
        }
    }

    private func actionPicker(_ title: String, action: Binding<PadAction>) -> some View {
        Picker(padText(title), selection: action) {
            ForEach(PadAction.choices.indices, id: \.self) { index in
                let (label, action) = PadAction.choices[index]
                Text(padText(label)).tag(action)
            }
        }
    }

    private func slider(_ title: String, value: Binding<Double>, range: ClosedRange<Double>, fraction: Bool = true) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            HStack {
                Text(padText(title))
                Spacer()
                Text("\(Int((value.wrappedValue * (fraction ? 100 : 1)).rounded()))%").foregroundColor(.secondary)
            }
            Slider(value: value, in: range, step: fraction ? 0.01 : 1) { Text(padText(title)) }
        }
    }
}
