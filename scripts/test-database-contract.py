#!/usr/bin/env python3
"""SQLite contract checks that run without Android SDK or external packages.

This is NOT a replacement for Android's SQLiteOpenHelper migration/instrumentation tests.
"""
from pathlib import Path
import re
import sqlite3

root = Path(__file__).resolve().parents[1]
src = (root/'app/src/main/java/com/medmate/personal/data/MedMateDatabase.kt').read_text()
back = (root/'app/src/main/java/com/medmate/personal/data/BackupManager.kt').read_text()

sqls = []
for sql in re.findall(r'db\.execSQL\(\s*"""(.*?)"""', src, re.S):
    sqls.append(sql.strip())
for sql in re.findall(r'db\.execSQL\(\s*"(CREATE [^"\n]+)"\)', src):
    sqls.append(sql)
assert len(sqls) == 8, f'unexpected number of schema statements: {len(sqls)}'
conn = sqlite3.connect(':memory:')
conn.execute('PRAGMA foreign_keys=ON')
for sql in sqls:
    conn.execute(sql)
# Reapplying additive migration helpers must not drop/duplicate existing study rows.
sha = 'c' * 64
conn.execute('INSERT INTO chapters VALUES (?,?,?,?,?,?,?)', (sha, 'Chapter',sha+'.pdf',128,3,1,123))
conn.execute('INSERT INTO page_notes VALUES (?,?,?,?)', (sha,1,'important',456))
conn.execute('INSERT INTO study_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
             ('a'*36,sha,1,'FLASHCARD','Question','Answer','[]',-1,'',123,123,0,2.3,0,0))
conn.execute('INSERT INTO study_reviews VALUES (?,?,?,?,?,?)', ('b'*36,'a'*36,2,-1,1,123))
for sql in sqls:
    if 'IF NOT EXISTS' in sql:
        conn.execute(sql)
assert conn.execute('SELECT note_text FROM page_notes').fetchone()[0] == 'important'
assert conn.execute('SELECT count(*) FROM study_reviews').fetchone()[0] == 1
try:
    conn.execute('INSERT INTO page_notes VALUES (?,?,?,?)', ('e'*64,1,'bad',1))
    raise AssertionError('Foreign key was not enforced')
except sqlite3.IntegrityError:
    pass
try:
    conn.execute('INSERT INTO study_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',
                 ('d'*36,sha,1,'NOT_A_KIND','x','','[]',-1,'',123,123,0,2.3,0,0))
    raise AssertionError('StudyKind constraint was not enforced')
except sqlite3.IntegrityError:
    pass
conn.execute('DELETE FROM chapters WHERE sha256=?', (sha,))
assert conn.execute('SELECT COUNT(*) FROM page_notes').fetchone()[0] == 0
assert conn.execute('SELECT COUNT(*) FROM study_reviews').fetchone()[0] == 0

# In v0.4 the backup mapper read eight cursor columns for a seven-column SELECT.
projection = re.search(r'"SELECT sha256,title,stored_name,bytes,page_count,last_page,added_at FROM chapters ORDER BY added_at".*?\)\.use \{ c -> while \(c.moveToNext\(\)\) chapters \+= Chapter\((.*?)\n\s*\)\s*\}', back, re.S)
assert projection, 'Backup export chapter projection has changed: review mapping manually'
indices = [int(n) for n in re.findall(r'c\.get(?:String|Int|Long)\((\d+)\)', projection.group(1))]
assert indices == list(range(7)), f'Broken backup cursor field mapping: {indices}'
assert 'verifyExistingStudyItem(db, j)' in back
assert 'verifyExistingReview(db, j)' in back
assert 'createdDocuments.forEach { it.delete() }' in back
print('PASS: SQLite schema, constraints, cascading deletes, additive DDL, backup column mapping and merge guards')
