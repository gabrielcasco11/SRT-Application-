package com.gabriel.srtmonitor.srt

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.math.min

/** Feeds the bytes received by [SrtReceiver] to Media3's MPEG-TS extractor. */
@UnstableApi
class SrtDataSource(private val receiver: SrtReceiver) : BaseDataSource(/* isNetwork = */ true) {

    private var uri: Uri? = null
    private var opened = false
    private var chunk: ByteArray? = null
    private var chunkPos = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        if (!receiver.isConnected) throw IOException("SRT not connected")
        uri = dataSpec.uri
        opened = true
        transferStarted(dataSpec)
        return C.LENGTH_UNSET.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var current = chunk
        if (current == null || chunkPos >= current.size) {
            current = null
            while (current == null) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException()
                if (!receiver.isConnected) throw IOException("SRT connection lost")
                current = try {
                    receiver.poll(200)
                } catch (e: InterruptedException) {
                    throw InterruptedIOException()
                }
            }
            chunk = current
            chunkPos = 0
        }
        val n = min(length, current.size - chunkPos)
        System.arraycopy(current, chunkPos, buffer, offset, n)
        chunkPos += n
        bytesTransferred(n)
        return n
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        chunk = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }
}
