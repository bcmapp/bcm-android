package com.bcm.plugin.fcm

import com.android.build.api.transform.*
import com.bcm.plugin.fcm.util.Compressor
import com.bcm.plugin.fcm.util.Decompression
import com.google.common.collect.Sets
import groovy.io.FileType
import jdk.internal.org.objectweb.asm.Opcodes
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor

class MessagingAnalyticsDisable extends Transform {

    Project mProject

    MessagingAnalyticsDisable(Project project) {
        mProject = project
    }

    @Override
    String getName() {
        return "bcm_disable_fcm_analytics"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return Collections.singleton(QualifiedContent.DefaultContentType.CLASSES)
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return Sets.immutableEnumSet(
                QualifiedContent.Scope.PROJECT,
                QualifiedContent.Scope.SUB_PROJECTS,
                QualifiedContent.Scope.EXTERNAL_LIBRARIES
        )
    }

    @Override
    boolean isIncremental() {
        return true
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        transformInvocation.inputs.each { input ->
            // Find annotated classes in jar
            input.jarInputs.each { jarInput ->
                if (!jarInput.file.exists()) return
                mProject.logger.info("jar input:" + jarInput.file.getAbsolutePath())
                mProject.logger.info("jar name:" + jarInput.name)

                def jarName = jarInput.name
                if (jarName.startsWith("com.google.firebase:firebase-messaging")) {
                    def dest = transformInvocation.outputProvider.getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                    mProject.logger.info("jar output path:" + dest.getAbsolutePath())
                    FileUtils.copyFile(jarInput.file, dest)

                    traversalJar(
                            transformInvocation,
                            jarInput,
                            {File outputFile, File inputFile ->
                                if (!inputFile.exists() || !inputFile.name.endsWith("MessagingAnalytics.class")) {
                                    return
                                }

                                mProject.logger.info("inputFile name:" + inputFile.name)
                                def inputStream = new FileInputStream(inputFile)
                                ClassReader cr = new ClassReader(inputStream)
                                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                                ClassVisitor classVisitor = new MessagingAnalyticsVisitor(cw)
                                cr.accept(classVisitor, 0)
                                outputFile.bytes = cw.toByteArray()
                                inputStream.close()
                            })
                } else {
                    def dest = transformInvocation.outputProvider.getContentLocation(jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                    mProject.logger.info("jar output path:" + dest.getAbsolutePath())
                    FileUtils.copyFile(jarInput.file, dest)
                }
            }

            input.directoryInputs.each { dirInput ->
                mProject.logger.info("dirInput.file :" + dirInput.file)

                def outDir = transformInvocation.outputProvider.getContentLocation(dirInput.name, dirInput.contentTypes, dirInput.scopes, Format.DIRECTORY)
                int pathBitLen = dirInput.file.toString().length()

                def callback = { File it ->
                    if (it.exists()) {
                        def path = "${it.toString().substring(pathBitLen)}"
                        if (it.isDirectory()) {
                            new File(outDir, path).mkdirs()
                        } else {
                            def output = new File(outDir, path)
                            if (!output.parentFile.exists()) output.parentFile.mkdirs()
                            output.bytes = it.bytes
                        }
                    }
                }

                if (dirInput.changedFiles != null && !dirInput.changedFiles.isEmpty()) {
                    dirInput.changedFiles.keySet().each(callback)
                }
                if (dirInput.file != null && dirInput.file.exists()) {
                    dirInput.file.traverse(callback)
                }
            }
        }
    }

    class MessagingAnalyticsVisitor extends ClassVisitor {

        MessagingAnalyticsVisitor(ClassVisitor cv) {
            super(Opcodes.ASM5, cv)
        }

        @Override
        MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
            mProject.logger.info("visiting method: $access, $name, $desc, $signature, $exceptions")
            if (access == 9 && (name+desc) == "shouldUploadMetrics(Landroid/content/Intent;)Z") {
                return new DisableShouldUploadMetricsVisitor(methodVisitor)
            }
            return methodVisitor;
        }
    }

    class DisableShouldUploadMetricsVisitor extends MethodVisitor {

        DisableShouldUploadMetricsVisitor(MethodVisitor mv) {
            super(Opcodes.ASM5, mv)
        }

        @Override
        void visitCode() {
            mv.visitInsn(Opcodes.ICONST_0)
            mv.visitInsn(Opcodes.IRETURN)
            super.visitCode()
        }
    }

    static void traversalJar(TransformInvocation transformInvocation, JarInput jarInput, Closure closure) {
        def jarName = jarInput.name

        File unzipDir = new File(
                jarInput.file.getParent(),
                jarName.replace(":", "") + "_unzip")
        if (unzipDir.exists()) {
            unzipDir.delete()
        }
        unzipDir.mkdirs()
        Decompression.uncompress(jarInput.file, unzipDir)

        File repackageFolder = new File(
                jarInput.file.getParent(),
                jarName.replace(":", "") + "_repackage"
        )

        FileUtils.copyDirectory(unzipDir, repackageFolder)

        unzipDir.eachFileRecurse(FileType.FILES, { File it ->
            File outputFile = new File(repackageFolder, it.absolutePath.split("_unzip")[1])
            closure.call(outputFile, it)
        })

        def dest = transformInvocation.outputProvider.getContentLocation(
                jarName, jarInput.contentTypes, jarInput.scopes, Format.JAR)
        Compressor zc = new Compressor(dest.getAbsolutePath())
        zc.compress(repackageFolder.getAbsolutePath())
    }
}