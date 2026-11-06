package com.dish.newfolderview

/**
 * One tile in a folder. Mirrors a single line of the config file:
 *   type,Label,target,icon
 *
 * type   -> ItemType (app | web | zinc)
 * label  -> text shown under the icon
 * target -> app: process/package name (may be "pkg/Activity")
 *           web/zinc: full URL
 * icon   -> "default" (app launcher icon) OR a path like /.pics/foo.png
 */
data class FolderItem(
    val type: ItemType,
    val label: String,
    val target: String,
    val icon: String
) {
    val usesDefaultIcon: Boolean
        get() = icon.equals("default", ignoreCase = true)

    /** For app entries that specify "pkg/Activity", split into components. */
    val packageName: String
        get() = if (type == ItemType.APP && target.contains("/"))
            target.substringBefore("/") else target

    val explicitActivity: String?
        get() = if (type == ItemType.APP && target.contains("/"))
            target.substringAfter("/") else null
}

enum class ItemType {
    APP, WEB, ZINC;

    companion object {
        fun from(raw: String): ItemType? = when (raw.trim().lowercase()) {
            "app" -> APP
            "web" -> WEB
            "zinc" -> ZINC
            else -> null
        }
    }
}

/**
 * A named folder = a section header plus its items.
 * Folder names are DATA, never hardcoded. Any name is valid.
 */
data class Folder(
    val name: String,
    val items: List<FolderItem>
)
