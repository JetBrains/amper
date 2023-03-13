 -- workspace.toml file
```toml

```

 -- Compiler plugins section
```toml
[kotlin.compilerPlugins]
"org.jetbrains.compose.compiler:compiler" = "42"
"org.jetbrains.kotlin:kotlin-allopen" = { version = "1.8.0", annotation = "com.my.Annotation" }
```

A case, when a library is build for specified platforms in build file.
 -- selected-platform-lib/build.toml file
```toml
[module]
supported-platforms = [ "androidArm", "iosx64" ]  # Q how to create a library for both ios-simulator and ios-device 

[kotlin]
version = "1.8.10

[target.android]
minSdkVersion = 42
targetSdkVersion = 42
manifest = "manifest.xml"

[target.ios]
appIcon = "myIcon.png"
sdkVersion = 13.1

[target.ios.kotlin]
extraCompilerArgs = [ "--linker-option=some" ]

[dependencies]
"org.some.common.koltin:dependency" = "1.2.3" # Common Kotlin dependency

[target.android.dependencies]
"org.some.java:dependency" = "1.2.3" # Java dependency
"org.some.koltin:dependency" = "1.2.3" # Kotlin dependency

[target.ios.dependencies]
"org.koltin:dependency" = "1.2.3" # Kotlin dependency
"AFNetworking" = { type = "cocoapods", version = "1.2" }

[target.ios_android.dependencies]
"org.ios.android.shared.koltin:dependency" = "1.2.3" # Kotlin dependency
```

 -- cross-platform-app/build.toml file
```toml

```

----------------------------- lib ---------------------------------------------

-- any-platform-lib/build.toml file

A case, when a library can work with "any" kotlin platforms that kotlin compilers support now
or in will support in the future.
this use-case is currently not supported by the kotlin compiler.

```toml

# Q: can/should one module have several artifacts, or one artifact per module?
#    - one module usually have at least 2 artifacts: production and tests
#    - when one module have several artifcats, user will need to specify artifact-specific
#      dependencies somehow (e.g. for tests). It might complicates the configuration

# [artifact.library]
[artifact] # artifacts - something that build tool produces from the sources (can be one or several files)
type = "library"   # something that can be reused or published (in scope of the build)?
                   # We cannot depend on a 'binary'

supported-platforms = "any"  # i-want-to-build-it-on-platform

# Q Is type specification necessary?
#   - tools settings might depend on type of the module/artifact (on Android and iOS)
#   - also type can affect, what user/build tool can do with the module
#   - depending on the type there can be required settings (e.g. app icon, manifest file, entry point) 

[kotlin]
# Some kotlin settings.

[dependencies]
"org.some.common.koltin:dependency" = "1.2.3" # Common Kotlin dependency
```

----------------------------- app + lib ---------------------------------------------

-- app/build.toml

```toml

[artifact] # artifacts - something that build tool produces from the sources (can be one or several files)
type = "binary"

# Consensus: artifact has a list of supported platforms that limits it's applicability/reusability in other modules, 
# but extends the list of available configuration seetings in its build file. 

supported-platforms = [ "ios", "android" ] # Try to build on desktop -> error.
# binary can have 'supported-platforms= any' - we cannot build binary for 'any platform' because there are too many versions of OS, arch etc.
# and there is no actual use case to build a binary for all possible platform

[platform.ios]

[platform.ios.dependecies]

# [dependencies]
[patform.all.dependencies]
any-platform-lib = "local"


```


-- lib/build.toml - a case when we want  

```toml

supportedPlatforms = [ "ios", "android" ]



```