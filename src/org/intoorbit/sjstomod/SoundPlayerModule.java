/* 
 * Copyright (c) 2014 Carl Reinke
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.intoorbit.sjstomod;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import org.intoorbit.sjstomod.utils.StringUtils;

/**
 *
 * @author mindless
 */
public class SoundPlayerModule
{
    public static SoundPlayerModule load( InputStream inStream ) throws IOException
    {
        DataInputStream in = new DataInputStream(inStream);
        
        SoundPlayerModule module = new SoundPlayerModule();
        
        module.delay = in.readUnsignedByte();
        module.delay |= in.readUnsignedByte() << 8;
        module.flags = in.readUnsignedByte();
        
        if ((module.flags & 0x0f) == 0)
            throw new IllegalStateException("module has no voices");
        
        if ((module.flags & ~0x0f) != 0)
            throw new IllegalStateException("module has unknown flags");
        
        for (int i = 0; i < module.voices.length; ++i)
            module.voices[i] = new Voice();
        
        int[] voiceRowCounts = new int[module.voices.length];
        
        int ended = 0;
        while ((ended & module.flags & 0x0f) != (module.flags & 0x0f))
        {
            for (int v = 0; v < module.voices.length; ++v)
            {
                Note note = new Note();
                
                note.semitone = in.readUnsignedByte();
                note.sample = in.readUnsignedByte();
                note.effect = in.readUnsignedByte();
                
                if ((ended & (1 << v)) != 0)
                    continue;
                
                Voice voice = module.voices[v];
                
                voice.notes.put(voiceRowCounts[v], note);
                voiceRowCounts[v] += 1;

                if (note.effect >= 0x57 && note.effect <= 0x88)
                {
                    // delay voice rows
                    voiceRowCounts[v] += note.effect - 0x57;
                }
                else if (note.effect == 0xde)
                {
                    // repeat voice
                    ended |= 1 << v;
                }
            }
        }
        
        module.rowCount = -1;
        for (int i = 0; i < voiceRowCounts.length; ++i)
        {
            if ((module.flags & (1 << i)) != 0)
            {
                if (module.rowCount == -1)
                    module.rowCount = voiceRowCounts[i];
                else if (voiceRowCounts[i] != module.rowCount)
                    throw new IllegalStateException("voices have mismatched row counts");
            }
        }
        
        return module;
    }
    
    private static final Charset charset = Charset.forName("ISO-8859-1");
        
    public static String[] determineSampleNames( RandomAccessFile levelDataFile, String moduleName ) throws IOException
    {
        if (moduleName.length() > 11)
            throw new IllegalArgumentException("moduleName is too long");
        
        byte[] moduleNameBytes = Arrays.copyOf(moduleName.getBytes(charset), 12);
        
        int maxSampleId = 0;
        
        long moduleEntryPosition = levelDataFile.length() - 16 - 12;
        
        while (0x1d38 + maxSampleId * (11 + 1) <= moduleEntryPosition)
        {
            levelDataFile.seek(moduleEntryPosition);
            
            byte[] moduleEntryNameBytes = new byte[12];
            
            levelDataFile.readFully(moduleEntryNameBytes);

            int[] moduleEntrySampleIds = new int[16];
            
            for (int i = 0; i < 16; ++i)
                moduleEntrySampleIds[i] = levelDataFile.readUnsignedByte();
            
            if (Arrays.equals(moduleEntryNameBytes, moduleNameBytes))
            {
                String[] sampleNames = new String[16];
                
                for (int i = 0; i < 16; ++i)
                {
                    if (moduleEntrySampleIds[i] > 0)
                    {
                        levelDataFile.seek(0x1d38 + (moduleEntrySampleIds[i] - 1) * (11 + 1));

                        byte[] sampleNameBytes = new byte[11];
                        
                        levelDataFile.readFully(sampleNameBytes);
                        
                        int sampleEntryUnknownFlag = levelDataFile.readUnsignedByte();
                        
                        String sampleEntryName = new String(sampleNameBytes, charset);
                        
                        int sampleEntryNameLength = sampleEntryName.indexOf(0);
                        
                        if (sampleEntryNameLength < 0)
                            throw new IllegalStateException();
                        
                        sampleNames[i] = sampleEntryName.substring(0, sampleEntryNameLength);
                    }
                }
                
                return sampleNames;
            }

            for (int i = 0; i < 16; ++i)
                if (moduleEntrySampleIds[i] > maxSampleId)
                    maxSampleId = moduleEntrySampleIds[i];
            
            moduleEntryPosition -= 16 + 12;
        }
        
        return null;
    }
    
