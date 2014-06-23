package org.intoorbit.sjstomod;

import org.intoorbit.sjstomod.utils.ArrayUtils;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

/**
 *
 * @author mindless
 */
public class ProtrackerModule
{
    private static final int[] periodTable = new int[]
        {
        1712, 1616, 1524, 1440, 1356, 1280, 1208, 1140, 1076, 1016, 960, 907,
        856, 808, 762, 720, 678, 640, 604, 570, 538, 508, 480, 453,
        428, 404, 381, 360, 339, 320, 302, 285, 269, 254, 240, 226,
        214, 202, 190, 180, 170, 160, 151, 143, 135, 127, 120, 113,
        107, 101, 95, 90, 85, 80, 75, 71, 67, 63, 60, 56,
        };

    public static int getPeriod( int i )
    {
        return periodTable[i];
    }
    
    private String title = "";
    
    public final Sample[] samples = new Sample[31];
    
    public final ArrayList<Integer> patternTable = new ArrayList<>();

    private int repeatIndex;

    public final ArrayList<Pattern> patterns = new ArrayList<>();

    public String getTitle()
    {
        return title;
    }

    public void setTitle( String title )
    {
        if (title == null)
            throw new IllegalArgumentException("title is null");

        if (title.length() > 20)
            title = title.substring(0, 20);

        this.title = title;
    }

    public int getRepeatIndex()
    {
        return repeatIndex;
    }

    public void setRepeatIndex( int repeatIndex )
    {
        if (repeatIndex < 0 || repeatIndex > 127)
            throw new IllegalArgumentException("repeatIndex is out-of-range");
        
        this.repeatIndex = repeatIndex;
    }
    
    public void dump()
    {
        String[] noteNames = new String[] { "C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-" };
        
        for (Pattern pattern : patterns)
        {
            for (Note[] row : pattern.notes)
            {
                for (Note note : row)
                {
                    if (note == null)
                    {
                        System.out.print(" | ... .. .. ...");
                        continue;
                    }

                    String noteNotation;
                    if (note.period == 0)
                    {
                        noteNotation = "...";
                    }
                    else
                    {
                        int noteIndex = ArrayUtils.binarySearchReverse(periodTable, note.period);
                        int octave = noteIndex / 12;
                        noteNotation = String.format("%s%d", noteNames[noteIndex - octave * 12], octave + 3);
                    }

                    String sampleNotation = note.sample == 0 ?
                            ".." :
                            String.format("%02d", note.sample);

                    String effectNotation = note.effect == 0 ?
                            "..." :
                            String.format("%03X", note.effect);
                
                    System.out.printf(" | %s %s .. %s", noteNotation, sampleNotation, effectNotation);
                }
            
                System.out.println();
            }
        }
    }

    public void save( OutputStream outStream ) throws IOException
    {
        validate();
        
        DataOutputStream out = new DataOutputStream(outStream);
        
        out.writeBytes(title);
        for (int i = title.length(); i < 20; ++i)
            out.writeByte(0);
        
        for (int s = 0; s < 31; ++s)
        {
            Sample sample = samples[s];
            
            if (sample == null)
                sample = Sample.empty;
            
            out.writeBytes(sample.name);
            for (int i = sample.name.length(); i < 22; ++i)
                out.writeByte(0);
            
            out.writeShort(sample.sample.length / 2);
            out.writeByte(sample.fineTune);
            out.writeByte(sample.volume);
            out.writeShort(sample.repeatOffset / 2);
            out.writeShort(sample.repeatLength / 2);
        }
        
        out.writeByte(patternTable.size());
        out.writeByte(repeatIndex);
        
        for (int patternIndex : patternTable)
            out.writeByte(patternIndex);
        for (int i = patternTable.size(); i < 128; ++i)
            out.writeByte(0);
        
        out.writeBytes("M.K.");
        
        for (Pattern pattern : patterns)
        {
            for (Note[] row : pattern.notes)
            {
                for (Note note : row)
                {
                    if (note == null)
                        note = Note.empty;
                    
                    out.writeByte((note.sample & 0xf0) | (note.period >> 8));
                    out.writeByte(note.period);
                    out.writeByte((note.sample << 4) | (note.effect >> 8));
                    out.writeByte(note.effect);
                }
            }
        }
        
        for (Sample sample : samples)
        {
            if (sample != null)
            {
                out.write(sample.sample);
            }
        }
    }

