package com.yk.photocritic.core

import java.io.File

/** The repository root, passed by Gradle, so tests read `shared/` (design file 39). */
object RepoRoot {
    private val dir = File(checkNotNull(System.getProperty("pc.repoRoot")) { "pc.repoRoot not set" })

    fun shared(path: String): String = File(dir, "shared/$path").readText()
}
