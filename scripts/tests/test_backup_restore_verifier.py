import importlib.util
from pathlib import Path
import unittest


spec = importlib.util.spec_from_file_location("backup_verifier", Path(__file__).resolve().parents[1] / "verify-backup-restore.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class StringArrayCastTests(unittest.TestCase):
    source = "CHECK (state::text = ANY ((ARRAY['READY'::character varying, 'RUNNING'::character varying])::text[]))"
    restored = "CHECK (state::text = ANY (ARRAY[('READY'::character varying)::text, ('RUNNING'::character varying)::text]))"

    def test_postgresql_reparsed_casts_compare_equally(self):
        self.assertEqual(verifier.normalize_string_array_casts(self.source), self.restored)
        self.assertEqual(verifier.normalize_string_array_casts(self.restored), self.restored)

    def test_changed_allowed_values_remain_different(self):
        self.assertNotEqual(verifier.normalize_string_array_casts(self.source), self.restored.replace("'READY'", "'FAILED'"))

    def test_changed_operators_and_order_remain_different(self):
        self.assertNotEqual(verifier.normalize_string_array_casts(self.source), self.restored.replace("ANY", "ALL"))
        self.assertNotEqual(verifier.normalize_string_array_casts(self.source), self.restored.replace("'READY'", "'TMP'").replace("'RUNNING'", "'READY'").replace("'TMP'", "'RUNNING'"))

    def test_other_cast_types_and_lengths_are_untouched(self):
        for definition in ("(ARRAY['x'::character varying(3)])::text[]", "(ARRAY[1::numeric, 2::numeric])::text[]", "(ARRAY['x'::text])::character varying[]"):
            self.assertEqual(verifier.normalize_string_array_casts(definition), definition)

    def test_quoted_sql_and_comments_are_untouched(self):
        for definition in ("SELECT '" + self.source.replace("'", "''") + "'", 'SELECT "' + self.source + '"', "-- " + self.source, "/* " + self.source + " */", "$tag$" + self.source + "$tag$"):
            self.assertEqual(verifier.normalize_string_array_casts(definition), definition)

    def test_escaped_literal_contents_are_preserved(self):
        source = "(ARRAY['can''t'::character varying])::text[]"
        self.assertEqual(verifier.normalize_string_array_casts(source), "ARRAY[('can''t'::character varying)::text]")


if __name__ == "__main__":
    unittest.main()
