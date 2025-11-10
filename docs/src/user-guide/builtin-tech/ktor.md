# Ktor

[Ktor](https://ktor.io/) is a Kotlin framework for building asynchronous server-side and client-side applications.

To enable Ktor support, add the following to the `module.yaml` file:
```yaml
settings:
  ktor: enabled
```

Setting `ktor: enabled` performs the following actions:

* Applies Ktor BOM
* Contributes Ktor-related entries to a built-in library catalog
* Adds the `io.ktor.development=true` system property when running the app with `amper run`

Examples of Ktor projects:

* [ktor-simplest-sample]({{ examples_base_url }}/ktor-simplest-sample)

You can also customize the version of the Ktor libraries using the full form of the configuration:
```yaml
settings:
  ktor:
    enabled: true
    version: 3.3.2
```

If you don't want the Ktor BOM to be applied, you can disable it explicitly:
```yaml
settings:
  ktor:
    enabled: true
    applyBom: false
```