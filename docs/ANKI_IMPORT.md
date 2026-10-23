# MedMate → Anki (manual import, v0.4 alpha)

**What is exported**

- Open one chapter → **Study Studio** → **تصدير Anki**.
- Choose a safe save location in Android's system document picker.
- The resulting `.tsv` is UTF-8 and contains **exactly 2 columns** separated by one TAB, with one note per line:
  1. `Front`: the flashcard's question, or MCQ question with numbered options.
  2. `Back`: the manually entered answer or correct MCQ option, optional explanation, chapter title, and **PDF page number**.
- High-Yield notes are exported separately using **تصدير High-Yield** to Markdown (`.md`), grouped by PDF page.
- This never transmits files online and never modifies MedMate data.

**Import**

1. Copy/share the exported TSV to a device running Anki that supports text imports (for example Anki Desktop).
2. Choose **Import File**, select the TSV and use **Tab** as field separator; choose note type **Basic**.
3. Map column 1 to Front, column 2 to Back. Inspect several preview cards (especially MCQ and Arabic) before finishing.
4. If you repeat the import, handle duplicate detection in Anki carefully. **Do not assume export is synchronized** or that repeated imports automatically merge. Card due dates, reviews, tags, media, and original PDFs are NOT included.

The export is designed for portable study rather than exact `.apkg` compatibility. Source page numbers are plain text, not clickable Anki links. Export text is **not medical advice** and is based only on the entries you wrote in MedMate. Test on copies before relying on it for important review history.
