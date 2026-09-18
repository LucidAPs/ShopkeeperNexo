package com.lucid.shopkeepernexo;

import java.time.Instant;

record SyncSnapshot(
        SyncTrigger trigger,
        Instant completedAt,
        long durationMillis,
        int nexoItems,
        int updatedNexoItems,
        int missingDefinitions,
        int failures,
        int shopkeepersReportedUpdates,
        boolean successful,
        String failureMessage
) {
    String summary() {
        if (!successful) {
            return "Synchronization failed: " + failureMessage;
        }

        return "Synchronization complete: "
                + updatedNexoItems + " Nexo item(s) updated, "
                + nexoItems + " Nexo item(s) inspected, "
                + missingDefinitions + " missing definition(s), "
                + failures + " item failure(s), "
                + durationMillis + " ms.";
    }
}
