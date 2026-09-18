package com.lucid.shopkeepernexo;

enum SyncTrigger {
    STARTUP("startup"),
    NEXO_ITEMS_LOADED("Nexo items loaded"),
    MANUAL("manual command");

    private final String description;

    SyncTrigger(String description) {
        this.description = description;
    }

    String description() {
        return description;
    }
}
