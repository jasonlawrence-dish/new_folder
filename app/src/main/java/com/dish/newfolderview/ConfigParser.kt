package com.dish.newfolderview

import android.util.Log

/**
 * Parses the folder config text into a list of [Folder]s.
 *
 * DESIGN GOALS (fixes vs. the original Folderview 4.0.6):
 *  - Folder names are DATA, not hardcoded. Any `Folder:<Name>` works.
 *  - No `NumberOfApps:` counter required. If present, it is IGNORED
 *    (the old count line caused two classes of crash). We trust the
 *    actual item lines.
 *  - Fully null-safe: never calls .equals() on a possibly-null token.
 *    Malformed lines are skipped and logged, never fatal.
 *  - Tolerates BOM, CR/LF, blank lines, and stray whitespace.
 *
 * Grammar (one item per line, comma-separated):
 *   Folder:<Name>                         section header, starts a folder
 *   NumberOfApps:<n>                       OPTIONAL legacy line, ignored
 *   app,<Label>,<process|pkg/Activity>,<default|/.pics/x.png>
 *   web,<Label>,<FULL_URL>,/.pics/x.png
 *   zinc,<Label>,<hotlineURL>,/.pics/x.png
 *
 * Lines before the first `Folder:` header are ignored.
 */
object ConfigParser {

    private const val TAG = "NFV.ConfigParser"

    data class Result(
        val folders: List<Folder>,
        val warnings: List<String>
    )

    fun parse(raw: String?): Result {
        val warnings = mutableListOf<String>()
        if (raw.isNullOrBlank()) {
            warnings.add("Config was empty or could not be read.")
            return Result(emptyList(), warnings)
        }

        // Strip UTF-8 BOM if present, normalize newlines.
        val normalized = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n")
        // WS1 (and some MDM config fields) collapse line breaks into spaces.
        // Re-insert a newline before each record keyword so a single flattened
        // line parses identically to proper multi-line input.
        val text = reinsertRecordBreaks(normalized)

        val folders = mutableListOf<Folder>()
        var currentName: String? = null
        var currentItems = mutableListOf<FolderItem>()

        fun flush() {
            val name = currentName ?: return
            folders.add(Folder(name, currentItems.toList()))
            currentItems = mutableListOf()
        }

        text.split("\n").forEachIndexed { idx, lineRaw ->
            val line = lineRaw.trim()
            val lineNo = idx + 1

            // Skip blanks and comments.
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("'")) return@forEachIndexed

            // Section header.
            if (line.startsWith("Folder:", ignoreCase = true)) {
                flush()
                currentName = line.substringAfter(":").trim()
                if (currentName.isNullOrEmpty()) {
                    warnings.add("Line $lineNo: empty folder name.")
                    currentName = "Folder"
                }
                return@forEachIndexed
            }

            // Legacy count line — intentionally ignored.
            if (line.startsWith("NumberOfApps:", ignoreCase = true) ||
                line.startsWith("NumberofApps:", ignoreCase = true)
            ) {
                return@forEachIndexed
            }

            // Item line.
            if (currentName == null) {
                warnings.add("Line $lineNo: item before any Folder: header, skipped.")
                return@forEachIndexed
            }

            val item = parseItem(line, lineNo, warnings)
            if (item != null) currentItems.add(item)
        }
        flush()

        if (folders.isEmpty()) warnings.add("No folders found in config.")
        warnings.forEach { Log.w(TAG, it) }
        return Result(folders, warnings)
    }

    /**
     * Insert a line break before each record keyword (Folder:, app,, web,, zinc,,
     * NumberOfApps:) when it is preceded by spaces/tabs. This recovers records from
     * configs whose newlines were flattened to spaces by an MDM config field, while
     * leaving already-multi-line input unchanged (keywords there follow a newline,
     * not a space). NOTE: avoid ending a label with the bare word app/web/zinc
     * immediately followed by a comma, as that could be mistaken for a record start.
     */
    private fun reinsertRecordBreaks(text: String): String {
        val re = Regex(
            "[ \\t]+(?=(?:app,|web,|zinc,|Folder:|NumberOfApps:|NumberofApps:))",
            RegexOption.IGNORE_CASE
        )
        return re.replace(text, "\n")
    }

    private fun parseItem(line: String, lineNo: Int, warnings: MutableList<String>): FolderItem? {
        // Split into at most 4 fields; URLs may contain commas is rare, but
        // our grammar uses exactly 4 fields: type,label,target,icon.
        // We split the FIRST two commas, then the LAST comma is the icon,
        // so the target (URL) may safely contain commas.
        val firstComma = line.indexOf(',')
        if (firstComma < 0) {
            warnings.add("Line $lineNo: no commas, skipped: \"$line\"")
            return null
        }
        val typeRaw = line.substring(0, firstComma)
        val type = ItemType.from(typeRaw)
        if (type == null) {
            warnings.add("Line $lineNo: unknown type \"$typeRaw\", skipped.")
            return null
        }

        val rest = line.substring(firstComma + 1)
        val secondComma = rest.indexOf(',')
        if (secondComma < 0) {
            warnings.add("Line $lineNo: missing fields, skipped: \"$line\"")
            return null
        }
        val label = rest.substring(0, secondComma).trim()

        val afterLabel = rest.substring(secondComma + 1)
        val lastComma = afterLabel.lastIndexOf(',')
        if (lastComma < 0) {
            warnings.add("Line $lineNo: missing icon field, skipped: \"$line\"")
            return null
        }
        val target = afterLabel.substring(0, lastComma).trim()
        val icon = afterLabel.substring(lastComma + 1).trim()

        if (label.isEmpty() || target.isEmpty() || icon.isEmpty()) {
            warnings.add("Line $lineNo: blank field, skipped: \"$line\"")
            return null
        }

        // web/zinc must have a real icon path, never "default".
        if ((type == ItemType.WEB || type == ItemType.ZINC) &&
            icon.equals("default", ignoreCase = true)
        ) {
            warnings.add("Line $lineNo: ${type.name.lowercase()} entry needs an icon path, not 'default'. Using fallback icon.")
            // Keep the item but mark icon invalid -> renderer falls back.
            return FolderItem(type, label, target, "")
        }

        return FolderItem(type, label, target, icon)
    }
}
