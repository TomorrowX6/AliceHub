import UIKit
import SwiftUI

final class RuntimeGamepad: UIView {
    private static var session: (game: String, engine: GameEngine)?
    private static weak var overlay: RuntimeGamepad?

    static func prepare(for game: String, engine: GameEngine) {
        session = (game, engine)
        alicehub_ios_set_gamepad_callbacks({ window in
            guard let window, let session = RuntimeGamepad.session else { return }
            RuntimeGamepad.overlay?.removeFromSuperview()
            let overlay = RuntimeGamepad(game: session.game, engine: session.engine)
            overlay.frame = window.bounds
            overlay.autoresizingMask = [.flexibleWidth, .flexibleHeight]
            window.addSubview(overlay)
            RuntimeGamepad.overlay = overlay
        }, { command in
            RuntimeGamepad.overlay?.run(command: command)
        }, { x, y, cursor, movie, _, ctrl in
            RuntimeGamepad.overlay?.update(x: x, y: y, cursor: cursor, movie: movie, ctrl: ctrl)
        })
    }

    private let store = GamepadProfiles()
    private let game: String
    private let engine: GameEngine
    private var profile: GamepadProfile
    private var controls: [TouchPadControl] = []
    private var inMovie = false
    private let menuButton = UIButton(type: .system)
    private let menuPanel = UIStackView()
    private let virtualToggle = UIButton(type: .system)
    private let physicalToggle = UIButton(type: .system)
    private weak var editor: UIViewController?
    private let sensitivityLabel = UILabel()
    private let sensitivitySlider = UISlider()
    private let cursorView = UIImageView(image: UIImage(systemName: "cursorarrow.rays"))
    private var cursorPosition = CGPoint(x: 0.5, y: 0.5)

