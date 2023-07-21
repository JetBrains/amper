General:

```yaml
settings:
  kotlin:
    languageVersion: <enum/string> 
    apiVersion: <enum/string> 
    allWarningsAsErrors: <bool>
    suppressWarnings: <bool>
    verbose: <bool>
    debug: <bool>
    progressiveMode: <bool>
    languageFeatures: <list>
    optIns: <list>
    freeCompilerArgs: <list>
    
  java:
    mainClass: <string>
    packagePrefix: <string>
    target: <enum>
    source: <enum>

  android:
    compileSdkVersion: <string>
    minSdk: <int/string>
    minSdkPreview: <int/string>
    maxSdk: <int>
    targetSdk: <int/string>
    applicationId: <string>
    namespace: <string>

  compose:
    enabled: <bool>
```

Testing:

```yaml
test-settings:
  junit:
    platformEnabled: <bool>
```
