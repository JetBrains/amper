# Plain example --------------------------------------
```toml
# ./build my-project --target=ios-x64

[kotlin]
version = "1.8.10

[kotlin.compilerPlugins]
"org.jetbrains.compose.compiler:compiler" = "42"
"org.jetbrains.kotlin:kotlin-allopen" = { version = "1.8.0", annotation = "com.my.Annotation" }

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
# Plain example end --------------------------------------


# Tree example --------------------------------------
```yaml
# ./build my-project --build-for=android-aarm64

build-for:
    - android:
        min-sdk-version: 42
        target-sdk-version: 42
        manifest: manifest.xml
        build-for:
            - aarm64:
                app-icon: myAarm64Icon.png
            - x86
    - ios:
      app-con: myIcon.png
      sdk-version: 13.1
      kotlin:
        compiler-settings:
          extra-compiler-args: ["--linker-option=some"] 
      build-for:
            - simulator:
                - aarm64:
                - x86-64:
            - device

# by default plugins are applied to all profiles
kotlin:
  version: 1.8.0
  compiler-settings:
    plugins:
      - coordinates: org.jetbrains.kotlin:kotlinx-serialization:1.8.0
      - coordinates: org.jetbrains.kotlin:kotlin-allopen:1.8.0
        annotation: com.my.Annotation

            
dependencies:
    *: # common for all targets
        - org.some.common.koltin:dependency:1.2.3
        - org.ios.android.shared.koltin:dependency:1.2.3
    android:
        - org.java:dependency:1.2.3  # Java dependency
        - org.kotlin:dependency:1.2.3 # Kotlin dependency
    ios:
        *:
            - org.koltin:dependency:1.2.3
            - type: cocoa
              coordinates: AFNetworking:1.2
              
```
# Tree example end --------------------------------------


Multiplatform targets will be existed in some way
Target-specific dependencies
Native package managers integration
