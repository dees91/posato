package app.posato.windows.helper

import com.sun.jna.FunctionMapper
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary

/** The Windows Filtering Platform management calls the service uses, from `fwpuclnt.dll`. */
internal interface Fwpuclnt : StdCallLibrary {
    fun fwpmEngineOpen0(
        serverName: WString?,
        authnService: Int,
        authIdentity: Pointer?,
        session: FwpmSession0,
        engine: PointerByReference,
    ): Int

    fun fwpmEngineClose0(engine: Pointer): Int

    fun fwpmTransactionBegin0(
        engine: Pointer,
        flags: Int,
    ): Int

    fun fwpmTransactionCommit0(engine: Pointer): Int

    fun fwpmTransactionAbort0(engine: Pointer): Int

    fun fwpmSubLayerAdd0(
        engine: Pointer,
        subLayer: FwpmSublayer0,
        descriptor: Pointer?,
    ): Int

    fun fwpmFilterAdd0(
        engine: Pointer,
        filter: FwpmFilter0,
        descriptor: Pointer?,
        id: LongByReference,
    ): Int

    fun fwpmFilterDeleteById0(
        engine: Pointer,
        id: Long,
    ): Int

    companion object {
        val INSTANCE: Fwpuclnt by lazy {
            val mapper = FunctionMapper { _, method -> method.name.replaceFirstChar(Char::uppercaseChar) }
            Native.load("fwpuclnt", Fwpuclnt::class.java, mapOf(Library.OPTION_FUNCTION_MAPPER to mapper))
        }
    }
}

// The structures below follow fwpmtypes.h and fwptypes.h on 64-bit Windows (x64 and arm64). An anonymous union of
// eight bytes is one Long; the filter's context union of a UINT64 and a GUID is two Longs.

@Structure.FieldOrder("name", "description")
internal class FwpmDisplayData0 : Structure() {
    @JvmField var name: WString? = null

    @JvmField var description: WString? = null
}

@Structure.FieldOrder("size", "data")
internal class FwpByteBlob : Structure() {
    @JvmField var size: Int = 0

    @JvmField var data: Pointer? = null
}

@Structure.FieldOrder("type", "value")
internal class FwpValue0 : Structure() {
    @JvmField var type: Int = 0

    @JvmField var value: Long = 0
}

@Structure.FieldOrder("sessionKey", "displayData", "flags", "txnWaitTimeoutInMSec", "processId", "sid", "username", "kernelMode")
internal class FwpmSession0 : Structure() {
    @JvmField var sessionKey: GUID = GUID()

    @JvmField var displayData: FwpmDisplayData0 = FwpmDisplayData0()

    @JvmField var flags: Int = 0

    @JvmField var txnWaitTimeoutInMSec: Int = 0

    @JvmField var processId: Int = 0

    @JvmField var sid: Pointer? = null

    @JvmField var username: Pointer? = null

    @JvmField var kernelMode: Int = 0
}

@Structure.FieldOrder("subLayerKey", "displayData", "flags", "providerKey", "providerData", "weight")
internal class FwpmSublayer0 : Structure() {
    @JvmField var subLayerKey: GUID = GUID()

    @JvmField var displayData: FwpmDisplayData0 = FwpmDisplayData0()

    @JvmField var flags: Int = 0

    @JvmField var providerKey: Pointer? = null

    @JvmField var providerData: FwpByteBlob = FwpByteBlob()

    @JvmField var weight: Short = 0
}

@Structure.FieldOrder("fieldKey", "matchType", "conditionValue")
internal class FwpmFilterCondition0 : Structure() {
    @JvmField var fieldKey: GUID = GUID()

    @JvmField var matchType: Int = 0

    @JvmField var conditionValue: FwpValue0 = FwpValue0()
}

@Structure.FieldOrder("type", "filterType")
internal class FwpmAction0 : Structure() {
    @JvmField var type: Int = 0

    @JvmField var filterType: GUID = GUID()
}

@Structure.FieldOrder(
    "filterKey",
    "displayData",
    "flags",
    "providerKey",
    "providerData",
    "layerKey",
    "subLayerKey",
    "weight",
    "numFilterConditions",
    "filterCondition",
    "action",
    "rawContext",
    "providerContextHigh",
    "reserved",
    "filterId",
    "effectiveWeight",
)
internal class FwpmFilter0 : Structure() {
    @JvmField var filterKey: GUID = GUID()

    @JvmField var displayData: FwpmDisplayData0 = FwpmDisplayData0()

    @JvmField var flags: Int = 0

    @JvmField var providerKey: Pointer? = null

    @JvmField var providerData: FwpByteBlob = FwpByteBlob()

    @JvmField var layerKey: GUID = GUID()

    @JvmField var subLayerKey: GUID = GUID()

    @JvmField var weight: FwpValue0 = FwpValue0()

    @JvmField var numFilterConditions: Int = 0

    @JvmField var filterCondition: Pointer? = null

    @JvmField var action: FwpmAction0 = FwpmAction0()

    @JvmField var rawContext: Long = 0

    @JvmField var providerContextHigh: Long = 0

    @JvmField var reserved: Pointer? = null

    @JvmField var filterId: Long = 0

    @JvmField var effectiveWeight: FwpValue0 = FwpValue0()
}
