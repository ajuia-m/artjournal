package com.ajuia.artjournal.data.remote

import com.squareup.moshi.JsonDataException
import java.io.IOException
import retrofit2.HttpException

sealed class RemoteFailure(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    class InvalidCredentials(cause: Throwable? = null) :
        RemoteFailure("Неверный логин или пароль.", cause)

    class SessionExpired(cause: Throwable? = null) :
        RemoteFailure("Сессия истекла. Войдите снова.", cause)

    class AccessDenied(cause: Throwable? = null) :
        RemoteFailure("У вашей учётной записи нет доступа к этому ресурсу.", cause)

    class Connectivity(cause: Throwable) :
        RemoteFailure("Не удалось связаться с сервером. Проверьте сеть и адрес backend.", cause)

    class InvalidResponse(cause: Throwable) :
        RemoteFailure("Сервер вернул ответ в неподдерживаемом формате.", cause)

    class Http(val statusCode: Int, cause: Throwable? = null) :
        RemoteFailure("Сервер вернул ошибку HTTP $statusCode.", cause)

    class Unexpected(cause: Throwable) :
        RemoteFailure("Произошла непредвиденная ошибка.", cause)
}

internal fun Throwable.toRemoteFailure(loginRequest: Boolean = false): RemoteFailure = when (this) {
    is RemoteFailure -> this
    is HttpException -> when (code()) {
        401 -> if (loginRequest) {
            RemoteFailure.InvalidCredentials(this)
        } else {
            RemoteFailure.SessionExpired(this)
        }
        403 -> RemoteFailure.AccessDenied(this)
        else -> RemoteFailure.Http(code(), this)
    }
    is IOException -> RemoteFailure.Connectivity(this)
    is JsonDataException -> RemoteFailure.InvalidResponse(this)
    else -> RemoteFailure.Unexpected(this)
}
