package com.shippopotamus.bloviate.testing

import com.shippopotamus.bloviate.data.ModelFileDownloader
import java.io.File
import java.io.IOException

/**
 * Hand-written fake of [ModelFileDownloader]; writes [bytesToWrite] to the
 * target (or throws [failure]) and records every call.
 */
class FakeModelFileDownloader : ModelFileDownloader {

    var bytesToWrite: ByteArray = ByteArray(0)

    var failure: IOException? = null

    var callCount: Int = 0
        private set

    val writtenTargets: MutableList<File> = mutableListOf()

    override suspend fun downloadTo(
        target: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ) {
        callCount++
        failure?.let { throw it }
        target.parentFile?.mkdirs()
        target.writeBytes(bytesToWrite)
        writtenTargets += target
        onProgress(bytesToWrite.size.toLong(), bytesToWrite.size.toLong())
    }
}
