# 0.3.0

* Standalone Amper CLI. See the [usage instructions](docs/Usage.md#using-the-standalone-amper-from-command-line). 

## Breaking changes

Amper now uses the `release` javac option instead of `target` and `source` to compile the Java code. 
This version is also passed to the Kotlin compiler on the JVM platform.   

You need to update your configuration files. If you have the following settings:

```yaml
settings:
  java:
    source: 17
  jvm:
    target: 17
```

Remove `java:source:` and change `jvm:target:` to `jvm:release:`: 
```yaml
settings:
  jvm:
    release: 17
```

See the [DSL reference](docs/DSLReference.md#jvm) for details.