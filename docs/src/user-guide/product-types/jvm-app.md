---
description: Learn how to use the `jvm/app` product type in a module to build a JVM console or desktop application.
---
# :intellij-java: JVM application

Use the `jvm/app` product type in a module to build a JVM console or desktop application.

## Module layout

Here is an overview of the module layout for a JVM application:

--8<-- "includes/module-layouts/jvm-app.md"

!!! note "Maven compatibility layout for JVM-only modules"

    If you're migrating from Maven, you can also configure the [Maven-like layout](../advanced/maven-like-layout.md)

## Entry point

By default, the entry point of JVM applications (the `main` function) is expected to be in a `main.kt` file
(case-insensitive) in the `src` folder.
The `main` function must either have no parameters or one `Array<String>` parameter (representing the command line arguments).

This can be overridden by specifying a main class explicitly in the module settings:
```yaml
product: jvm/app

settings:
  jvm:
    mainClass: org.example.myapp.MyMainKt
```

!!! note

    In Kotlin, unlike Java, the `main` function doesn't have to be declared in a class, and is usually at the top level
    of the file. However, the JVM still expects a main class when running any application. Kotlin always compiles 
    top-level declarations to a class, and the name of that class is derived from the name of the file by capitalizing 
    the name and turning the `.kt` extension into a `Kt` suffix.
    
    For example, the top-level declarations of `myMain.kt` will be in a class named `MyMainKt`.

## Packaging

You can use the `build` command to produce a regular JAR of your application's code, or the `package` 
command to produce an [Executable JAR](https://docs.spring.io/spring-boot/specification/executable-jar/index.html).

The executable JAR format is developed by the [Spring](https://spring.io/) team, hence why you might see the 
`spring-boot-loader` embedded in it.
That being said, this format is a universal packaging solution suitable for any JVM application.
It provides a convenient, runnable self-contained deployment unit that includes all necessary dependencies.

!!! question "Why not create an Über JAR (a.k.a Fat JAR)?"

    The usual "Über JAR" (a.k.a "Fat JAR") format is quite popular: it just contains the contents of all your dependency
    JARs, merge with your own classes and resources. This approach has issues that stem from this unpacking/repacking of
    JARs. For example:

      * signature files are no longer valid after the merge, which can render some security libraries invalid
      * `META-INF/MANIFEST.MF` files are duplicated in many JARs, and thus need to be discarded or merged
      * service loader resources also may have conflicting names and need to be merged
      * any other duplicate class/resource file need to be handle in custom ways, which have to be configurable
    
    The executable JAR format doesn't have these problems because it keeps the original JARs intact – it just embeds 
    them and loads them on-demand.
