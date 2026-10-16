package com.hospital.common.events;

/** Triage priority captured at check-in. Higher weight is served first by priority strategies. */
public enum Priority {
    NORMAL(0),
    URGENT(1),
    CRITICAL(2);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
