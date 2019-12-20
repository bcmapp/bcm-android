package com.bcm.plugin.fcm.util

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.gradle.util.Path

import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.FileChannel
import java.nio.channels.ReadableByteChannel
import java.util.jar.JarEntry
import java.util.jar.JarFile

public class Decompression {

    protected static Log log = LogFactory.getLog(Decompression.class);

    @SuppressWarnings("resource")
    public static void uncompress(File jar, File tarDir) throws IOException {
        JarFile jarFile = new JarFile(jar)
        Enumeration<JarEntry> enumEntry = jarFile.entries();
        while (enumEntry.hasMoreElements()) {
            JarEntry jarEntry = enumEntry.nextElement();
            if (jarEntry.isDirectory()) {
                new File(tarDir, jarEntry.getName()).mkdirs()
            } else  {
                copyFileFromJar(jarFile, jarEntry, tarDir)
            }
        }
    }

    private static void copyFileFromJar(JarFile jarFile, JarEntry jarEntry, File tarDir) {
        File toFile = new File(tarDir, jarEntry.getName())

        String toDir = toFile.absolutePath
        File toDirFile = new File(toDir.substring(0, toDir.lastIndexOf(File.separator)))
        if (!toDirFile.exists()) {
            toDirFile.mkdirs()
        }

        log.info("copyFileFromJar dest dir:" + toDirFile.absolutePath)
        log.info("copyFileFromJar source:" + jarEntry.getName() + " dest: " + toFile.absolutePath)

        ByteBuffer byteBuffer = ByteBuffer.allocate(2*1024*1024)

        InputStream sourceStream = jarFile.getInputStream(jarEntry);
        ReadableByteChannel sourceChannel = Channels.newChannel(sourceStream)

        FileChannel destChannel = new FileOutputStream(toFile).getChannel();
        try {
            while (-1 != (sourceChannel.read(byteBuffer))) {
                byteBuffer.flip()
                destChannel.write(byteBuffer)
                byteBuffer.clear()
            }
        } catch (IOException e) {
            e.printStackTrace()
        } finally {
            if (null != sourceChannel) {
                try {
                    sourceChannel.close()
                } catch (IOException e) {
                    e.printStackTrace()
                }
            }
            if (null != destChannel) {
                try {
                    destChannel.close()
                } catch (IOException e) {
                    e.printStackTrace()
                }
            }
        }
    }
}