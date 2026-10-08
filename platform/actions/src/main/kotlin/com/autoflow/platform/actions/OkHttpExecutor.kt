package com.autoflow.platform.actions

import com.autoflow.core.engine.http.HttpCall
import com.autoflow.core.engine.http.HttpExecutor
import com.autoflow.core.engine.http.HttpResponse
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** [HttpExecutor] backed by OkHttp. Cleartext HTTP is blocked by Android's default network security config. */
class OkHttpExecutor(
    private val client: OkHttpClient = OkHttpClient(),
    private val userAgent: String = "AutoFlow",
) : HttpExecutor {

    override suspend fun execute(call: HttpCall): HttpResponse {
        val body = call.body?.toRequestBody(call.contentType.toMediaTypeOrNull())
        val request = try {
            Request.Builder()
                .url(call.url)
                .apply { call.headers.forEach { (name, value) -> header(name, value) } }
                .header("User-Agent", call.headers["User-Agent"] ?: userAgent)
                .method(call.method.name, body ?: if (call.method.name in METHODS_REQUIRING_BODY) EMPTY_BODY else null)
                .build()
        } catch (e: IllegalArgumentException) {
            throw IOException("Invalid request: ${e.message}", e)
        }
        val httpCall = client.newBuilder()
            .callTimeout(call.timeoutMs, TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)

        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { httpCall.cancel() }
            httpCall.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (!continuation.isCancelled) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { HttpResponse(it.code, readLimited(it)) }
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                        return
                    }
                    continuation.resume(result)
                }
            })
        }
    }

    private fun readLimited(response: Response): String {
        val source = response.body?.source() ?: return ""
        source.request(MAX_BODY_BYTES)
        val buffer = source.buffer
        return buffer.readUtf8(minOf(buffer.size, MAX_BODY_BYTES))
    }

    private companion object {
        const val MAX_BODY_BYTES = 1_048_576L
        val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}
