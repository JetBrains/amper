
- misc settings (format version etc.)
- settings for tools
- list of dependencies
- list of targets
  - type (library or binary. library can be used as dependency, binary is not)   
  
```
# 
# misc settings:
# 
version = 1.0

# 
# default settings for tools
# 
[kotlin]
version = 1.7

[java]
version = 17

# 
# list of dependencies
# 
[dependencies] #common

[dependencies.android] #android specific (if there is such target) 

[dependencies.ios+armv7] #only for iOS and armv7 (if there is such target) 

# 
# list of targets
# 
[target]
platform = ios # can be ios, watchos, android, jvm, linux, wasm, etc.
arch = [armv7] # depends on the platform
type = application # depends on the platform, can be application, library, framework, etc,  

[target]
platform = jvm
type = application
packaging = jar
  [java]
  version = 1.8

```

```
Module {
  version : String
  toolDefaults : Map<Tool, List<Option>>
  dependencies : Map<Dependency, List<Constraint>>
  targets : List<Target> 
}

Kotlin : Tool {
}

Java : Tool {
}

Target {
   Platform
   
}

Platform {
  supportedArchitectures : List<>
  supportedTargetTypes : List<>
}

```