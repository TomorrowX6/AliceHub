import pathlib
import re
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class ModifierSaveKeysTest(unittest.TestCase):
    def test_swift_editable_keys_match_native_writer(self):
        source = (ROOT / "xsystem4/src/modifier.c").read_text()
        writer = re.search(r"^static bool write_local_save_mod_str\(.*?^}$", source, re.S | re.M).group(0)
        native = set(re.findall(r'\(mod, "([A-Za-z0-9]+)"', writer))
        bridge = (ROOT / "ios/App/ModifierBridge.swift").read_text()
        block = re.search(r"writableSaveKeys: Set<String> = \[(.*?)\]", bridge, re.S).group(1)
        self.assertEqual(set(re.findall(r'"([A-Za-z0-9]+)"', block)), native)

    def test_global_flags_reject_non_boolean_values_and_repair_integer_entries(self):
        source = (ROOT / "xsystem4/src/modifier.c").read_text()
        definitions = re.search(r"^enum csd_value_type \{.*?^struct csd_buf", source, re.S | re.M).group(0)
        definitions = definitions.removesuffix("struct csd_buf")
        functions = re.search(r"^static void csd_table_free\(.*?(?=^static int csd_entry_cmp)", source, re.S | re.M).group(0)
        native_keys = re.search(r"s_csd_r10_keys\[\] = \{(.*?)\};", source, re.S).group(1)
        flags = set(re.findall(r'"(\w+)",\s*true', native_keys))
        editor = (ROOT / "ios/App/GlobalSaveEditorView.swift").read_text()
        self.assertEqual(set(re.findall(r'id: "(\w+)"[^\n]+flag: true', editor)), flags)
        with tempfile.TemporaryDirectory() as temporary:
            test = pathlib.Path(temporary) / "flags.c"
            binary = test.with_suffix("")
            test.write_text(
                "#include <assert.h>\n#include <stdbool.h>\n#include <stdint.h>\n#include <stdlib.h>\n#include <string.h>\n"
                + definitions + functions + """
                int main(void) {
                    struct csd_table table = {0};
                    const char *key = "CardComplete";
                    assert(!csd_table_set_int_or_bool(&table, key, 2, true));
                    assert(table.count == 0);
                    assert(csd_table_set_int_or_bool(&table, key, 2, false));
                    struct csd_entry *flag = csd_table_find(&table, key);
                    assert(flag->type == CSD_INT && flag->i_val == 2);
                    assert(csd_table_set_int_or_bool(&table, key, 1, true));
                    assert(flag->type == CSD_BOOL && flag->i_val == 1);
                    assert(!csd_table_set_int_or_bool(&table, key, -1, true));
                    assert(flag->type == CSD_BOOL && flag->i_val == 1);
                    assert(csd_table_set_int_or_bool(&table, key, 0, true));
                    assert(flag->type == CSD_BOOL && flag->i_val == 0);
                    csd_table_free(&table);
                    return 0;
                }
                """
            )
            subprocess.run(["cc", "-std=gnu11", "-o", str(binary), str(test)], check=True)
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
