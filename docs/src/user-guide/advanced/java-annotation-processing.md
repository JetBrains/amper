# Java annotation processing

To add java annotation processors to your module, add their maven coordinates to the
`settings.java.annotationProcessing.processors` list:

```yaml
settings:
  java:
    annotationProcessing:
      processors:
        - org.mapstruct:mapstruct-processor:1.6.3
```

This option is only available for java or android modules (it's a platform-specific).

As with KSP, it's possible to reference a local Amper module as a processor. See the
[KSP section](ksp.md#using-your-own-local-ksp-processor) for more information. Using library catalog entry is also supported.

Some annotation processors can be customized by passing options.
You can pass these options using the `processorOptions` map:

```yaml
settings:
  java:
    annotationProcessing:
      processors:
        - $libs.auto.service # using catalog reference 
      processorOptions:
        debug: true
```
