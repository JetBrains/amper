/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.annotation;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_17;

@SupportedAnnotationTypes("com.example.annotation.HelloWorld")
@SupportedSourceVersion(RELEASE_17)
public class HelloWorldProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (TypeElement annotation : annotations) {
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                HelloWorld helloWorld = element.getAnnotation(HelloWorld.class);
                String name = helloWorld.value();

                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.NOTE,
                    "Hello, " + name + "! from " + element.getSimpleName()
                );
            }
        }
        return true;
    }
}