    private init(game: String, engine: GameEngine) {
        self.game = game
        self.engine = engine
        profile = store.active(game)
        super.init(frame: .zero)
        backgroundColor = .clear

        menuButton.setImage(UIImage(systemName: "gamecontroller"), for: .normal)
        menuButton.tintColor = .white
        menuButton.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        menuButton.layer.cornerRadius = 12
        menuButton.accessibilityLabel = padText("Gamepad")
        menuButton.addAction(UIAction { [weak self] _ in self?.toggleMenu() }, for: .touchUpInside)
        addSubview(menuButton)

        cursorView.isUserInteractionEnabled = false
        cursorView.tintColor = .white
        cursorView.layer.shadowColor = UIColor.black.cgColor
        cursorView.layer.shadowOpacity = 1
        cursorView.layer.shadowRadius = 2
        cursorView.layer.shadowOffset = .zero
        cursorView.isHidden = true
        addSubview(cursorView)

        menuPanel.axis = .vertical
        menuPanel.spacing = 8
        menuPanel.isLayoutMarginsRelativeArrangement = true
        menuPanel.layoutMargins = UIEdgeInsets(top: 12, left: 20, bottom: 12, right: 20)
        menuPanel.backgroundColor = UIColor(white: 0.12, alpha: 0.96)
        menuPanel.layer.cornerRadius = 12
        for button in [virtualToggle, physicalToggle] {
            button.tintColor = .white
            menuPanel.addArrangedSubview(button)
        }
        physicalToggle.isHidden = engine == .system3
        virtualToggle.addAction(UIAction { [weak self] _ in self?.toggleVirtual() }, for: .touchUpInside)
        physicalToggle.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.profile.physical.enabled.toggle()
            self.saveProfile()
        }, for: .touchUpInside)
        sensitivityLabel.textColor = .white
        sensitivityLabel.font = .systemFont(ofSize: 13, weight: .medium)
        sensitivityLabel.textAlignment = .center
        sensitivitySlider.minimumValue = 20
        sensitivitySlider.maximumValue = 300
        sensitivitySlider.widthAnchor.constraint(greaterThanOrEqualToConstant: 180).isActive = true
        sensitivitySlider.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            let pct = Double(Int(self.sensitivitySlider.value.rounded()))
            self.profile.physical.cursorSpeedPct = pct
            self.sensitivityLabel.text = "\(padText("Sensitivity")): \(Int(pct))%"
            self.applyEngineProfile()
            if !self.sensitivitySlider.isTracking {
                self.store.save(self.profile, for: self.store.saved(self.game) == nil ? nil : self.game)
            }
        }, for: .valueChanged)
        sensitivitySlider.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            self.store.save(self.profile, for: self.store.saved(self.game) == nil ? nil : self.game)
        }, for: [.touchUpInside, .touchUpOutside, .touchCancel])
        menuPanel.addArrangedSubview(sensitivityLabel)
        menuPanel.addArrangedSubview(sensitivitySlider)
        let edit = UIButton(type: .system)
        edit.setTitle(padText("Edit Layout"), for: .normal)
        edit.tintColor = .white
        edit.addAction(UIAction { [weak self] _ in self?.openEditor() }, for: .touchUpInside)
        menuPanel.addArrangedSubview(edit)
        let close = UIButton(type: .system)
        close.setTitle(padText("Close"), for: .normal)
        close.tintColor = .white
        close.addAction(UIAction { [weak self] _ in self?.toggleMenu() }, for: .touchUpInside)
        menuPanel.addArrangedSubview(close)
        menuPanel.isHidden = true
        addSubview(menuPanel)
        applyProfile()
        NotificationCenter.default.addObserver(self, selector: #selector(releaseInputs),
            name: UIScene.willDeactivateNotification, object: nil)
    }

    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        let safeBounds = bounds.inset(by: safeAreaInsets)
        for control in controls {
            control.bounds.size = control.spec.size(in: safeBounds, globalScale: profile.virtual.scale)
            control.center = control.spec.center(in: safeBounds)
        }
        menuButton.frame = CGRect(x: safeBounds.midX - 22, y: safeBounds.minY + 4, width: 44, height: 36)
        let size = menuPanel.systemLayoutSizeFitting(UIView.layoutFittingCompressedSize)
        menuPanel.frame = CGRect(x: safeBounds.midX - size.width / 2, y: safeBounds.midY - size.height / 2,
                                 width: size.width, height: size.height)
        layoutCursor()
    }

    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        let hit = super.hitTest(point, with: event)
        return hit === self && menuPanel.isHidden ? nil : hit
    }

    private func applyProfile() {
        releaseInputs()
        applyEngineProfile()
        controls.forEach { $0.removeFromSuperview() }
        controls = profile.virtual.controls.filter(\.visible).map { spec in
            let control = TouchPadControl(spec: spec, opacity: profile.virtual.opacity, engine: engine)
            insertSubview(control, belowSubview: menuButton)
            return control
        }
        refreshVisibility()
        virtualToggle.setTitle(padText(profile.virtual.enabled ? "Hide Virtual Gamepad" : "Show Virtual Gamepad"), for: .normal)
        physicalToggle.setTitle(padText(profile.physical.enabled ? "Disable Controller" : "Enable Controller"), for: .normal)
        let speedPct = min(300, max(20, Int(profile.physical.cursorSpeedPct.rounded())))
        sensitivitySlider.value = Float(speedPct)
        sensitivityLabel.text = "\(padText("Sensitivity")): \(speedPct)%"
        setNeedsLayout()
    }

    private func applyEngineProfile() {
        profile.engineJSON().withCString { json in
            if engine == .system3 { xsystem35_set_gamepad_config_json(json) }
            else { input_set_gamepad_config_json(json) }
        }
    }

    private func saveProfile() {
        store.save(profile, for: store.saved(game) == nil ? nil : game)
        applyProfile()
    }

    private func toggleVirtual() {
        profile.virtual.enabled.toggle()
        saveProfile()
    }

    private func toggleMenu() {
        guard !inMovie, editor == nil else { return }
        menuPanel.isHidden.toggle()
        refreshVisibility()
    }

    private func run(command: Int32) {
        switch command {
        case Int32(INPUT_EMU_TOGGLE_VPAD.rawValue): toggleVirtual()
        case Int32(INPUT_EMU_OPEN_MENU.rawValue): toggleMenu()
        case Int32(INPUT_EMU_OPEN_EDITOR.rawValue): openEditor()
        case Int32(INPUT_EMU_SCREENSHOT.rawValue) where engine == .system3: saveScreenshot()
        default: break
        }
    }

    private func refreshVisibility() {
        let hideControls = !profile.virtual.enabled || inMovie || !menuPanel.isHidden || editor != nil
        if hideControls && controls.contains(where: { !$0.isHidden }) { releaseInputs() }
        controls.forEach { $0.isHidden = hideControls }
        menuButton.isHidden = inMovie || editor != nil
        if !menuPanel.isHidden || editor != nil { cursorView.isHidden = true }
    }

    @objc private func releaseInputs() {
        controls.forEach { $0.reset() }
        if engine == .system3 { xsystem35_clear_virtual_inputs() }
        else { input_clear_virtual_inputs() }
    }

    private func saveScreenshot() {
        guard let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return }
        let folder = documents.appendingPathComponent("Screenshots", isDirectory: true)
        guard (try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)) != nil else { return }
        let file = folder.appendingPathComponent("System35-\(UUID().uuidString).bmp")
        _ = file.path.withCString { xsystem35_save_screenshot($0) }
    }

    private func openEditor() {
        guard !inMovie, editor == nil, let window, let host = window.rootViewController,
              host.presentedViewController == nil else { return }
        releaseInputs()
        menuPanel.isHidden = true
        let panel = UIHostingController(rootView: GamepadEditor(game: game, allowsPhysicalMapping: engine == .system4) { [weak self] in
            self?.closeEditor()
        })
        panel.modalPresentationStyle = .overFullScreen
        panel.isModalInPresentation = true
        editor = panel
        refreshVisibility()
        host.present(panel, animated: true)
    }

    private func closeEditor() {
        editor?.dismiss(animated: true) { [weak self] in
            guard let self else { return }
            self.editor = nil
            self.window?.makeKeyAndVisible()
            self.profile = self.store.active(self.game)
            self.applyProfile()
        }
    }

    private func update(x: Float, y: Float, cursor: Bool, movie: Bool, ctrl: Bool) {
        let showCursor = cursor && menuPanel.isHidden && editor == nil
        if cursorView.isHidden == showCursor { cursorView.isHidden = !showCursor }
        if showCursor {
            let position = CGPoint(x: Double(x), y: Double(y))
            if position != cursorPosition {
                cursorPosition = position
                layoutCursor()
            }
        }
        if movie != inMovie {
            inMovie = movie
            if movie { menuPanel.isHidden = true }
            refreshVisibility()
        }
        for control in controls where control.spec.action == .ctrl {
            control.setLatched(ctrl)
        }
    }

    private func layoutCursor() {
        cursorView.frame = CGRect(x: cursorPosition.x * bounds.width, y: cursorPosition.y * bounds.height,
                                  width: 24, height: 24)
    }
}

