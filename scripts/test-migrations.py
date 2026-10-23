#!/usr/bin/env python3
"""Exercise SQL-equivalent v1/v2 -> v3 migration with realistic preserved rows.

These are SQLite contract tests, NOT actual SQLiteOpenHelper instrumentation.
"""
from pathlib import Path
import re
import sqlite3

base=Path(__file__).resolve().parents[1]
source=(base/'app/src/main/java/com/medmate/personal/data/MedMateDatabase.kt').read_text()

def find_sql(marker, index=0):
    pat=re.escape(marker)+r'.*?db\.execSQL\(\s*"""(.*?)"""'
    values=re.findall(pat,source,re.S)
    assert len(values)>index, (marker,index)
    return values[index].strip()

# Extract creation statements from production Kotlin to minimize drift.
create=re.findall(r'db\.execSQL\(\s*"""(.*?)"""',source,re.S)
indices=re.findall(r'db\.execSQL\("(CREATE INDEX[^"\n]+)"\)',source)
assert len(create)==5 and len(indices)==3, (len(create),indices)
chapters,notes,page_text,study_items,study_reviews=create
chapter_index=indices[0]
study_index=re.search(r'db\.execSQL\("(CREATE INDEX IF NOT EXISTS idx_study_chapter_due[^"\n]+)"\)',source).group(1)
review_index=re.search(r'db\.execSQL\("(CREATE INDEX IF NOT EXISTS idx_study_reviews_item[^"\n]+)"\)',source).group(1)

for old in (1,2):
    db=sqlite3.connect(':memory:')
    db.execute('PRAGMA foreign_keys=ON')
    for statement in (chapters,notes,chapter_index): db.execute(statement)
    if old==2: db.execute(page_text)
    sha='f'*64
    db.execute('INSERT INTO chapters VALUES (?,?,?,?,?,?,?)',(sha,'Anatomy','f'*64+'.pdf',2048,4,2,1))
    db.execute('INSERT INTO page_notes VALUES (?,?,?,?)',(sha,2,'ملاحظة طبية مهمة',456))
    if old==2: db.execute('INSERT INTO page_text VALUES (?,?,?,?)',(sha,2,'The tibia',123))
    # Simulate exact production onUpgrade: v1 additionally creates page_text;
    # both paths create study tables/indexes and do not drop older tables.
    if old==1: db.execute(page_text)
    for statement in (study_items,study_index,study_reviews,review_index): db.execute(statement)
    assert db.execute('SELECT last_page FROM chapters').fetchone()==(2,)
    assert db.execute('SELECT note_text FROM page_notes').fetchone()==('ملاحظة طبية مهمة',)
    if old==2: assert db.execute('SELECT text_content FROM page_text').fetchone()==('The tibia',)
    db.execute('INSERT INTO study_items VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)',(
        '1'*36,sha,2,'FLASHCARD','Bone?','Tibia','[]',-1,'',123,123,0,2.3,0,0))
    assert db.execute('SELECT COUNT(*) FROM study_items').fetchone()==(1,)
    db.close()
    print(f'PASS: SQL migration contract v{old} -> v3, existing note/page retained')
