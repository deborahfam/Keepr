"""Exercise the exact CREATE statements used by SQLiteOpenHelper against SQLite."""
import json
from pathlib import Path
import re
import sqlite3
import unittest

SOURCE = Path(__file__).resolve().parents[1] / "app/src/main/java/com/keepr/app/data/VaultDatabase.java"


class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.execute("PRAGMA foreign_keys=ON")
        statements = re.findall(r'db\.execSQL\(("(?:[^"\\]|\\.)*")\);', SOURCE.read_text())
        self.assertGreaterEqual(len(statements), 8)
        for statement in statements:
            self.db.execute(json.loads(statement))
        self.db.execute("INSERT INTO Category VALUES ('c', 'Electronics', 1)")
        self.db.execute("INSERT INTO SavedItem(id,url,category_id,created_at,updated_at) VALUES ('i','https://example.com','c',1,1)")

    def test_defaults_and_status_constraint(self):
        self.assertEqual(("ACTIVE", "PENDING"), self.db.execute("SELECT status,preview_state FROM SavedItem").fetchone())
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE SavedItem SET status='UNKNOWN'")

    def test_category_delete_preserves_item(self):
        self.db.execute("DELETE FROM Category")
        self.assertEqual((None,), self.db.execute("SELECT category_id FROM SavedItem").fetchone())

    def test_media_and_tags_cascade_without_deleting_tag(self):
        self.db.execute("INSERT INTO CachedMedia VALUES ('m','i','https://example.com/image','media/m.jpg','image/jpeg',8,'digest',1)")
        self.db.execute("INSERT INTO Tag VALUES ('t','Wishlist')")
        self.db.execute("INSERT INTO SavedItemTag VALUES ('i','t')")
        self.db.execute("DELETE FROM SavedItem")
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM CachedMedia").fetchone()[0])
        self.assertEqual(0, self.db.execute("SELECT count(*) FROM SavedItemTag").fetchone()[0])
        self.assertEqual(1, self.db.execute("SELECT count(*) FROM Tag").fetchone()[0])

    def test_rejects_orphan_media_and_negative_sizes(self):
        for item_id, size in [("missing", 8), ("i", -1)]:
            with self.assertRaises(sqlite3.IntegrityError):
                self.db.execute("INSERT INTO CachedMedia VALUES ('m',?,'https://example.com/image','media/m.jpg','image/jpeg',?,'digest',1)", (item_id, size))
