package com.franklinharper.social.media.client.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

private val client = HttpClient(CIO) {
    expectSuccess = false
    followRedirects = true
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    defaultRequest {
        header(HttpHeaders.Accept, "application/json, application/xml, text/xml;q=0.9, */*;q=0.8")
    }
}

internal actual object NetworkHttp {
    actual suspend fun get(
        url: String,
        headers: Map<String, String>,
    ): NetworkResponse {
        val response = client.get(url) {
            headers.forEach { (name, value) -> header(name, value) }
        }
        return response.toNetworkResponse()
    }

    actual suspend fun postJson(
        url: String,
        body: String,
        headers: Map<String, String>,
    ): NetworkResponse {
        val response = client.post(url) {
            header(HttpHeaders.ContentType, "application/json")
            headers.forEach { (name, value) -> header(name, value) }
            setBody(body)
        }
        return response.toNetworkResponse()
    }

    actual suspend fun deleteJson(
        url: String,
        body: String?,
        headers: Map<String, String>,
    ): NetworkResponse {
        val response = client.delete(url) {
            header(HttpHeaders.ContentType, "application/json")
            headers.forEach { (name, value) -> header(name, value) }
            body?.let(::setBody)
        }
        return response.toNetworkResponse()
    }
}

internal actual fun networkHttpEngineName(): String = "CIO"

private suspend fun io.ktor.client.statement.HttpResponse.toNetworkResponse(): NetworkResponse =
    NetworkResponse(
        statusCode = status.value,
        body = bodyAsText(),
        headers = headers.entries().associate { (name, values) -> name to values.joinToString(",") },
    )
