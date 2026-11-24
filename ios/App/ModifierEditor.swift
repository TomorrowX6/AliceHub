import SwiftUI

struct ModifierEditor: View {
    @Environment(\.dismiss) private var dismiss
    let title: String
    let fields: [ModifierField]
    let summary: [String]
    @State private var initial: [String: String]
    let quickAction: ModifierCheat?
    let apply: ([String: Any]) -> Void
    @State private var input: [String: String]

    init(_ title: String, fields: [ModifierField], state: [String: Any], summary: [String] = [],
         quickAction: ModifierCheat? = nil, apply: @escaping ([String: Any]) -> Void) {
        self.title = title
        self.fields = fields
        self.summary = summary
        self.quickAction = quickAction
        self.apply = apply
        let values = Dictionary(uniqueKeysWithValues: fields.map { ($0.id, $0.initialValue(in: state)) })
        _initial = State(initialValue: values)
        _input = State(initialValue: values)
    }

    var body: some View {
        let changes = ModifierField.changes(from: input, excluding: initial)
        let hasInvalid = input.values.contains {
            let value = $0.trimmingCharacters(in: .whitespacesAndNewlines)
            return !value.isEmpty && Int32(value) == nil
        }
        Form {
            if !summary.isEmpty {
                Section {
                    ForEach(summary, id: \.self) { Text($0) }
                }
            }
            Section {
                ForEach(fields) { field in
                    HStack {
                        Text(field.title)
                        Spacer()
                        TextField("不修改", text: Binding(
                            get: { input[field.id, default: ""] },
                            set: { input[field.id] = $0 }
                        ))
                        .keyboardType(.numbersAndPunctuation)
                        .multilineTextAlignment(.trailing)
                        .monospacedDigit()
                        .accessibilityLabel(field.title)
                    }
                }
            }
            if let quickAction {
                Section {
                    Button(quickAction.title) {
                        apply(quickAction.action)
                        dismiss()
                    }
                }
            }
        }
        .modifier(HubListStyle())
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("应用") {
                    apply(changes)
                    dismiss()
                }.disabled(changes.isEmpty || hasInvalid)
            }
        }
    }
}
