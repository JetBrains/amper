# Lombok

Despite there is a way to configure annotation processing in general for Java, for the Kotlin interop you need a
compiler plugin configured. So if you want to add Lombok to your project it is better to use `settings:lombok` instead
of configuring `settings:java:annotationProcessing` directly even if your module is java-only. Enabling Lombok is by

```yaml

  settings:
    lombok: enabled
```

adds `lombok` dependency, annotation processor, and kotlin compiler plugin.
