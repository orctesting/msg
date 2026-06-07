package org.messenger.app.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.messenger.app.shared.data.model.UpdateInfo
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

actual object PlatformUpdater {

    private val osName = System.getProperty("os.name").lowercase()
    private val isWin = osName.contains("win")
    private val isMac = osName.contains("mac") || osName.contains("darwin")

    actual fun platformId(): String = when {
        isWin -> "desktop_win"
        isMac -> "desktop_mac"
        else -> "desktop_linux"
    }

    actual fun currentVersionCode(): Int {
        val stream = PlatformUpdater::class.java.classLoader
            .getResourceAsStream("version.properties") ?: return 0
        val props = Properties().apply { stream.use { load(it) } }
        return props.getProperty("VERSION_CODE", "0").toIntOrNull() ?: 0
    }

    actual fun downloadAndInstall(info: UpdateInfo): Flow<UpdateProgress> = flow {
        emit(UpdateProgress.Downloading(0, info.fileSizeBytes))

        val updatesDir = File(appDataDir(), "updates").apply { mkdirs() }
        val zipFile = File(updatesDir, "update-${info.versionName}.zip")

        // download
        val conn = URL(info.downloadUrl).openConnection()
        conn.connect()
        val total = if (info.fileSizeBytes > 0) info.fileSizeBytes else conn.contentLengthLong
        conn.getInputStream().use { input ->
            zipFile.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } >= 0) {
                    out.write(buf, 0, read)
                    done += read
                    emit(UpdateProgress.Downloading(done, total))
                }
            }
        }

        // verify
        emit(UpdateProgress.Verifying)
        if (!sha256Of(zipFile).equals(info.sha256, ignoreCase = true)) {
            zipFile.delete()
            throw IllegalStateException("SHA-256 mismatch")
        }

        // unpack
        val staging = File(updatesDir, "staging-${info.versionName}")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        unzip(zipFile, staging)
        zipFile.delete()

        // install + restart
        emit(UpdateProgress.Installing)
        launchBootstrap(staging)
        Thread.sleep(300)
        exitProcess(0)
    }.flowOn(Dispatchers.IO)

    private fun installDir(): File {
        val jar = File(
            PlatformUpdater::class.java.protectionDomain.codeSource.location.toURI()
        )
        // jpackage: <install>/app/<name>.jar → install = parent.parent
        return jar.parentFile?.parentFile ?: File(System.getProperty("user.dir"))
    }

    private fun appDataDir(): File {
        val base = when {
            isWin -> System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home")
            isMac -> "${System.getProperty("user.home")}/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: "${System.getProperty("user.home")}/.local/share"
        }
        return File(base, "Messenger").apply { mkdirs() }
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            var r: Int
            while (ins.read(buf).also { r = it } >= 0) md.update(buf, 0, r)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzip(zip: File, dest: File) {
        val destPath = dest.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(dest, entry.name)
                if (outFile.canonicalPath != destPath &&
                    !outFile.canonicalPath.startsWith(destPath + File.separator)
                ) throw SecurityException("Zip slip: ${entry.name}")
                if (entry.isDirectory) outFile.mkdirs()
                else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun launchBootstrap(staging: File) {
        val install = installDir()
        val pid = ProcessHandle.current().pid()
        val launcher = findLauncher(install)

        val command: List<String>
        if (isWin) {
            val script = File(staging.parentFile, "update.bat")
            script.writeText(
                """
                @echo off
                :wait
                tasklist /FI "PID eq $pid" 2>NUL | find "$pid" >NUL
                if not errorlevel 1 ( timeout /t 1 /nobreak >NUL & goto wait )
                xcopy /E /Y /I "${staging.absolutePath}\*" "${install.absolutePath}\"
                start "" "${launcher?.absolutePath ?: ""}"
                rmdir /S /Q "${staging.absolutePath}"
                del "%~f0"
                """.trimIndent()
            )
            command = listOf("cmd", "/c", "start", "/min", "", script.absolutePath)
        } else {
            val script = File(staging.parentFile, "update.sh")
            script.writeText(
                """
                #!/bin/sh
                while kill -0 $pid 2>/dev/null; do sleep 1; done
                cp -Rf "${staging.absolutePath}/." "${install.absolutePath}/"
                rm -rf "${staging.absolutePath}"
                "${launcher?.absolutePath ?: ""}" &
                rm -- "${'$'}0"
                """.trimIndent()
            )
            script.setExecutable(true)
            command = listOf("/bin/sh", script.absolutePath)
        }

        ProcessBuilder(command)
            .directory(staging.parentFile)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    private fun findLauncher(install: File): File? {
        val candidates = mutableListOf<File>()
        if (isWin) install.listFiles()?.forEach {
            if (it.extension.equals("exe", true)) candidates += it
        }
        File(install, "bin").listFiles()?.forEach { candidates += it }
        return candidates.firstOrNull()
    }
}