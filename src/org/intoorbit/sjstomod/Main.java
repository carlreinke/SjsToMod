/* 
 * Copyright (c) 2014 Carl Reinke
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.intoorbit.sjstomod;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mindless
 */
public class Main
{
    private Main()
    {
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main( String[] args )
    {
        if (args.length < 1)
        {
            System.err.println("SJS-to-MOD Converter v1");
            System.err.println("Usage: sjsToMod <sjsModule> [<protrackerModule>]");
            System.err.println("'leveldata' and sample files must be in the same directory as sjsModule");
            System.exit(-1);
        }
        
        Path modulePath = Paths.get(args[0]);

        File moduleFile = modulePath.toFile();
        
        FileInputStream moduleStream = null;
        try
        {
            moduleStream = new FileInputStream(moduleFile);
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
        
        SoundPlayerModule module = null;
        try
        {
            module = SoundPlayerModule.load(moduleStream);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
        
        if (args.length < 2)
        {
            module.dump();
            System.exit(-1);
        }

        String moduleName = modulePath.getFileName().toString();
        
        Path moduleDirectoryPath = modulePath.toAbsolutePath().getParent();
        
        ProtrackerModule.Sample[] ptSamples = new ProtrackerModule.Sample[16];
        
        try
        {
            loadSamples(moduleDirectoryPath, moduleName, ptSamples);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        FileOutputStream outStream = null;
        try
        {
            outStream = new FileOutputStream(args[1]);
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
        
        ProtrackerModule ptModule = module.toProtracker(ptSamples);
        
        ptModule.setTitle(moduleName);
        
        try
        {
            ptModule.save(outStream);
        }
        catch (IOException ex)
        {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(-1);
        }
    }

    private static void loadSamples( Path moduleDirectoryPath, String moduleName, ProtrackerModule.Sample[] ptSamples ) throws IOException
    {
        File levelDataFile = moduleDirectoryPath.resolve("leveldata").toFile();
        if (!levelDataFile.exists())
        {
            System.err.println("cannot determine samples: missing 'leveldata'");
            return;
        }
        
        RandomAccessFile levelDataDataInputFile = new RandomAccessFile(levelDataFile, "r");

        String[] sampleNames = SoundPlayerModule.determineSampleNames(levelDataDataInputFile, moduleName);

        if (sampleNames == null)
        {
            System.err.println("cannot determine samples: no entry in 'leveldata'");
            return;
        }

        for (int i = 0; i < sampleNames.length; ++i)
        {
            if (sampleNames[i] == null)
                continue;

            File sampleFile = moduleDirectoryPath.resolve(sampleNames[i]).toFile();
            if (!sampleFile.exists())
            {
                System.err.printf("missing sample '%s'\n", sampleNames[i]);
                continue;
            }

            FileInputStream sampleStream = new FileInputStream(sampleFile);

            Iff8svx sample = Iff8svx.loadForm(sampleStream);

            ptSamples[i] = sample.toProtracker();

            ptSamples[i].setName(sampleNames[i]);
        }
    }
}
