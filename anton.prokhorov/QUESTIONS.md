# Core concepts
???

# Aleksandr.Tsarev questions
 - How to configure testing? Specific dependencies? Etc?
 
 - Should we have dependency "aliases" implemented in some way?
   Like `external-references`.
 - What should be dependency scope for non jvm/kotlin dependency?
 - Should we have additional-capabilities for platform declaration?
 - What are `ertifacts` in platform declaration?
 - Where to place platform specific attributes? Do we need to place them in platforms tree only?
   Here I mean we can separate platform declarations from their specific settings to achieve "plain" sight.
 - Why compiler plugins are located under `compiler-setting` section?
 - Can we place user declared variables under special section, like in maven?
 - Do we need to specify publish info in build files? Can we pass it through 
   CLI, leaving only repo info inside build file?
 - Do we need separate `type` field for dependency? Can we just use it like prefix? 
   (Like `maven/some:dependency:1.0.0.` or `cocoa:some-pod:1.0.0`)
 - Do we need to separate env variables from user defined variables in some way?
 - What platform should user specify when he has only common part, but wants multiplatform library to be generated?
 - Should we have inheritance enabled by default?
 - `value:` attribute in dependencies list seems confusing - seems that it is pure yaml limitation
 - What is source `type`?
 - Can I specify `common` platform for sources?
 - Can I place specific language or compiler options in `platforms` section?
 - How compose will leak from common dependency to ios dependency with compose-ios component included?
 - Should we omit "ios" in "src/ios/kotlin" if single platform specified?
 - Why GAV not in module section?
 - What should root file do? Should it be inherited automatically?
 - How can I run pre-built/post-build (Example: I want to generate documentation, SwaggerAPI, call cmake, etc.)?
 - Should we enable module files autodiscovery?
 - Should we allow string in dependencies? In other places? Should we prefer no string approach?
 - How to manage project-wide settings? Repos, toolchains, etc.? Place in root and inherit? In case of conflicts?
 - How to specify secrets?
 - Why kotlin api-version is placed in common module wide setting? Can it be platform specific?
 - How can I specify where to publish my artifact?
 - Can I set some "run specific" parameters, like -Xmx or smth?
 - How can I manage scopes and transitive versions?
 - Maybe we need to force placing version somewhere?
 - How to mark generated sources?

# Aleksandr.Tsarev confusions
 - I need to specify dependency platform in single platform module.
 - When I can specify any variate attribute and reference it, so it can be chaos.
   Like how should I define version?
   Like this:
   ```yaml
   my-library: 123
   ```
   Or like this:
   ```yaml
   my-library:
      version: 123 
   ```
   Or even this:
   ```yaml
   versions:
      library1: 123
      library2: 123
   ```
 - Also, a bit confusing where to place such versions. It seems to me that if there is a single place
   (call it version catalog, or version file, whatever) it would be simpler to search and place versions.