private final class TouchPadControl: UIView {
    let spec: PadControl
    private let label = UILabel()
    private let thumb = UIView()
    private var activeTouch: UITouch?
    private var axis = CGPoint.zero
    private var latched = false
    private let opacity: Double
    private let engine: GameEngine

    init(spec: PadControl, opacity: Double, engine: GameEngine) {
        self.spec = spec
        self.opacity = opacity * spec.opacity
        self.engine = engine
        super.init(frame: .zero)
        label.text = spec.kind == .dpad ? "✚" : padText(spec.label)
        label.font = .systemFont(ofSize: 14, weight: .semibold)
        label.textColor = .white
        label.textAlignment = .center
        label.adjustsFontSizeToFitWidth = true
        label.minimumScaleFactor = 0.5
        thumb.backgroundColor = UIColor.white.withAlphaComponent(0.28)
        thumb.isHidden = spec.kind != .leftStick && spec.kind != .rightStick
        addSubview(thumb)
        addSubview(label)
        isAccessibilityElement = true
        accessibilityLabel = padText(spec.label)
        accessibilityTraits = spec.kind == .button ? .button : []
        layer.borderWidth = 1
        updateColor()
    }

    required init?(coder: NSCoder) { nil }

    override func layoutSubviews() {
        super.layoutSubviews()
        layer.cornerRadius = spec.shape == .square ? 12 : min(bounds.width, bounds.height) / 2
        label.frame = bounds.insetBy(dx: 3, dy: 0)
        if !thumb.isHidden {
            let size = bounds.width * 0.45
            thumb.bounds = CGRect(x: 0, y: 0, width: size, height: size)
            thumb.center = CGPoint(x: bounds.midX + axis.x * bounds.width * 0.26,
                                   y: bounds.midY + axis.y * bounds.height * 0.26)
            thumb.layer.cornerRadius = size / 2
        }
    }

