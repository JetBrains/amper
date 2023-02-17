# Key concepts

- Module - logical code organization that produces either a library or binary(ies)
- Library and Binary target types
  - library - artifact directly reusable in other Deft modules
  - Binary - non-reusable artifact, e.g. executable, ios framework.
- Target - defines an artifact that needs to be built from the Module's sources. 
  A target specifies the destination platform (os/arch).
  - Depending on target's platform, and type (binary or library), the necessary toolchain and build steps are selected.
  - List of files to include in the target are defined by the platform and the toolchain.
- Multiplatform variants are defined via tags (os/arch) instead of hierarchy, tags can be on dependencies, toolchain settings and files.
- File structure can be either flat or structured directory layout, via tags

Basic module file:
```toml
# misc settings:
version = 1.0

# toolchain settings for all targets
[kotlin]
version = 1.7

[java]
version = 17

[kotlin.android] #koltin toolchain settings for all Android targets 
version = 1.8



# list of targets
[[target]]
platform = "ios" # can be ios, watchos, android, jvm, linux, wasm, etc.
arch = ["armv7"] # depends on the platform
type = "binary" # or "library"

[[target]]
platform = "jvm"
packaging = "jar"

[[target.java]] #target-specific tool settings
version = 1.8
type = "binary" # or "library"

[dependencies] #common dependencies
"kotlin:kotlin-logging"="1.2" # by default we use koltin/java distribution (Maven central)

[dependencies.android] # android-specific (if there is such target) 

[dependencies."ios+armv7"] # only for iOS and armv7 (if there is such target) 
"SPUtility" = { type = "cocoapods", name="SPUtility.framework" } # Cocoapods dependency

```

Flat directory structure:
```
-/
 |- src/              # pre-defined directory for sources
   |- file.kt          # common source
   |- file@jvm.java    # JVM-only source 
   |- file@jvm+ios.kt  # file will be include in JVM and ios targes only
   |- @ios/           # files to be included only in ios targets 
     |- file.kt       
 |- module.toml    
```

Basic directory structure:
```
-/
 |- src/              # pre-defined directory for sources
   |- foo.kt          # common source
   |- foo@jvm.java    # JVM-only source 
   |- foo@jvm+ios.kt  # file will be include in JVM and ios targes only
 |- module.toml    
```