    public int delay;  // uint16
    public int flags;  // uint8

    public Voice[] voices = new Voice[4];
    
    public int rowCount;
    
    public int getBpm()
    {
        // same as SoundFX
        return 14565 * 122 / delay;
    }
    
    public void dump()
    {
        System.out.printf("delay:  %d (%d bpm)\n", delay, getBpm());
        System.out.printf("voices: %s\n", StringUtils.reverse(StringUtils.leftPad(Integer.toBinaryString(flags & 0x0f), 4, '0')));
        
        System.out.printf("rows:   %d\n", rowCount);
        
        String[] noteNames = new String[] { "C-", "C#", "D-", "D#", "E-", "F-", "F#", "G-", "G#", "A-", "A#", "B-" };
        
        for (int r = 0; r < rowCount; ++r)
        {
            for (int v = 0; v < voices.length; ++v)
            {
                Voice voice = voices[v];
                Note note = voice.notes.get(r);
                
                if (note == null)
                {
                    System.out.print(" | ... .. .. ...");
                    continue;
                }
                
                String semitone;
                if (note.semitone == 0)
                {
                    semitone = "...";
                }
                else
                {
                    int adjustedSemitone = note.semitone < 4 ? note.semitone - 1 : note.semitone;  // apparently no B-3
                    int octave = (adjustedSemitone - 1 + 9) / 12;
                    semitone = String.format("%s%d", noteNames[adjustedSemitone - 1 + 9 - octave * 12], octave + 3);
                }
                
                String sampleNotation = note.sample == 0 ?
                        ".." :
                        String.format("%02d", note.sample);
                
                String effectNotation;
                if (note.effect == 0x00)
                {
                    // no op
                    effectNotation = "...";
                }
                else if (note.effect == 0x01)
                {
                    // disable high-cut filter
                    effectNotation = "E01";
                }
                else if (note.effect == 0x02)
                {
                    // enable high-cut filter
                    effectNotation = "E00";
                }
                else if (note.effect <= 0x42)
                {
                    assert(note.effect >= 0x03);
                    // set voice volume (persistent)
                    effectNotation = String.format("V%02X", note.effect - 0x03);
                }
                else if (note.effect == 0x43)
                {
                    // disable voice DMA (i.e., cut previous note)
                    effectNotation = note.semitone == 0 || note.sample == 0 ?
                            "C00" /* or "0xEC0" */ :
                            "...";
                }
                else if (note.effect <= 0x56)
                {
                    assert(note.effect >= 0x44);
                    // no op
                    effectNotation = "...";
                }
                else if (note.effect <= 0x88)
                {
                    assert(note.effect >= 0x57);
                    // delay voice rows
                    effectNotation = "...";
                }
                else if (note.effect <= 0xa6)
                {
                    assert(note.effect >= 0x89);
                    // no op
                    effectNotation = "...";
                }
                else if (note.effect <= 0xb0)
                {
                    assert(note.effect >= 0xa7);
                    // unknown op 6
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect <= 0xba)
                {
                    assert(note.effect >= 0xb1);
                    // unknown op 7
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect <= 0xce)
                {
                    assert(note.effect >= 0xbb);
                    // unknown op 8
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xcf)
                {
                    // unknown op 9
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xd0)
                {
                    // unknown op 10
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xd1)
                {
                    // unknown op 11
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect <= 0xdb)
                {
                    assert(note.effect >= 0xd2);
                    // unknown op 12
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xdc)
                {
                    // unknown op 13
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xdd)
                {
                    // unknown op 14
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xde)
                {
                    // repeat voice
                    effectNotation = "B00";
                }
                else if (note.effect == 0xdf)
                {
                    // unknown op 16
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xe0)
                {
                    // unknown op 17
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xe1)
                {
                    // unknown op 18
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xe2)
                {
                    // unknown op 19
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xe3)
                {
                    // unknown op 20
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xe4)
                {
                    // unknown op 21
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect <= 0xf8)
                {
                    assert(note.effect >= 0xe5);
                    // unknown op 22
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xf9)
                {
                    // unknown op 23
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xfa)
                {
                    // unknown op 24
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xfb)
                {
                    // unknown op 25
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xfc)
                {
                    // unknown op 26
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect == 0xfd)
                {
                    // unknown op 27
                    effectNotation = String.format("?%02X", note.effect);
                }
                else if (note.effect <= 0xff)
                {
                    assert(note.effect >= 0xfe);
                    // no op
                    effectNotation = String.format("?%02X", note.effect);
                }
                else
                {
                    throw new IllegalStateException("invalid effect");
                }
                
                System.out.printf(" | %s %s .. %s", semitone, sampleNotation, effectNotation);
            }
            
            System.out.println();
        }
    }
    
