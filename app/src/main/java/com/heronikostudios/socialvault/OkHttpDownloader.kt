package com.heronikostudios.socialvault

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

class OkHttpDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : Downloader() {

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val okRequestBuilder = okhttp3.Request.Builder().url(url)

        headers?.forEach { (headerName, headerValues) ->
            headerValues.forEach { value ->
                okRequestBuilder.addHeader(headerName, value)
            }
        }

        val contentType = headers?.get("Content-Type")?.firstOrNull() ?: "application/json"
        val requestBody = dataToSend?.toRequestBody(contentType.toMediaTypeOrNull())

        when (httpMethod.uppercase()) {
            "GET" -> okRequestBuilder.get()
            "HEAD" -> okRequestBuilder.head()
            "POST" -> okRequestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(null))
            "PUT" -> okRequestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> okRequestBuilder.delete(requestBody)
            else -> okRequestBuilder.method(httpMethod, requestBody)
        }

        val okResponse = client.newCall(okRequestBuilder.build()).execute()
        val responseBodyString = okResponse.body?.string() ?: ""
        val latestUrl = okResponse.request.url.toString()

        return Response(
            okResponse.code,
            okResponse.message,
            okResponse.headers.toMultimap(),
            responseBodyString,
            latestUrl
        )
    }
}