    private void validate()
    {
        for (Sample sample : samples)
            if (sample != null)
                sample.validate();

        for (Integer patternIndex : patternTable)
        {
            if (patternIndex == null)
                throw new IllegalStateException("patternIndex is null");
            
            if (patternIndex < 0 || patternIndex >= patterns.size())
                throw new IllegalStateException("patternIndex is out-of-range");
        }
        
        for (Pattern pattern : patterns)
        {
            if (pattern == null)
                throw new IllegalStateException("pattern is null");
            
            pattern.validate();
        }
    }

    public static class Sample
    {
        private static final Sample empty = new Sample();
        
        private String name = "";
        private byte[] sample = new byte[0];
        private int fineTune;
        private int volume;
        private int repeatOffset;
        private int repeatLength;

        public String getName()
        {
            return name;
        }
        
        public void setName( String name )
        {
            if (name == null)
                throw new IllegalArgumentException("name is null");

            if (name.length() > 22)
                name = name.substring(0, 22);

            this.name = name;
        }

        public byte[] getSample()
        {
            return sample;
        }
        
        public void setSample( byte[] sample )
        {
            if (sample == null)
                throw new IllegalArgumentException("sample is null");
            
            this.sample = sample;
        }

        public int getFineTune()
        {
            return fineTune;
        }

        public void setFineTune( int fineTune )
        {
            if (fineTune < -8 || fineTune > 7)
                throw new IllegalArgumentException("fineTune is out-of-range");
            
            this.fineTune = fineTune & 0x0f;
        }

        public int getVolume()
        {
            return volume;
        }

        public void setVolume( int volume )
        {
            if (volume < 0 || volume > 64)
                throw new IllegalArgumentException("volume is out-of-range");
            
            this.volume = volume;
        }

        public int getRepeatOffset()
        {
            return repeatOffset;
        }

        public void setRepeatOffset( int repeatOffset )
        {
            if (repeatOffset < 0 || repeatOffset > (1 << 17))
                throw new IllegalArgumentException("repeatOffset is out-of-range");
            if (repeatOffset % 2 != 0)
                throw new IllegalArgumentException("repeatOffset is invalid");
            
            this.repeatOffset = repeatOffset;
        }

        public int getRepeatLength()
        {
            return repeatLength;
        }

        public void setRepeatLength( int repeatLength )
        {
            if (repeatLength < 0 || repeatLength > (1 << 17))
                throw new IllegalArgumentException("repeatLength is out-of-range");
            if (repeatLength % 2 != 0)
                throw new IllegalArgumentException("repeatLength is invalid");
            
            this.repeatLength = repeatLength;
        }
        
        private void validate()
        {
            if (repeatOffset > sample.length)
                throw new IllegalStateException("repeatOffset is out-of-range");
            
            if (repeatOffset + repeatLength > sample.length)
                throw new IllegalStateException("repeatLength is out-of-range");
        }
    }

    public static class Note
    {
        private static final Note empty = new Note();
                
        private int period;
        private int sample;
        private int effect;

        @Override
        public boolean equals( Object o )
        {
            if (o instanceof Note)
            {
                Note other = (Note)o;
                return period == other.period &&
                        sample == other.sample &&
                        effect == other.effect;
            }
            
            return false;
        }

        public int getPeriod()
        {
            return period;
        }

        public void setPeriod( int period )
        {            
            if (period != 0 && ArrayUtils.binarySearchReverse(periodTable, period) < 0)
                throw new IllegalArgumentException("period is invalid");
            
            this.period = period;
        }

        public int getSample()
        {
            return sample;
        }

        public void setSample( int sample )
        {
            if (sample < 0 || sample > 31)
                throw new IllegalArgumentException("sample is out-of-range");
            
            this.sample = sample;
        }

        public int getEffect()
        {
            return effect;
        }

        public void setEffect( int effect )
        {
            if (effect < 0 || effect > 0xfff)
                throw new IllegalArgumentException("effect is out of range");
            
            this.effect = effect;
        }

        public boolean isEmpty()
        {
            return equals(empty);
        }
        
        private void validate()
        {
            // TODO
        }
    }
    
    public static class Pattern
    {
        public final Note[][] notes = new Note[64][4];

        @Override
        public boolean equals( Object o )
        {
            if (o instanceof Pattern)
            {
                Pattern other = (Pattern)o;
                return Arrays.deepEquals(notes, other.notes);
            }
            
            return false;
        }
        
        private void validate()
        {
            for (Note[] row : notes)
            {
                if (row == null)
                    throw new IllegalStateException("row is null");
                
                for (Note note : row)
                    if (note != null)
                        note.validate();
            }
        }
    }
}
