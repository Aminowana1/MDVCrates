package com.mdvcraft.mdvcrates.model;

public record KeyDefinition(String mmoItemsType, String mmoItemsId) {
    public boolean isConfigured() {
        return mmoItemsType != null && !mmoItemsType.isBlank()
                && mmoItemsId != null && !mmoItemsId.isBlank();
    }
}
