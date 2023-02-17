Key properties
- module - logical code organization that produces either reusable library or external binary(ies).
- files + dependencies -> target(with platform/arch constraints) -> steps+tools -> product(s)
- KMP-organization via compatibility tags (platform/arch) instead of hierarchy
- flat or structured directory layout, via tags
- library and binary target types 
  - library - reusable directly in other modules
  - binary - non-reusable directly, e.g. executable, ios framework.
  - Problematic case: a JVM-only  *.jar library (is it a KMP library with only one platform, or a native JVM binary)
    - idea (from Max): such reusable library can be 'instantiated' into jar when we build the main JVM module   
  

   
Module structure:
```toml
# misc settings:
version = 1.0
type = "binary" # or "library"

# tools default for all targets
[kotlin]
version = 1.7

[java]
version = 17

# list of targets
[[target]]
platform = "ios" # can be ios, watchos, android, jvm, linux, wasm, etc.
arch = ["armv7"] # depends on the platform

[[target]]
platform = "jvm"
packaging = "jar"
[target.java] #target-specific tool settings
  version = 1.8

[dependencies] #common dependencies

[dependencies.android] # android-specific (if there is such target) 

[dependencies."ios+armv7"] # only for iOS and armv7 (if there is such target) 

```

