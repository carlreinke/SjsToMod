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
        
        SjsModule module = null;
        try
        {
            module = SjsModule.load(moduleStream);
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
        
        Path moduleDirectoryPath = modulePath.getParent();
        
        File levelDataFile = moduleDirectoryPath.resolve("leveldata").toFile();

        ProtrackerModule.Sample[] ptSamples = new ProtrackerModule.Sample[16];
        
        try
        {
            RandomAccessFile levelDataDataInputFile = new RandomAccessFile(levelDataFile, "r");
            
            String[] sampleNames = SjsModule.determineSampleNames(levelDataDataInputFile, moduleName);
            
            if (sampleNames != null)
            {
                for (int i = 0; i < sampleNames.length; ++i)
                {
                    if (sampleNames[i] != null)
                    {
                        File sampleFile = moduleDirectoryPath.resolve(sampleNames[i]).toFile();

                        FileInputStream sampleStream = new FileInputStream(sampleFile);

                        Iff8svx sample = Iff8svx.loadForm(sampleStream);

                        ptSamples[i] = sample.toProtracker();
                        
                        ptSamples[i].setName(sampleNames[i]);
                    }
                }
            }
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
        
        // ptModule.dump();
        
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
}
