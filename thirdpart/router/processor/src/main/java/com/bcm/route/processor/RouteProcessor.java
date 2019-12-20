package com.bcm.route.processor;

import com.squareup.javapoet.*;
import com.bcm.route.annotation.Route;
import com.bcm.route.annotation.RouteModel;
import com.bcm.route.annotation.RouteType;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.util.*;

@SupportedAnnotationTypes(Consts.CONST_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class RouteProcessor extends AbstractProcessor {
    private Messager messager;
    private Types types;
    private Elements elementUtils;
    private String moduleName;
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);

        messager = processingEnvironment.getMessager();
        types = processingEnvironment.getTypeUtils();
        elementUtils = processingEnvironment.getElementUtils();
        filer = processingEnvironment.getFiler();

        moduleName = processingEnvironment.getOptions().get(Consts.MODULE_NAME);
        if (moduleName == null) {
            // Developer has not set ModuleName in build.gradle, stop compile
            messager.printMessage(Diagnostic.Kind.ERROR, Consts.MODULE_NAME + " is null!!");
        }

        messager.printMessage(Diagnostic.Kind.NOTE, "Init process success");
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        messager.printMessage(Diagnostic.Kind.NOTE, "process start");
        List<TypeData> typeDataList = findAnnotations(roundEnvironment);
//        boolean isEmpty = typeDataList.isEmpty();
//        messager.printMessage(Diagnostic.Kind.WARNING, "type data list is empty? = " + isEmpty);
        if (!typeDataList.isEmpty()) {
            // Find out Classes annotated by Route, start generate codes
            genRouteFile(typeDataList);
        }
        return true;
    }

    private List<TypeData> findAnnotations(RoundEnvironment roundEnvironment) {
        ArrayList<TypeData> typeDataList = new ArrayList<>();

        // Find out all classes annotated by Route
        Set<? extends Element> elements = roundEnvironment.getElementsAnnotatedWith(Route.class);
        for (Element e : elements) {
            TypeElement typeElement = (TypeElement) e;
            TypeData typeData = genTypeData(typeElement);
            if (typeData != null) {
                typeDataList.add(typeData);
            }
        }

        return typeDataList;
    }

    private TypeData genTypeData(TypeElement element) {
        ClassName className = ClassName.get(element);
        ElementKind kind = element.getKind();
        if (kind.isClass()) {
            return new TypeData(element, className);
        } else {
            // Element is not a class, miss it
            messager.printMessage(Diagnostic.Kind.WARNING, className + " is not a class");
            return null;
        }
    }

    private void genRouteFile(List<TypeData> typeDataList) {
        TypeMirror typeActivity = elementUtils.getTypeElement(Consts.ACTIVITY).asType();
        TypeMirror typeFragment = elementUtils.getTypeElement(Consts.FRAGMENT).asType();
        TypeMirror typeFragmentX = elementUtils.getTypeElement(Consts.FRAGMENT_ANDROIDX).asType();
        TypeMirror typeProvider = elementUtils.getTypeElement(Consts.PROVIDER).asType();
        TypeMirror typeService = elementUtils.getTypeElement(Consts.SERVICE).asType();

//        String packageName = elementUtils.getPackageOf(typeDataList.get(0).getTypeElement()).getQualifiedName().toString();
//        messager.printMessage(Diagnostic.Kind.WARNING, "package name = " + packageName);

        // Generate Route$$Module$${moduleName} class, implement IRoutePaths
        TypeSpec.Builder routeModuleBuilder = TypeSpec.classBuilder("Route$$Module$$" + moduleName)
                .addJavadoc(Consts.AUTO_GEN_DOCS)
                .addSuperinterface(ClassName.get(Consts.PACKAGE_API, Consts.I_ROUTE_PATHS))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // Create loadInto method's parameter
        ParameterizedTypeName inputMethodType = ParameterizedTypeName.get(
                ClassName.get(HashMap.class),
                ClassName.get(String.class),
                ClassName.get(RouteModel.class)
        );

        // Implement loadInto method
        MethodSpec.Builder loadIntoBuilder = MethodSpec.methodBuilder("loadInto")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(ParameterSpec.builder(inputMethodType, "routes").build());

        for (TypeData data : typeDataList) {
            TypeElement element = data.getTypeElement();
            Route annotation = element.getAnnotation(Route.class);
            if (types.isSubtype(element.asType(), typeActivity)) {
                // Element is an Activity
                loadIntoBuilder.addStatement("routes.put($S, new RouteModel($T." + RouteType.ACTIVITY + ", $S, $S, $T.class));",
                        annotation.routePath(),
                        ClassName.get(RouteType.class),
                        moduleName,
                        annotation.routePath(),
                        data.getClassName());
            } else if (types.isSubtype(element.asType(), typeFragment)) {
                // Element is a Fragment
                loadIntoBuilder.addStatement("routes.put($S, new RouteModel($T." + RouteType.FRAGMENT + ", $S, $S, $T.class));",
                        annotation.routePath(),
                        ClassName.get(RouteType.class),
                        moduleName,
                        annotation.routePath(),
                        data.getClassName());
            } else if (types.isSubtype(element.asType(), typeFragmentX)) {
                // Element is an AndroidX Fragment
                loadIntoBuilder.addStatement("routes.put($S, new RouteModel($T." + RouteType.FRAGMENT + ", $S, $S, $T.class));",
                        annotation.routePath(),
                        ClassName.get(RouteType.class),
                        moduleName,
                        annotation.routePath(),
                        data.getClassName());
            } else if (types.isSubtype(element.asType(), typeProvider)) {
                // Element is a IRouteProvider
                loadIntoBuilder.addStatement("routes.put($S, new RouteModel($T." + RouteType.PROVIDER + ", $S, $S, $T.class));",
                        annotation.routePath(),
                        ClassName.get(RouteType.class),
                        moduleName,
                        annotation.routePath(),
                        data.getClassName());
            } else if (types.isSubtype(element.asType(), typeService)) {
                // Element is a Service
                loadIntoBuilder.addStatement("routes.put($S, new RouteModel($T." + RouteType.SERVICE + ", $S, $S, $T.class));",
                        annotation.routePath(),
                        ClassName.get(RouteType.class),
                        moduleName,
                        annotation.routePath(),
                        data.getClassName());
            } else {
                // Element is not a specific class type, stop compile
                messager.printMessage(Diagnostic.Kind.ERROR, data.getClassName() + " is not a Activity or a Fragment or an IRouteProvider");
            }
        }

        // Write to file
        routeModuleBuilder.addMethod(loadIntoBuilder.build());
        messager.printMessage(Diagnostic.Kind.NOTE, "Write generated codes to file");
        try {
            JavaFile.builder(Consts.PACKAGE_NAME, routeModuleBuilder.build()).build().writeTo(filer);
        } catch (Exception e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }
}
