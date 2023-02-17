should product type (library vs binary) be a property of a module or of a specific target

Build variability via Tags: should debug/release build type be a tag option?

How to create JVM-only library, which is expected to be a jar
 - Alexey: there is no such things as multiplatform KLib.
 - No possible to so build publish common library for all platforms (e.g. math)
 - need to build for each platform, probably on different CI nodes, which means publishing in Maven is problematic (multi-step)  
 - ==== HUGE problem for library developers!!!!!!!!!!!!!


defining sub-modules seems to still be heavy:
- versioning, layout, where to declare them?

- When and how is lock file should be updated (it should not happen unexpectedly, and should not change on every build tool modification)
   locking could be in several modes : restrictive and strict
  

- How to configure integration tests
- How to manage resources (E.g. android)

----------
chat notes
```
Alexander Tsarev11:38
util@android+ios-x64
util@androidx64+iosx64
util@android+ios-x64+x32

You11:44
util@x64+android+ios+x32+debug

Alexander Tsarev11:51
androidNativeArm32ApiElements
androidNativeArm64ApiElements
debugAndroidNativeArm64ApiElements

Alexander Tsarev12:02
platform = [ "ios-arm64", "ios-armv7" ]

Alexander Tsarev12:04
[target.ios] # valid
[target.x64] # not valid

```