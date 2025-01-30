package com.smolka.utils;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CellsVariant {

    private final Set<Cell> cells;

    private UUID batchUUID;

    private final UUID uuid;

    private final SubSegmentType type;

    public CellsVariant(Set<Cell> cells, SubSegmentType type) {
        this.cells = cells;
        this.uuid = UUID.randomUUID();
        this.type = type;
    }

    public UUID getBatchUUID() {
        return batchUUID;
    }

    public Set<Cell> getCells() {
        return cells;
    }

    public UUID getUuid() {
        return uuid;
    }

    public SubSegmentType getType() {
        return type;
    }

    public void setBatchUUID(UUID batchUUID) {
        this.batchUUID = batchUUID;
    }

    public boolean containsPosition(Position position) {
        return cells.stream().anyMatch(c -> Objects.equals(c.position(), position));
    }
}