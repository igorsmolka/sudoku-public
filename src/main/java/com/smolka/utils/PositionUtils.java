package com.smolka.utils;

import java.util.HashSet;
import java.util.Set;

public class PositionUtils {

    public static int getIndexInSquare(Position position, int n) {
        int squareIndex = getSquareIndexByPosition(position, n);

        Position startPos = getStartCoordinatesBySquareIndex(squareIndex, n);

        int fromRow = startPos.row();
        int fromColumn = startPos.column();

        int toRow = fromRow + n;
        int toColumn = fromColumn + n;

        int counter = -1;

        for (int k = 1; k <= n; k++) {
            for (int row = fromRow; row < toRow; row++) {
                for (int column = fromColumn; column < toColumn; column++) {
                    counter++;
                    if (position.equals(new Position(row, column))) {
                        return counter;
                    }
                }
            }
            fromRow++;
        }

        return -1;
    }

    public static Integer getSquareIndexByPosition(Position position, int n) {
        Set<Integer> squaresByRow = squaresInRow(position.row(), n);
        Set<Integer> squaresByColumn = squaresInColumn(position.column(), n);

        squaresByColumn.retainAll(squaresByRow);

        return squaresByColumn.stream().findFirst().orElse(null);
    }

    public static boolean positionInAnySquare(Position position, Set<Integer> squares, int n) {
        for (int square : squares) {
            if (positionInSquare(position, square, n)) {
                return true;
            }
        }

        return false;
    }

    public static Set<Integer> squaresInColumn(Integer columnIndex, int n) {
        Set<Integer> result = new HashSet<>();

        int startSquareIndex = -1;

        for (int counter = 0; counter <= columnIndex; counter++) {
            if (counter % n == 0) {
                startSquareIndex++;
            }
        }

        result.add(startSquareIndex);

        int incrementedSquareIndex = startSquareIndex;
        for (int counter = 1; counter < n; counter++) {
            incrementedSquareIndex += n;
            result.add(incrementedSquareIndex);
        }

        return result;
    }



    public static boolean positionInSquare(Position position, Integer squareIndex, int n) {
        Position left = getStartCoordinatesBySquareIndex(squareIndex, n);
        Position right = new Position(left.row() + n - 1, left.column() + n - 1);

        return position.column() >= left.column() && position.column() <= right.column() && position.row() >= left.row() && position.row() <= right.row();
    }

    public static Set<Integer> squaresInRow(Integer rowIndex, int n) {
        Set<Integer> result = new HashSet<>();

        int startSquareIndex = -n;
        for (int counter = 0; counter <= rowIndex; counter++) {
            if (counter % n == 0) {
                startSquareIndex += n;
            }
        }

        result.add(startSquareIndex);
        int incrementedSquareIndex = startSquareIndex;
        for (int counter = 1; counter < n; counter++) {
            incrementedSquareIndex++;
            result.add(incrementedSquareIndex);
        }

        return result;
    }

    public static Position getStartCoordinatesBySquareIndex(int squareIndex, int n) {
        int sqrN = n * n;
        assert squareIndex >= 0;

        int row = 0;
        int col = -n;
        for (int i = 0; i < sqrN; i++) {
            col += n;
            if (col >= sqrN) {
                col = 0;
                row += n;
            }
            if (i == squareIndex) {
                return new Position(row, col);
            }
        }

        return new Position(-1, -1);
    }
}
