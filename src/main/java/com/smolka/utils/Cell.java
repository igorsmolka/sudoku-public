package com.smolka.utils;

import java.util.Objects;

public record Cell(
        Position position,
        Integer number
) {

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Cell cell = (Cell) o;
        return Objects.equals(number, cell.number) && Objects.equals(position, cell.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, number);
    }

    public boolean isEmpty() {
        return number == null;
    }
}
