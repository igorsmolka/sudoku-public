package com.smolka;

import com.smolka.impl.SudokuImpl;
import org.junit.Test;

public class SudokuTest {


    @Test
    public void test_valid() {
        int[][] field = {
                { 8, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 3, 6, 0, 0, 0, 0, 0 },
                { 0, 7, 0, 0, 9, 0, 2, 0, 0 },
                { 0, 5, 0, 0, 0, 7, 0, 0, 0 },
                { 0, 0, 0, 0, 4, 5, 7, 0, 0 },
                { 0, 0, 0, 1, 0, 0, 0, 3, 0 },
                { 0, 0, 1, 0, 0, 0, 0, 6, 8 },
                { 0, 0, 8, 5, 0, 0, 0, 1, 0 },
                { 0, 9, 0, 0, 0, 0, 4, 0, 0 },
        };

        int[][] variantToCheck = {
                { 8, 1, 2, 7, 5, 3, 6, 4, 9 },
                { 9, 4, 3, 6, 8, 2, 1, 7, 5 },
                { 6, 7, 5, 4, 9, 1, 2, 8, 3 },
                { 1, 5, 4, 2, 3, 7, 8, 9, 6 },
                { 3, 6, 9, 8, 4, 5, 7, 2, 1 },
                { 2, 8, 7, 1, 6, 9, 5, 3, 4 },
                { 5, 2, 1, 9, 7, 4, 3, 6, 8 },
                { 4, 3, 8, 5, 2, 6, 9, 1, 7 },
                { 7, 9, 6, 3, 1, 8, 4, 5, 2 }
        };

        Sudoku sudoku = new SudokuImpl(3, field);
        boolean isValid = sudoku.checkVariant(variantToCheck);

        assert isValid;
    }

    @Test
    public void test_invalid() {
        int[][] field = {
                { 8, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 0, 0, 3, 6, 0, 0, 0, 0, 0 },
                { 0, 7, 0, 0, 9, 0, 2, 0, 0 },
                { 0, 5, 0, 0, 0, 7, 0, 0, 0 },
                { 0, 0, 0, 0, 4, 5, 7, 0, 0 },
                { 0, 0, 0, 1, 0, 0, 0, 3, 0 },
                { 0, 0, 1, 0, 0, 0, 0, 6, 8 },
                { 0, 0, 8, 5, 0, 0, 0, 1, 0 },
                { 0, 9, 0, 0, 0, 0, 4, 0, 0 },
        };

        int[][] variantToCheck = {
                { 8, 1, 2, 7, 5, 3, 6, 4, 9 },
                { 9, 1, 3, 6, 8, 2, 1, 7, 5 },
                { 6, 7, 5, 4, 9, 1, 2, 8, 3 },
                { 1, 5, 4, 2, 3, 7, 8, 9, 6 },
                { 3, 6, 9, 8, 4, 5, 7, 2, 1 },
                { 2, 8, 7, 1, 6, 9, 5, 3, 4 },
                { 5, 2, 1, 9, 7, 4, 3, 6, 8 },
                { 4, 3, 8, 5, 2, 6, 9, 1, 7 },
                { 7, 9, 6, 3, 1, 8, 4, 5, 2 }
        };

        Sudoku sudoku = new SudokuImpl(3, field);
        boolean isValid = sudoku.checkVariant(variantToCheck);

        assert !isValid;
    }


    @Test
    public void test_finding2() {
        int[][] field = {
                { 0, 0, 0, 0 },
                { 0, 0, 4, 0 },
                { 0, 0, 0, 0 },
                { 1, 0, 0, 2 }
        };

        Sudoku sudoku = new SudokuImpl(2, field);
        int[][] variant = sudoku.getVariant();

        boolean isValid = sudoku.checkVariant(variant);
        assert  isValid;
    }
}
