package com.example.ilyphoto.data

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Custom Azure cloud backend exceptions. */
class AzureNotConfiguredException(
    message: String = "Azure backend is not configured yet. Configure AzureConfig.API_BASE_URL."
) : IllegalStateException(message)

class AzureNetworkException(
    message: String = "Unable to connect to Azure cloud service. Please check your internet connection.",
    cause: Throwable? = null
) : IOException(message, cause)

class AzureAuthException(
    message: String = "Authentication failed. Please verify your credentials.",
    cause: Throwable? = null
) : Exception(message, cause)

class AzureApiException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/** Turns Azure / system exceptions into user-friendly messages. */
fun Throwable.toUserMessage(): String = when (this) {
    is AzureNotConfiguredException -> message ?: "Azure is not configured."
    is AzureAuthException -> message ?: "Incorrect email or password."
    is AzureNetworkException, is UnknownHostException, is ConnectException ->
        "Cannot reach Azure cloud server. Check your network connection."
    is SocketTimeoutException ->
        "Azure server timed out. Please try again."
    is AzureApiException -> when (statusCode) {
        400 -> message.ifBlank { "Invalid input or parameters." }
        401 -> "Incorrect email or password, or session expired. Please sign in again."
        403 -> "You don't have permission to perform this action."
        404 -> "The requested record was not found on Azure."
        409 -> message.ifBlank { "A conflict occurred (such as a conflicting booking date or duplicate email)." }
        else -> message.ifBlank { "Azure server error ($statusCode). Please try again." }
    }
    else -> message?.takeIf { it.isNotBlank() } ?: "An unexpected error occurred. Please try again."
}
