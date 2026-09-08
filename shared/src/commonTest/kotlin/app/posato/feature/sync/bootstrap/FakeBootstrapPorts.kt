package app.posato.feature.sync.bootstrap

internal val bindingA = checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 1 }))
internal val bindingB = checkNotNull(AccountBinding.fromBytes(ByteArray(ACCOUNT_BINDING_BYTES) { 2 }))

internal class FakeBootstrapAccountPort(
    var default: BindingResolution = BindingResolution.Available(bindingA),
    private val script: ArrayDeque<BindingResolution> = ArrayDeque()
) : BootstrapAccountPort {
    var calls = 0
        private set

    fun scriptResolution(vararg resolutions: BindingResolution) {
        resolutions.forEach { script.addLast(it) }
    }

    override suspend fun resolveBinding(): BindingResolution {
        calls += 1
        return script.removeFirstOrNull() ?: default
    }
}

internal class FakeBootstrapCloudPort(
    var zoneExists: Boolean = false,
    var storedAnchor: WorkspaceAnchor? = null,
    private val zoneFetches: ArrayDeque<ZoneFetchResult> = ArrayDeque(),
    private val zoneSaves: ArrayDeque<ZoneSaveResult> = ArrayDeque(),
    private val anchorReads: ArrayDeque<AnchorReadResult> = ArrayDeque(),
    private val anchorCreates: ArrayDeque<AnchorCreateResult> = ArrayDeque()
) : BootstrapCloudPort {
    var zoneFetchCalls = 0
        private set
    var zoneSaveCalls = 0
        private set
    var anchorReadCalls = 0
        private set
    var anchorCreateCalls = 0
        private set
    val createdAnchors = mutableListOf<WorkspaceAnchor>()

    fun scriptZoneFetch(vararg results: ZoneFetchResult) {
        results.forEach { zoneFetches.addLast(it) }
    }

    fun scriptZoneSave(vararg results: ZoneSaveResult) {
        results.forEach { zoneSaves.addLast(it) }
    }

    fun scriptAnchorRead(vararg results: AnchorReadResult) {
        results.forEach { anchorReads.addLast(it) }
    }

    fun scriptAnchorCreate(vararg results: AnchorCreateResult) {
        results.forEach { anchorCreates.addLast(it) }
    }

    override suspend fun fetchZone(expectedBinding: AccountBinding): ZoneFetchResult {
        zoneFetchCalls += 1
        return zoneFetches.removeFirstOrNull()
            ?: if (zoneExists) ZoneFetchResult.Found else ZoneFetchResult.Missing
    }

    override suspend fun saveZone(expectedBinding: AccountBinding): ZoneSaveResult {
        zoneSaveCalls += 1
        return zoneSaves.removeFirstOrNull() ?: if (zoneExists) {
            ZoneSaveResult.AlreadyExists
        } else {
            zoneExists = true
            ZoneSaveResult.Created
        }
    }

    override suspend fun readAnchor(expectedBinding: AccountBinding): AnchorReadResult {
        anchorReadCalls += 1
        return anchorReads.removeFirstOrNull()
            ?: storedAnchor?.let { AnchorReadResult.Found(it) } ?: AnchorReadResult.Missing
    }

    override suspend fun createAnchor(
        expectedBinding: AccountBinding,
        anchor: WorkspaceAnchor
    ): AnchorCreateResult {
        anchorCreateCalls += 1
        createdAnchors.add(anchor)
        val scripted = anchorCreates.removeFirstOrNull()
        if (scripted != null) {
            if (scripted is AnchorCreateResult.Created) {
                storedAnchor = anchor
            }
            return scripted
        }
        val existing = storedAnchor
        if (existing == null) {
            storedAnchor = anchor
            return AnchorCreateResult.Created
        }
        return AnchorCreateResult.Conflict
    }
}

