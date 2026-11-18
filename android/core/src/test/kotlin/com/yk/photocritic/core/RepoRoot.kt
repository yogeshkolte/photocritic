package com.yk.photocritic.core

import java.io.File

/** The repository root, passed by Gradle, so tests read `shared/` (design file 39). */
object RepoRoot {
    private val dir = File(checkNotNull(System.getProperty("pc.repoRoot")) { "pc.repoRoot not set" })

    fun shared(path: String): String = File(dir, "shared/$path").readText()

    fun sharedBytes(path: String): ByteArray = File(dir, "shared/$path").readBytes()

    /** The `.json` file names in a `shared/` folder, sorted; empty if the folder does not exist. */
    fun sharedJsonFiles(folder: String): List<String> =
        File(dir, "shared/$folder")
            .listFiles { file -> file.name.endsWith(".json") }
            .orEmpty()
            .map { it.name }
            .sorted()
}
