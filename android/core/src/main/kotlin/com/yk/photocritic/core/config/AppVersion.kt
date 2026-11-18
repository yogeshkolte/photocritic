package com.yk.photocritic.core.config

/**
 * A `MAJOR.MINOR.PATCH` version, compared by number so `1.10.0` is above
 * `1.9.0` (design file 26). Cases: `shared/vectors/app-version.json`.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int = compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val pattern = Regex("""(\d{1,9})\.(\d{1,9})\.(\d{1,9})""")

        /** Null unless [text] is exactly three dot-separated numbers. */
        fun parse(text: String): AppVersion? =
            pattern.matchEntire(text)?.destructured?.let { (major, minor, patch) ->
                AppVersion(major.toInt(), minor.toInt(), patch.toInt())
            }
    }
}
