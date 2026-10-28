package app.posato.windows.helper

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.lang.ref.Reference
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The Windows Filtering Platform block filters of one pause. They live in a dynamic session with Posato's own
 * sublayer, so they vanish when the service stops or crashes; each filter matches one remote address and only the
 * installing user's connections, so system services are never blocked.
 */
internal class WfpFilters(
    private val userSid: String,
) : AutoCloseable {
    private var engine: Pointer? = null
    private var userCondition: UserCondition? = null
    private val filters = LinkedHashMap<InetAddress, Long>()

    val blocked: Set<InetAddress>
        get() = filters.keys.toSet()

    /** Makes the filters exactly [addresses] in one transaction; false keeps the earlier filters. */
    fun replace(addresses: Set<InetAddress>): Boolean {
        if (addresses == filters.keys) return true
        val handle = open() ?: return false
        val api = Fwpuclnt.INSTANCE
        if (api.fwpmTransactionBegin0(handle, 0) != 0) return false
        val removed = filters.keys - addresses
        val added = addresses - filters.keys
        val ids = HashMap<InetAddress, Long>()
        val succeeded = removed.all { api.fwpmFilterDeleteById0(handle, filters.getValue(it)) == 0 } &&
            added.all { address -> add(handle, address)?.also { ids[address] = it } != null }
        if (!succeeded || api.fwpmTransactionCommit0(handle) != 0) {
            api.fwpmTransactionAbort0(handle)
            return false
        }
        removed.forEach(filters::remove)
        filters.putAll(ids)
        return true
    }

    override fun close() {
        engine?.let(Fwpuclnt.INSTANCE::fwpmEngineClose0)
        engine = null
        filters.clear()
        userCondition?.close()
        userCondition = null
    }

    private fun open(): Pointer? {
        return engine ?: openSession()?.also { handle ->
            userCondition = UserCondition(userSid)
            engine = handle
        }
    }

    private fun openSession(): Pointer? {
        val session = FwpmSession0().apply {
            displayData.name = WString(NAME)
            flags = FWPM_SESSION_FLAG_DYNAMIC
        }
        val handle = PointerByReference()
        if (Fwpuclnt.INSTANCE.fwpmEngineOpen0(null, RPC_C_AUTHN_WINNT, null, session, handle) != 0) return null
        val sublayer = FwpmSublayer0().apply {
            subLayerKey = GUID.fromString(SUBLAYER_KEY)
            displayData.name = WString(NAME)
            weight = SUBLAYER_WEIGHT
        }
        if (Fwpuclnt.INSTANCE.fwpmSubLayerAdd0(handle.value, sublayer, null) != 0) {
            Fwpuclnt.INSTANCE.fwpmEngineClose0(handle.value)
            return null
        }
        return handle.value
    }

    private fun add(
        handle: Pointer,
        address: InetAddress,
    ): Long? {
        val user = userCondition ?: return null
        val v4 = address is Inet4Address
        val remote = if (v4) null else Memory(IPV6_BYTES.toLong()).apply { write(0, address.address, 0, IPV6_BYTES) }
        val conditions = FwpmFilterCondition0().toArray(2).map { it as FwpmFilterCondition0 }
        conditions[0].apply {
            fieldKey = GUID.fromString(CONDITION_IP_REMOTE_ADDRESS)
            matchType = FWP_MATCH_EQUAL
            conditionValue.type = if (v4) FWP_UINT32 else FWP_BYTE_ARRAY16_TYPE
            conditionValue.value = if (v4) ipv4HostOrder(address) else Pointer.nativeValue(remote)
        }
        conditions[1].apply {
            fieldKey = GUID.fromString(CONDITION_ALE_USER_ID)
            matchType = FWP_MATCH_EQUAL
            conditionValue.type = FWP_SECURITY_DESCRIPTOR_TYPE
            conditionValue.value = Pointer.nativeValue(user.blob)
        }
        conditions.forEach(Structure::write)
        val filter = FwpmFilter0().apply {
            displayData.name = WString(NAME)
            layerKey = GUID.fromString(if (v4) LAYER_ALE_AUTH_CONNECT_V4 else LAYER_ALE_AUTH_CONNECT_V6)
            subLayerKey = GUID.fromString(SUBLAYER_KEY)
            weight.type = FWP_EMPTY
            numFilterConditions = conditions.size
            filterCondition = conditions[0].pointer
            action.type = FWP_ACTION_BLOCK
        }
        val id = LongByReference()
        val result = Fwpuclnt.INSTANCE.fwpmFilterAdd0(handle, filter, null, id)
        // The native call reads the conditions and the address through raw pointers, so they must outlive it.
        Reference.reachabilityFence(conditions)
        Reference.reachabilityFence(remote)
        return id.value.takeIf { result == 0 }
    }

    private fun ipv4HostOrder(address: InetAddress): Long {
        return address.address.fold(0L) { value, byte -> (value shl BITS_PER_BYTE) or (byte.toLong() and BYTE_MASK) }
    }

    /** `FWPM_CONDITION_ALE_USER_ID` matches when this descriptor grants the connecting token `FWP_ACTRL_MATCH_FILTER`. */
    private class UserCondition(
        sid: String,
    ) : AutoCloseable {
        private val descriptor = SecurityDescriptor("O:SYG:SYD:(A;;CC;;;$sid)")
        val blob: Memory = Memory(BLOB_SIZE).apply {
            setInt(0, descriptor.size)
            setPointer(POINTER_OFFSET, descriptor.address)
        }

        override fun close() {
            descriptor.close()
        }
    }

    private companion object {
        const val NAME = "Posato"
        const val SUBLAYER_KEY = "{6E0C3B4A-6E58-4C1B-9B0E-5D1A2F7C9A31}"
        const val LAYER_ALE_AUTH_CONNECT_V4 = "{C38D57D1-05A7-4C33-904F-7FBCEEE60E82}"
        const val LAYER_ALE_AUTH_CONNECT_V6 = "{4A72393B-319F-44BC-84C3-BA54DCB3B6B4}"
        const val CONDITION_IP_REMOTE_ADDRESS = "{B235AE9A-1D64-49B8-A44C-5FF3D9095045}"
        const val CONDITION_ALE_USER_ID = "{AF043A0A-B34D-4F86-979C-C90371AF6E66}"
        const val FWPM_SESSION_FLAG_DYNAMIC = 1
        const val RPC_C_AUTHN_WINNT = 10
        const val SUBLAYER_WEIGHT: Short = 0x7FFF
        const val FWP_EMPTY = 0
        const val FWP_UINT32 = 3
        const val FWP_BYTE_ARRAY16_TYPE = 11
        const val FWP_SECURITY_DESCRIPTOR_TYPE = 14
        const val FWP_MATCH_EQUAL = 0
        const val FWP_ACTION_BLOCK = 0x1001
        const val IPV6_BYTES = 16
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xFFL
        const val BLOB_SIZE = 16L
        const val POINTER_OFFSET = 8L
    }
}
