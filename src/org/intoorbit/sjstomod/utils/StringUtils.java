package org.intoorbit.sjstomod.utils;

/**
 *
 * @author mindless
 */
public class StringUtils
{
    public static String leftPad( String string, int n, char c )
    {
        int count = n - string.length();
        if (count <= 0)
            return string;
        
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; ++i)
            builder.append(c);
        builder.append(string);
        
        return builder.toString();
    }
    
    public static String reverse( String string )
    {
        return new StringBuilder(string).reverse().toString();
    }
}
