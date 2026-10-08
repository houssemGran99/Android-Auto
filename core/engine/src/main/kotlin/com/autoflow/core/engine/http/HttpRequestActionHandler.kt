package com.autoflow.core.engine.http

import com.autoflow.core.engine.AutomationContext
import com.autoflow.core.engine.action.ActionHandler
import com.autoflow.core.model.ActionResult
import com.autoflow.core.model.ActionSpec
import com.autoflow.core.model.FailureKind
import com.autoflow.core.model.HttpAuth
import com.autoflow.core.model.HttpMethod
import com.autoflow.core.model.VariableScope
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.util.Base64

data class HttpCall(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
    val contentType: String,
    val timeoutMs: Long,
)

data class HttpResponse(val code: Int, val body: String)

/** Network transport abstraction (OkHttp on Android, fakes in tests). Throws [IOException] on network errors. */
fun interface HttpExecutor {
    suspend fun execute(call: HttpCall): HttpResponse
}

/**
 * Performs an HTTP request with placeholder substitution in URL, query, headers and body.
 * The response body is stored in the local variable `<responseVariable>` (JSON paths can be
 * read with `$http.data.field`) and the status code in `<responseVariable>_status`.
 */
class HttpRequestActionHandler(private val executor: HttpExecutor) : ActionHandler<ActionSpec.HttpRequest> {

    override suspend fun execute(action: ActionSpec.HttpRequest, context: AutomationContext): ActionResult {
        val call = try {
            buildCall(action, context::resolve)
        } catch (e: IllegalArgumentException) {
            return ActionResult.Failure(FailureKind.INVALID_CONFIGURATION, e.message ?: "Invalid request")
        }
        val response = try {
            executor.execute(call)
        } catch (e: IOException) {
            return ActionResult.Failure(FailureKind.ERROR, "${call.method} ${call.url}: ${e.message ?: e::class.simpleName}")
        }
        val variable = action.responseVariable.ifBlank { DEFAULT_VARIABLE }
        context.setVariable(variable, response.body, VariableScope.LOCAL)
        context.setVariable("${variable}_status", response.code.toString(), VariableScope.LOCAL)
        return if (response.code in 200..299) {
            ActionResult.Success("HTTP ${response.code}")
        } else {
            ActionResult.Failure(FailureKind.ERROR, "HTTP ${response.code}")
        }
    }

    companion object {
        const val DEFAULT_VARIABLE = "http"
        private val BODY_METHODS = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)

        fun buildCall(action: ActionSpec.HttpRequest, resolve: (String) -> String): HttpCall {
            val baseUrl = resolve(action.url).trim()
            val scheme = runCatching { URI(baseUrl).scheme?.lowercase() }.getOrNull()
            require(scheme == "http" || scheme == "https") { "URL must start with http:// or https://" }
            require(action.timeoutSeconds in 1..300) { "Timeout must be between 1 and 300 seconds" }

            val query = action.queryParameters.entries.joinToString("&") { (key, value) ->
                encode(resolve(key)) + "=" + encode(resolve(value))
            }
            val url = when {
                query.isEmpty() -> baseUrl
                baseUrl.contains('?') -> "$baseUrl&$query"
                else -> "$baseUrl?$query"
            }

            val headers = action.headers.entries.associate { (k, v) -> resolve(k).trim() to resolve(v) }.toMutableMap()
            when (val auth = action.auth) {
                is HttpAuth.Basic -> {
                    val credentials = "${resolve(auth.username)}:${resolve(auth.password)}"
                    headers["Authorization"] = "Basic " + Base64.getEncoder().encodeToString(credentials.toByteArray())
                }
                is HttpAuth.Bearer -> headers["Authorization"] = "Bearer " + resolve(auth.token)
                null -> Unit
            }

            val body = if (action.method in BODY_METHODS) action.body?.let(resolve) else null
            return HttpCall(action.method, url, headers, body, action.contentType, action.timeoutSeconds * 1000L)
        }

        private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
    }
}
