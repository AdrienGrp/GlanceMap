package com.glancemap.glancemapcompanionapp.livetracking

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal const val ARKLUZ_CLIENT_TOKEN_HEADER = "X-GlanceMap-Client-Token"

private val arkluzClientTokenHosts = setOf("arkluz.com")

internal class ArkluzClientTokenInterceptor(
    private val token: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val authenticatedRequest = chain.request().withArkluzClientToken(token)
        return chain.proceed(authenticatedRequest)
    }
}

internal fun Request.withArkluzClientToken(
    token: String,
    allowedHosts: Set<String> = arkluzClientTokenHosts,
): Request {
    val requestBuilder = newBuilder().removeHeader(ARKLUZ_CLIENT_TOKEN_HEADER)
    if (token.isNotEmpty() && url.isHttps && url.host.lowercase() in allowedHosts) {
        requestBuilder.header(ARKLUZ_CLIENT_TOKEN_HEADER, token)
    }
    return requestBuilder.build()
}
