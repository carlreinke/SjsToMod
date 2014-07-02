/* 
 * Copyright (c) 2014 Carl Reinke
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.intoorbit.sjstomod.utils;

/**
 *
 * @author mindless
 */
public class StringUtils
{
    private StringUtils()
    {
    }
    
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
