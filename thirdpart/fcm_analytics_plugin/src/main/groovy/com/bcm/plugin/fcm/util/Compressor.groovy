package com.bcm.plugin.fcm.util

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.gradle.util.Path

import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

public class Compressor {

    private static Log log = LogFactory.getLog(Compressor.class);

    private static final int BUFFER = 2 * 1024 * 1024

    private File destFile

    public Compressor(String destPath) {
        destFile = new File(destPath)
    }

    public void compress(String sourceDir) {
        File sourceDirFile = new File(sourceDir);
        if (!sourceDirFile.exists())
            throw new RuntimeException(srcPathName + " not found")
        try {
            File[] sourceFiles = sourceDirFile.listFiles();
            if (null == sourceFiles || sourceFiles.length == 0) {
                log.info("compress path:" + srcPathName + "is empty")
            } else {
                FileOutputStream destStream = new FileOutputStream(destFile)
                ZipOutputStream out = new ZipOutputStream(new CheckedOutputStream(destStream, new CRC32()))

                sourceFiles.each { file ->
                    compress(file, out, "")
                }
                out.close()
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void compress(File file, ZipOutputStream out, String basedir) {
        if (file.isDirectory()) {
            this.compressDirectory(file, out, basedir);
        } else {
            this.compressFile(file, out, basedir);
        }
    }

    private void compressDirectory(File dir, ZipOutputStream out, String basedir) {
        if (dir.exists()) {
            dir.listFiles().each { file ->
                compress(file, out, basedir + dir.getName() + File.separator);
            }
        }
    }

    private void compressFile(File file, ZipOutputStream out, String basedir) {
        if (file.exists()) {
            try {
                BufferedInputStream bis = new BufferedInputStream(
                        new FileInputStream(file))

                String filePath = (basedir + file.getName())

                ZipEntry entry = new ZipEntry(filePath);
                out.putNextEntry(entry)
                int count
                byte[] data = new byte[BUFFER]
                while ((count = bis.read(data, 0, BUFFER)) != -1) {
                    out.write(data, 0, count);
                }
                bis.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}