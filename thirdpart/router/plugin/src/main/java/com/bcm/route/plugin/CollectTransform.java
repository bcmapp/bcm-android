package com.bcm.route.plugin;

import com.android.build.api.transform.*;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import org.gradle.internal.impldep.org.apache.commons.codec.digest.DigestUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Set;

public class CollectTransform extends Transform {
    @Override
    public String getName() {
        return "CollectTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);

        Context context = transformInvocation.getContext();
        Collection<TransformInput> inputs = transformInvocation.getInputs();
        Collection<TransformInput> referencedInputs = transformInvocation.getReferencedInputs();
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        boolean isIncremental = transformInvocation.isIncremental();

        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass ctClass = pool.get("Route$$Module$$Map");

            if (ctClass.isFrozen()) {
                ctClass.defrost();
            }

            CtMethod method = ctClass.getDeclaredMethod("get");

            for (TransformInput transformInput : inputs) {
                for (DirectoryInput directoryInput : transformInput.getDirectoryInputs()) {
                    if (directoryInput.getFile().getName().startsWith("Route$$Module$$")) {
                        method.insertAfter("list.add(\"" + directoryInput.getFile().getName() + "\");");
                    }
                    File dest = outputProvider.getContentLocation(directoryInput.getName(), directoryInput.getContentTypes(), directoryInput.getScopes(), Format.DIRECTORY);
                    FileUtils.copyDirectory(directoryInput.getFile(), dest);
                }

                for (JarInput jarInput : transformInput.getJarInputs()) {
                    String jarName = jarInput.getName();
                    String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
                    if (jarName.endsWith(".jar")) {
                        jarName = jarName.substring(0, jarName.length() - 4);
                    }
                    File dest = outputProvider.getContentLocation(jarName + md5Name, jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);

                    FileUtils.copyFile(jarInput.getFile(), dest);
                }
            }

            ctClass.writeFile();
            ctClass.detach();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
