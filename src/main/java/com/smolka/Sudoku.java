package com.smolka;

import java.util.List;

public interface Sudoku {

    int[][] getVariant();

    boolean checkVariant(int[][] variant);
}