    public ProtrackerModule toProtracker( ProtrackerModule.Sample[] ptSamples )
    {
        // SJS allows for some initial set-up rows which we try to collapse
        int collapsedRowCount = rowCount % 64;
        
        for (int v = 0; v < voices.length; ++v)
        {
            Voice voice = voices[v];
            
            for (int r = 0; r < collapsedRowCount; ++r)
            {
                Note note = voice.notes.get(r);
                
                if (note == null)
                    continue;
                
                if (note.semitone != 0)
                    collapsedRowCount = 0;
            }
        }
        
        ProtrackerModule ptModule = new ProtrackerModule();
        
        for (int i = 0; i < ptSamples.length; ++i)
            ptModule.samples[i] = ptSamples[i];
        
        for (int patternStartRow = collapsedRowCount; patternStartRow < rowCount; patternStartRow += 64)
        {
            ProtrackerModule.Pattern ptPattern = new ProtrackerModule.Pattern();
            
            for (int v = 0; v < 4; ++v)
            {
                Voice voice = voices[v];

                for (int r = 0; r < 64; ++r)
                {
                    Note note = voice.notes.get(patternStartRow + r);

                    if (note != null)
                    {
                        ProtrackerModule.Note ptNote = new ProtrackerModule.Note();

                        int adjustedNote = note.semitone < 4 ? note.semitone - 1 : note.semitone;  // apparently no B-3
                        
                        ptNote.setPeriod(note.semitone == 0 ?
                                0 :
                                ProtrackerModule.getPeriod(adjustedNote - 1 + 9));
                        
                        ptNote.setSample(note.sample);
                        
                        ptNote.setEffect(translateEffectToProtracker(note));

                        if (!ptNote.isEmpty())
                            ptPattern.notes[r][v] = ptNote;
                    }
                }
            }
            
            if (patternStartRow == collapsedRowCount)
                translateInitialEffects(collapsedRowCount, ptPattern);
            
            int patternIndex;
            
            for (patternIndex = 0; patternIndex < ptModule.patterns.size(); ++patternIndex)
                if (ptModule.patterns.get(patternIndex).equals(ptPattern))
                    break;
            
            if (patternIndex == ptModule.patterns.size())
                ptModule.patterns.add(ptPattern);
            
            ptModule.patternTable.add(patternIndex);
        }
        
        translateVolumeToProtracker(ptModule);
                
        return ptModule;
    }

