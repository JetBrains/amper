 -- workspace.toml file
```toml

```

 -- Compiler plugins section
```toml
[kotlin.compilerPlugins]
"org.jetbrains.compose.compiler:compiler" = "42"
"org.jetbrains.kotlin:kotlin-allopen" = { version = "1.8.0", annotation = "com.my.Annotation" }
```

A case, when a library can work with "any" kotlin platforms that kotlin compilers support now 
or in will support in the future.
* this use-case is currently not supported by the kotlin compiler.

-- cross-platform-lib/build.toml file
```toml

# Q: can/should one module have several artifacts, or one artifact per module?
#    - one module usually have at least 2 artifacts: production and tests
#    - when one module have several artifcats, user will need to specify artifact-specific
#      dependencies somehow (e.g. for tests). It might complicates the configuration

[module]
default-platforms = [ "1", "2", "3" ]

# ----


[dependencies]
"org.some.common.koltin:dependency" = "1.2.3" # Common Kotlin dependency

```

A case, when a library is build for specified platforms in build file.
 -- multi-platform-lib/build.toml file
```toml
# ./build my-project --target=ios-x64
[kotlin]
version = "1.8.10

[target.desktop]

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



----------------------------- app + lib ---------------------------------------------

-- app/build.toml

```toml

# Consensus: Module has list of supported platforms that limit it's applicability/reusability in other modules, 
# but extends the list of available configuration seetings in its build file. 

supportedPlatforms = [ "ios", "android" ]
supportedPlatforms = "any"

[dependencies]
lib = "local"


```


-- lib/build.toml

```toml



```