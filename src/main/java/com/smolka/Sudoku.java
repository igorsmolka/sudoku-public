package com.smolka;

public interface Sudoku {

    int[][] getVariant();

    boolean checkVariant(int[][] variant);
}