    private void translateInitialEffects( int collapsedRowCount, ProtrackerModule.Pattern firstPtPattern )
    {
        // best-effort placment of effects from collapsed rows
        for (int v = 0; v < voices.length; ++v)
        {
            Voice voice = voices[v];
            
            for (int r = 0; r < collapsedRowCount; ++r)
            {
                Note note = voice.notes.get(r);
                
                if (note == null)
                    continue;
                
                int ptEffect = translateEffectToProtracker(note);
                
                if (ptEffect != 0)
                {
                    ProtrackerModule.Note ptNote = firstPtPattern.notes[0][v];
                    
                    if (ptNote == null)
                    {
                        ptNote = new ProtrackerModule.Note();
                        firstPtPattern.notes[0][v] = ptNote;
                    }
                    
                    if (ptNote.getEffect() != 0)
                        throw new UnsupportedOperationException("irreconcilible initial effects");
                    
                    ptNote.setEffect(ptEffect);
                }
            }
        }
        
        int tpd = 6;
        int bpm = getBpm();

        // adjust BPM and TPD so that BPM is in range
        if (bpm < 0x20)
        {
            throw new UnsupportedOperationException("small BPM");
        }
        else if (bpm > 0xff)
        {
            int minDivisor = (bpm - 1) / 0xff + 1;

            for (int d = minDivisor; d <= tpd; ++d)
            {
                if (tpd % d == 0 && bpm % d == 0)
                {
                    tpd /= d;
                    bpm /= d;
                }
            }

            if (bpm > 0xff)
            {
                int oldTpd = tpd;
                tpd = (int)(oldTpd / (bpm / 0xff.p0f));
                bpm = bpm * tpd / oldTpd;
            }

            if (tpd == 0)
                throw new IllegalStateException();
        }
        
        ArrayList<Integer> firstRowPtEffects = new ArrayList<>();
        
        if (bpm != 125)
            firstRowPtEffects.add(0xf00 | bpm);
        if (tpd != 6)
            firstRowPtEffects.add(0xf00 | tpd);
        
        // best-effort palement of tempo effect
        nextFirstRowEffect:
        for (int ptEffect : firstRowPtEffects)
        {
            for (int v = 4 - 1; v >= 0; --v)
            {
                ProtrackerModule.Note ptNote = firstPtPattern.notes[0][v];
                
                if (ptNote == null)
                {
                    ptNote = new ProtrackerModule.Note();
                    firstPtPattern.notes[0][v] = ptNote;
                }
                
                if (ptNote.getEffect() == 0)
                {
                    ptNote.setEffect(ptEffect);
                    continue nextFirstRowEffect;
                }
            }
            
            throw new UnsupportedOperationException("irreconcilable initial effects");
        }
    }
    
    // the SJS files from the Lemmings games never change the volume after
    // initially setting it, so we just apply the volume to the samples
    private void translateVolumeToProtracker( ProtrackerModule ptModule ) throws UnsupportedOperationException
    {
        Integer[] sampleVolumes = new Integer[31];
        
        for (int v = 0; v < 4; ++v)
        {
            Voice voice = voices[v];
            
            int voiceVolume = 63;
            
            for (Note note : voice.notes.values())
            {
                if (note.effect >= 0x03 && note.effect <= 0x42)
                    voiceVolume = note.effect - 0x03;
                
                if (note.semitone == 0 || note.sample == 0)
                    continue;
                
                Integer oldSampleVolume = sampleVolumes[note.sample - 1];
                
                if (oldSampleVolume == null)
                    sampleVolumes[note.sample - 1] = voiceVolume;
                else if (oldSampleVolume != voiceVolume)
                    throw new UnsupportedOperationException("sample volume change");
                
            }
        }
        
        for (int i = 0; i < 31; ++i)
        {
            Integer sampleVolume = sampleVolumes[i];
            
            if (sampleVolume != null)
            {
                ProtrackerModule.Sample sample = ptModule.samples[i];
                
                if (sample == null)
                {
                    sample = new ProtrackerModule.Sample();
                    ptModule.samples[i] = sample;
                    sample.setVolume(63);
                }
                    
                sample.setVolume(sample.getVolume() * sampleVolume / 63);
            }
        }
    }

