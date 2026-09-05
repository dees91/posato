package app.posato.provisioning.core

import kotlinx.serialization.json.Json

object ProvisioningJson {
    val pretty: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    /** App Store Connect returns many attributes this tool does not model, and adds more over time. */
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
}
