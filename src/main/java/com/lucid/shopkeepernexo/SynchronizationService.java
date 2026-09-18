package com.lucid.shopkeepernexo;

import java.util.function.Consumer;

interface SynchronizationService {
    void requestSync(SyncTrigger trigger, Consumer<SyncSnapshot> completion);

    void recordNexoItem();

    void recordUpdatedItem();

    void recordMissingDefinition();

    void recordFailure();

    boolean isQueued();

    boolean isRunning();

    SyncSnapshot lastSnapshot();
}