    // the SJS files from the Lemmings games use very few of the effects, so
    // most of them are untranslated
    private static int translateEffectToProtracker( Note note ) throws IllegalStateException
    {
        int ptEffect;
        
        if (note.effect == 0x00)
        {
            // no op
            ptEffect = 0;
        }
        else if (note.effect == 0x01)
        {
            // disable high-cut filter
            ptEffect = 0xE01;
        }
        else if (note.effect == 0x02)
        {
            // enable high-cut filter
            ptEffect = 0xE00;
        }
        else if (note.effect <= 0x42)
        {
            assert(note.effect >= 0x03);
            // set voice volume (persistent)
            ptEffect = 0;
        }
        else if (note.effect == 0x43)
        {
            // disable voice DMA (i.e., cut previous note)
            ptEffect = note.semitone == 0 || note.sample == 0 ?
                    0xC00 /* or 0xEC0 */ :
                    0;
        }
        else if (note.effect <= 0x56)
        {
            assert(note.effect >= 0x44);
            // no op
            ptEffect = 0;
        }
        else if (note.effect <= 0x88)
        {
            assert(note.effect >= 0x57);
            // delay voice rows
            ptEffect = 0;
        }
        else if (note.effect <= 0xa6)
        {
            assert(note.effect >= 0x89);
            // no op
            ptEffect = 0;
        }
        else if (note.effect <= 0xb0)
        {
            assert(note.effect >= 0xa7);
            // unknown op 6
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect <= 0xba)
        {
            assert(note.effect >= 0xb1);
            // unknown op 7
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect <= 0xce)
        {
            assert(note.effect >= 0xbb);
            // unknown op 8
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xcf)
        {
            // unknown op 9
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xd0)
        {
            // unknown op 10
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xd1)
        {
            // unknown op 11
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect <= 0xdb)
        {
            assert(note.effect >= 0xd2);
            // unknown op 12
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xdc)
        {
            // unknown op 13
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xdd)
        {
            // unknown op 14
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xde)
        {
            // repeat voice
            // (voice row positions are independent of one another in SJS and 
            // could go out-of-sync, but this effect only ever appears
            // synchronized at the end of modules, so we don't bother trying to
            // translate it)
            ptEffect = 0xB00;
        }
        else if (note.effect == 0xdf)
        {
            // unknown op 16
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xe0)
        {
            // unknown op 17
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xe1)
        {
            // unknown op 18
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xe2)
        {
            // unknown op 19
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xe3)
        {
            // unknown op 20
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xe4)
        {
            // unknown op 21
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect <= 0xf8)
        {
            assert(note.effect >= 0xe5);
            // unknown op 22
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xf9)
        {
            // unknown op 23
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xfa)
        {
            // unknown op 24
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xfb)
        {
            // unknown op 25
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xfc)
        {
            // unknown op 26
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect == 0xfd)
        {
            // unknown op 27
            throw new IllegalStateException("unknown effect");
        }
        else if (note.effect <= 0xff)
        {
            assert(note.effect >= 0xfe);
            // no op
            ptEffect = 0;
        }
        else
        {
            throw new IllegalStateException("invalid effect");
        }
        
        return ptEffect;
    }

    public static class Note
    {
        public int semitone;  // uint8
        public int sample;    // uint8
        public int effect;    // uint8
    }
    
    public static class Voice
    {
        public TreeMap<Integer, Note> notes = new TreeMap<>();
    }
}
