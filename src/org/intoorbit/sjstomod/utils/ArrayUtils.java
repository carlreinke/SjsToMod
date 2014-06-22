package org.intoorbit.sjstomod.utils;

/**
 *
 * @author mindless
 */
public class ArrayUtils
{
    public static int binarySearchReverse( int[] array, int key )
    {
        if (array.length == 0)
            return -1;
        
        return ArrayUtils.binarySearchReverse(array, 0, array.length - 1, key);
    }

    public static int binarySearchReverse( int[] array, int lower, int upper, int key )
    {
        if (lower > upper)
            throw new IllegalArgumentException("Lower index is greater than upper index.");
        if (lower < 0)
            throw new ArrayIndexOutOfBoundsException("Lower index is out of bounds.");
        if (upper > array.length)
            throw new ArrayIndexOutOfBoundsException("Upper index is out of bounds.");

        while (lower < upper)
        {
            int middle = (lower + upper) >>> 1;
            
            if (array[middle] > key)
                lower = middle + 1;
            else
                upper = middle;
        }
        
        if (lower == upper && array[lower] == key)
            return lower;
        else
            return -1;  // doesn't quite mimic Array.binarySearch
    }
}
