/*
    Copyright (C) 2026 OpenMW-Android contributors

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
*/

package mods

import android.content.Context
import file.GameInstaller
import java.io.File

/** Shared path handling for the launcher mod manager and openmw.cfg writer. */
object ModsPaths {

    /** Root directory that contains the game's Data Files directory. */
    fun gameRoot(ctx: Context): File {
        val dataFiles = GameInstaller.getDataFilesFile(ctx)
        return dataFiles.parentFile ?: File("")
    }

    /** Optional folder next to Data Files that holds additional mod directories. */
    const val MODS_DIRECTORY = "Mods"

    /** True if [dir] is the Mods folder itself (not a mod directory). */
    fun isModsFolder(dir: File): Boolean =
        dir.name.equals(MODS_DIRECTORY, ignoreCase = true) &&
            File(dir.parentFile, "Data Files").isDirectory

    /**
     * Path list used by the Directories tab: the game root (mod directories
     * next to Data Files) and, if present, the Mods folder.
     */
    fun directoryRoots(ctx: Context): ArrayList<String> {
        val root = gameRoot(ctx)
        val result = arrayListOf(root.absolutePath)
        root.listFiles()
            ?.firstOrNull { it.isDirectory && isModsFolder(it) }
            ?.let { result.add(it.absolutePath) }
        return result
    }

    /** Resolves a Directories-tab entry (a bare directory name) against the directory roots. */
    private fun resolveDirectory(roots: List<String>, name: String): File? =
        roots.asSequence().map { File(it, name) }.firstOrNull { it.isDirectory }

    /**
     * Returns the base Data Files directory plus every additional directory
     * next to it or inside the Mods folder. This is used by the mod manager so
     * enable/load-order state is preserved even while an additional data
     * directory is disabled.
     */
    fun allDataDirectories(ctx: Context): ArrayList<String> {
        val dataFiles = GameInstaller.getDataFilesFile(ctx)
        val result = arrayListOf(dataFiles.absolutePath)
        if (dataFiles.parentFile == null) return result

        directoryRoots(ctx).forEach { root ->
            File(root).listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.filterNot { it.absoluteFile == dataFiles.absoluteFile || isModsFolder(it) }
                ?.mapTo(result) { it.absolutePath }
        }

        return result
    }

    /**
     * Returns the base Data Files directory plus only additional data
     * directories that are enabled in the launcher.
     */
    fun activeDataDirectories(
        ctx: Context,
        db: ModsDatabaseOpenHelper = ModsDatabaseOpenHelper.getInstance(ctx)
    ): ArrayList<String> {
        val dataFiles = GameInstaller.getDataFilesFile(ctx)
        val result = arrayListOf(dataFiles.absolutePath)
        if (dataFiles.parentFile == null) return result

        val roots = directoryRoots(ctx)
        val directories = ModsCollection(ModType.Dir, roots, db)

        directories.mods
            .asSequence()
            .filter { it.enabled }
            .mapNotNull { resolveDirectory(roots, it.filename) }
            .mapTo(result) { it.absolutePath }

        return result
    }

    /** Returns true if [filename] is present in at least one active data directory. */
    fun fileExistsIn(filename: String, dataDirectories: Collection<String>): Boolean =
        dataDirectories.any { File(it, filename).isFile }
}
