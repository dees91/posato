package app.posato.control.core

import kotlinx.serialization.json.Json

object ControlJson {
    val pretty: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    val compact: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