internal class FakeBootstrapKeyPort(
    val items: MutableMap<String, ByteArray> = mutableMapOf(),
    private val reads: ArrayDeque<KeyItemReadResult> = ArrayDeque(),
    private val creates: ArrayDeque<KeyItemCreateResult> = ArrayDeque(),
    private val deletes: ArrayDeque<KeyItemDeleteResult> = ArrayDeque()
) : BootstrapKeyPort {
    var readCalls = 0
        private set
    var createCalls = 0
        private set
    var deleteCalls = 0
        private set

    fun scriptRead(vararg results: KeyItemReadResult) {
        results.forEach { reads.addLast(it) }
    }

    fun scriptCreate(vararg results: KeyItemCreateResult) {
        results.forEach { creates.addLast(it) }
    }

    fun scriptDelete(vararg results: KeyItemDeleteResult) {
        results.forEach { deletes.addLast(it) }
    }

    override suspend fun readItem(
        expectedBinding: AccountBinding,
        account: KeyAccount
    ): KeyItemReadResult {
        readCalls += 1
        reads.removeFirstOrNull()?.let { return it }
        val bytes = items[account.text] ?: return KeyItemReadResult.Missing
        return WorkspaceKeyItem.fromBytes(bytes)?.let { KeyItemReadResult.Found(it) }
            ?: KeyItemReadResult.IntegrityFailure
    }

    override suspend fun createItem(
        expectedBinding: AccountBinding,
        account: KeyAccount,
        value: WorkspaceKeyItem
    ): KeyItemCreateResult {
        createCalls += 1
        creates.removeFirstOrNull()?.let { return it }
        if (items.containsKey(account.text)) {
            return KeyItemCreateResult.AlreadyExists
        }
        items[account.text] = value.copyBytes()
        return KeyItemCreateResult.Created
    }

    override suspend fun deleteItemAndVerifyAbsent(
        expectedBinding: AccountBinding,
        account: KeyAccount
    ): KeyItemDeleteResult {
        deleteCalls += 1
        deletes.removeFirstOrNull()?.let { return it }
        items.remove(account.text)
        return if (items.containsKey(account.text)) {
            KeyItemDeleteResult.Retryable
        } else {
            KeyItemDeleteResult.DeletedAndAbsent
        }
    }
}

internal class FakeBootstrapStore(
    var state: BootstrapState = BootstrapState.None,
    var readFailure: BootstrapStoreFailure? = null,
    var writeFailure: BootstrapStoreFailure? = null
) : BootstrapStore {
    var persistCalls = 0
        private set
    var commitCalls = 0
        private set

    override suspend fun clearEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit> {
        val failure = writeFailure
        if (failure != null) return BootstrapStoreResult.Failure(failure)
        if (state != BootstrapState.Established(workspace)) return BootstrapStoreResult.Failure(BootstrapStoreFailure.CORRUPTION)
        state = BootstrapState.None
        return BootstrapStoreResult.Success(Unit)
    }

    override suspend fun read(): BootstrapStoreResult<BootstrapState> {
        val failure = readFailure
        return if (failure == null) {
            BootstrapStoreResult.Success(state)
        } else {
            BootstrapStoreResult.Failure(failure)
        }
    }

    override suspend fun persistCandidate(candidate: PersistedCandidate): BootstrapStoreResult<Unit> {
        persistCalls += 1
        val failure = writeFailure
        return if (failure == null) {
            state = BootstrapState.Candidate(candidate)
            BootstrapStoreResult.Success(Unit)
        } else {
            BootstrapStoreResult.Failure(failure)
        }
    }

    override suspend fun commitEstablished(workspace: EstablishedWorkspace): BootstrapStoreResult<Unit> {
        commitCalls += 1
        val failure = writeFailure
        return if (failure == null) {
            state = BootstrapState.Established(workspace)
            BootstrapStoreResult.Success(Unit)
        } else {
            BootstrapStoreResult.Failure(failure)
        }
    }
}
