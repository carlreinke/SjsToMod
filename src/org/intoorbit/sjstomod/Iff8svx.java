/* 
 * Copyright (c) 2014 Carl Reinke
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.intoorbit.sjstomod;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

/**
 *
 * @author mindless
 */
public class Iff8svx
{
    private static final int fccForm = 0x464f524d;  // FORM
    
    private static final int fcc8svx = 0x38535658;  // 8SVX
    
    private static final int fccVhdr = 0x56484452;  // VHDR
    
    private static final int fccName = 0x4E414D45;  // NAME
    
    private static final int fccAnno = 0x414E4E4F;  // ANNO
    
    private static final int fccBody = 0x424F4459;  // BODY
    
    public static Iff8svx loadForm( InputStream inStream ) throws IOException
    {
        DataInputStream in = new DataInputStream(inStream);
        
        if (in.readInt() != fccForm)
            throw new IllegalStateException();
        
        int size = in.readInt();
        
        if (size < 0)
            throw new IllegalStateException();
        
        byte[] formBytes = new byte[size];
        
        in.readFully(formBytes);
        
        ByteArrayInputStream formStream = new ByteArrayInputStream(formBytes);
        
        return load8svx(new DataInputStream(formStream));
    }

    private static final Charset charset = Charset.forName("ISO-8859-1");
            
    private static Iff8svx load8svx( DataInputStream in ) throws IOException
    {
        if (in.readInt() != fcc8svx)
            throw new IllegalStateException();
        
        Iff8svx sample = new Iff8svx();
        
        while (in.available() > 0)
        {
            int propertyFcc = in.readInt();
            
            int propertySize = in.readInt();
            
            if (propertySize < 0)
                throw new IllegalStateException();
            
            switch (propertyFcc)
            {
                case fccVhdr:
                    if (propertySize != 20)
                        throw new IllegalStateException();
                    
                    sample.oneShotHighOctaveSamples = in.readInt();
                    sample.repeatHighOctaveSamples = in.readInt();
                    sample.samplesPerHighOctaveCycle = in.readInt();
                    sample.samplesPerSecond = in.readUnsignedShort();
                    sample.octaveCount = in.readUnsignedByte();
                    sample.compressionMethod = in.readUnsignedByte();
                    sample.volume = in.readInt();
                    
                    if (sample.oneShotHighOctaveSamples < 0)
                        throw new IllegalStateException();
                    if (sample.repeatHighOctaveSamples < 0)
                        throw new IllegalStateException();
                    if (sample.samplesPerHighOctaveCycle != 0 && sample.samplesPerHighOctaveCycle != 32)
                        throw new IllegalStateException();
                    if (sample.octaveCount != 1)
                        throw new IllegalStateException();
                    if (sample.compressionMethod != 0)
                        throw new IllegalStateException();
                    if (sample.volume < 0 || sample.volume > 0x10000)
                        throw new IllegalStateException();
                    break;

                case fccName:
                    byte[] name = new byte[propertySize];
                    
                    in.readFully(name);
                    
                    sample.name = new String(name, charset);
                    break;
                    
                case fccAnno:
                    // don't care
                    in.skipBytes(propertySize);
                    break;

                case fccBody:
                    byte[] body = new byte[propertySize];
                    
                    in.readFully(body);
                    
                    sample.body = body;
                    break;

                default:
                    System.err.printf("skipped %c%c%c%c in sample\n", propertyFcc >> 24 & 0xff, propertyFcc >> 16 & 0xff, propertyFcc >> 8 & 0xff, propertyFcc & 0xff);
                    
                    in.skipBytes(propertySize);
            }
        }
        
        return sample;
    }
    
    private int oneShotHighOctaveSamples;
    private int repeatHighOctaveSamples;
    private int samplesPerHighOctaveCycle;
    private int samplesPerSecond;
    private int octaveCount;
    private int compressionMethod;
    private int volume;
    
    private String name;
    
    private byte[] body = new byte[0];
    
    // TODO: Protracker requires sample offset/lengths to be multiples of 2 bytes
    public ProtrackerModule.Sample toProtracker()
    {
        ProtrackerModule.Sample sample = new ProtrackerModule.Sample();
        
        if (repeatHighOctaveSamples > 0)
        {
            sample.setRepeatOffset(oneShotHighOctaveSamples);
            sample.setRepeatLength(repeatHighOctaveSamples);
        }
        
        sample.setVolume(volume >> 10);
        
        if (name != null)
            sample.setName(name);
        
        byte[] body;
        int highOctaveSamples = oneShotHighOctaveSamples + repeatHighOctaveSamples;
        body = Arrays.copyOf(this.body, highOctaveSamples);
        
        sample.setSample(body);
        
        return sample;
    }
}
