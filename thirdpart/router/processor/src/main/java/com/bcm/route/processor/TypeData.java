package com.bcm.route.processor;

import com.squareup.javapoet.ClassName;

import javax.lang.model.element.TypeElement;

public class TypeData {
    private TypeElement typeElement;
    private ClassName className;

    public TypeData(TypeElement typeElement, ClassName className) {
        this.typeElement = typeElement;
        this.className = className;
    }

    public TypeElement getTypeElement() {
        return typeElement;
    }

    public void setTypeElement(TypeElement typeElement) {
        this.typeElement = typeElement;
    }

    public ClassName getClassName() {
        return className;
    }

    public void setClassName(ClassName className) {
        this.className = className;
    }
}
