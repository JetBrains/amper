General:

```yaml
settings:
  kotlin:
    languageVersion: <enum/string> 
    apiVersion: <enum/string> 
    jvmTarget: <enum/string>
    allWarningsAsErrors: <bool>
    freeCompilerArgs: <list>
    suppressWarnings: <bool>
    verbose: <bool>
    linkerOptions:  <list>
    debug: <bool>
    progressiveMode: <bool>
    languageFeatures: <list>
    optIns: <list>
    
  java:
    target: <enum>
    source: <enum>

  android:
    compileSdkVersion: <enum>
    minSdk: <enum>
    minSdkPreview: <enum>
    maxSdk: <enum>
    targetSdk: <enum>
    applicationId: <string>
    namespace: <string>

  compose:
    enabled: <bool>
```

Testing:

```yaml
test-settings:
  junit:
    forkEvery: <bool>
    enableAssertions: <bool>
    exclude: <string>
    filter: <string>
    jvmArgs: <list>
```