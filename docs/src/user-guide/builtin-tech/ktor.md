# Ktor

To enable Ktor support, add the following to the `module.yaml` file:

```yaml

settings:
  ktor: enabled
```

Setting `ktor: enabled` performs the following actions:

*   Applies Ktor BOM
*   Contributes Ktor-related entries to a built-in library catalog
*   Adds default JVM arguments when running the app

Examples of Ktor projects:

* [ktor-simplest-sample]({{ examples_base_url }}/ktor-simplest-sample)