    override func point(inside point: CGPoint, with event: UIEvent?) -> Bool {
        super.point(inside: point, with: event) &&
            UIBezierPath(roundedRect: bounds, cornerRadius: layer.cornerRadius).contains(point)
    }

    override func accessibilityActivate() -> Bool {
        guard spec.kind == .button else { return false }
        sendButton(true)
        sendButton(false)
        return true
    }

    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard activeTouch == nil, let touch = touches.first else { return }
        activeTouch = touch
        if spec.kind == .button { sendButton(true) } else { updateAxis(touch.location(in: self)) }
        updateColor()
    }

    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = activeTouch, touches.contains(touch), spec.kind != .button else { return }
        updateAxis(touch.location(in: self))
    }

    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        if let touch = activeTouch, touches.contains(touch) { reset() }
    }

    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        touchesEnded(touches, with: event)
    }

    func reset() {
        guard activeTouch != nil else { return }
        activeTouch = nil
        if spec.kind == .button { sendButton(false) }
        else { updateAxis(CGPoint(x: bounds.midX, y: bounds.midY)) }
        updateColor()
    }

    func setLatched(_ value: Bool) {
        guard latched != value else { return }
        latched = value
        updateColor()
    }

    private func sendButton(_ pressed: Bool) {
        let action = spec.action
        let setButton = engine == .system3 ? xsystem35_set_virtual_button : input_set_virtual_button
        setButton(spec.slot, action.type, action.code, action.code2, action.toggle, pressed)
    }

    private func updateAxis(_ point: CGPoint) {
        let x = (point.x - bounds.midX) / max(1, bounds.midX)
        let y = (point.y - bounds.midY) / max(1, bounds.midY)
        let length = max(1, hypot(x, y))
        axis = CGPoint(x: x / length, y: y / length)
        switch spec.kind {
        case .dpad:
            let setDPad = engine == .system3 ? xsystem35_set_virtual_dpad : input_set_virtual_dpad
            setDPad(Int32(spec.mode.rawValue), x < -0.26, x > 0.26, y < -0.26, y > 0.26)
        case .leftStick, .rightStick:
            let setStick = engine == .system3 ? xsystem35_set_virtual_stick : input_set_virtual_stick
            setStick(spec.kind == .leftStick ? 0 : 1, Float(axis.x), Float(axis.y))
            setNeedsLayout()
        case .button: break
        }
    }

    private func updateColor() {
        let highlighted = activeTouch != nil || latched
        backgroundColor = (highlighted ? UIColor.systemBlue : UIColor.black).withAlphaComponent(opacity)
        layer.borderColor = UIColor.white.withAlphaComponent(min(1, opacity * 1.4)).cgColor
        label.alpha = min(1, opacity * 1.6)
    }
}
