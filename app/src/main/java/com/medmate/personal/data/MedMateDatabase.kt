package com.medmate.personal.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Explicit, conservative schema. Future changes must use migrations, never drop study data. */
internal class MedMateDatabase(context: Context) :
    SQLiteOpenHelper(context, "medmate.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE chapters (
                sha256 TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                stored_name TEXT NOT NULL,
                bytes INTEGER NOT NULL,
                page_count INTEGER NOT NULL,
                last_page INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE page_notes (
                chapter_sha256 TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                note_text TEXT NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(chapter_sha256, page_index),
                FOREIGN KEY(chapter_sha256) REFERENCES chapters(sha256) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_chapters_added ON chapters(added_at DESC)")
        createPageTextTable(db)
        createStudyTables(db)
    }

    private fun createPageTextTable(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS page_text (
                chapter_sha256 TEXT NOT NULL,
                page_index INTEGER NOT NULL,
                text_content TEXT NOT NULL,
                extracted_at INTEGER NOT NULL,
                PRIMARY KEY(chapter_sha256, page_index),
                FOREIGN KEY(chapter_sha256) REFERENCES chapters(sha256) ON DELETE CASCADE
            )
        """.trimIndent())
    }

    private fun createStudyTables(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS study_items (
            id TEXT PRIMARY KEY NOT NULL,
            chapter_sha256 TEXT NOT NULL,
            page_index INTEGER NOT NULL,
            kind TEXT NOT NULL CHECK(kind IN ('HIGH_YIELD','FLASHCARD','MCQ')),
            prompt TEXT NOT NULL,
            answer TEXT NOT NULL DEFAULT '',
            options_json TEXT NOT NULL DEFAULT '[]',
            correct_index INTEGER NOT NULL DEFAULT -1,
            explanation TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            due_at INTEGER NOT NULL,
            interval_days INTEGER NOT NULL DEFAULT 0,
            ease REAL NOT NULL DEFAULT 2.3,
            repetitions INTEGER NOT NULL DEFAULT 0,
            lapses INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(chapter_sha256) REFERENCES chapters(sha256) ON DELETE CASCADE
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_chapter_due ON study_items(chapter_sha256, due_at)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS study_reviews (
            id TEXT PRIMARY KEY NOT NULL,
            item_id TEXT NOT NULL,
            rating INTEGER NOT NULL CHECK(rating BETWEEN 0 AND 3),
            selected_index INTEGER NOT NULL DEFAULT -1,
            was_correct INTEGER NOT NULL CHECK(was_correct IN (0,1)),
            reviewed_at INTEGER NOT NULL,
            FOREIGN KEY(item_id) REFERENCES study_items(id) ON DELETE CASCADE
        )""".trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_study_reviews_item ON study_reviews(item_id, reviewed_at DESC)")
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion !in 1..2 || newVersion != 3) {
            error("Unsupported non-destructive database migration: $oldVersion to $newVersion")
        }
        if (oldVersion == 1) createPageTextTable(db)
        createStudyTables(db)
    }
}
