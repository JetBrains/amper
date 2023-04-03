# Core concepts
Target is an analysis unit (aka IJ module) that is passed to compiler as a whole.
So, target must have one main source set.

Module is a target or aggregation of targets (see questions).
Module is always defined by build file. Module must have one main build file
and maybe some specialized build files (see questions).

Every target can alter common module parameters and can have some platform specific parameters also.

Targets are linked to each other with "is compatible" relation.

Essentially target is a string or collection of strings (if some code should be available 
for ios and android simultaneously, for example).

# Core questions
 -- Questions about core model concepts, like what is module, what is target, etc.
1. Module is an analysis unit or aggregation of analysis units? (aka targets, or IJ modules)
   Question can be understood as "Is module a JB module or Gradle module?".
   The conceptual difference is whether multiple source sets can reside in single module, thus requiring to have 
   different analysis configurations.
   In a nutshell, answer to this question is a fork between (module == target) and (module == aggregation of targets).
   Maybe answer should be more complex to add some "fragments/variants" layer like is KMPP model.
2. Independent of 1 question - can we have "intersected" targets? Like, "target" that contain multiple targets.
3. Do we need a way to declare specific sources for target with specific parameters? 
   Like, tests or integration tests. Should this additional sources have same amount of parametrization?
   This question is a core question, because its answer will clarify if "sources" actually exist.
4. How can we implement, for example, fat jar production?
5. Should we provide some API for external tools?

# Features questions
 -- Questions of some abstract features that should or should not be available, 
 -- like plugins, repositories, java modules, etc.
1. Do we need to add some generic plugins, that will allow to alter build behaviour? (Not just templating)
   Here I mean some heavy altering, like changing "build actions" or "global model" completely, whatever it means.
2. Do we need plugins, that can add different repositories/package managers/toolchains/codegen/etc? (More specific functionality)
   Here I mean plugins, that can add some functionality for predefined extension points, very limited.
3. Do we need a way to inherit one configuration from another? If yes, what should be limits for this inheritance?
4. Do we need a way to define generic variables? Substitution, string interpolation, etc?
5. Do we need some conditional parameters? Based on what? How they will interact with targets? 
6. Do we need to add java modules section for jvm targets?
7. Do we need a configuration file (that also can be located at USER home)?
8. Do we need a root build file, like build.root.toml or workspace.toml? Shall it be in same language?
9. Do we need synthetic targets? Or will target lists be enough?
10. Continuing question 9 - do we need target attributes, or targets will be enough?
11. Do we need to provide a way of specifying "is compatible" relation between targets explicitly? In workspace file? Anywhere else?
12. Should we support multiple types of publication?
13. Should we support "child" build files, like `build.ios.toml`?
14. Should we support filesystem conventions on target sources and build file locations structure? Like `module/ios` is always ios specific, etc.
15. Should we support git dependencies?
16. Should we support compiler plugins in build files?
17. Should we support some toolchains?
18. Do we need to have version catalogs? Where should they locate?
19. Do we need some git distributed defaults? Can be relevant to 3 question.
20. Do we need to configure repositories in workspace, build file or somewhere else?
21. Should we support auto discover of modules or force to specify them? Maybe a flag to switch mode?
22. Do we need a way to change sources layout? Just some predefined modes? Plugin? Fully configurable section?
23. Do we need to be able to build effective build files? 
24. Do we need some scopes for dependencies? If yes, how should they look?
25. Should we support environment variables? Interop with common parameters?
26. How to create file specific IJ modules while we are facade mode for Gradle?
27. How to reference some variables? How to define them? (For example for repo secrets)
28. How to inherit one config from another? Do we need inheritance?
29. Should we discover test modules (or tests sources) by convention?
30. Should we support general build cycle triggers? (beforeBuild, afterBuild, beforeAllBuild, afterAllBuild)?
31. Should we have cpecial conventions for single-target modules?

# Detail questions
 -- Questions about some specific details, like compiler options or single module parameters.
1. Should there be a special dependency type to ease target specific dependencies?
   Like `#module2-ios [target.ios.dependency] module1-ios = "local"` will also create dependency from
   "module2-common" to "module1-common", if there is `#module2-ios [dependencies] module2-common = "linked"`.
   Or too much magic?
2. Should there be some "application type"?
3. Should we have def file support (C, Objective-C integration) or only attributes in toml?
4. Should module coordinates also mean that it can be published?
5. Should we support shortcuts like this: `[target.ios.dependencies."package(cocoa)"]`
6. Is there a need for dedicated `[kotlin]` parameters block?
7. Do we need to specify publication info in build scripts, workspace or passed from outside?
8. Should we force dependencies list in one place, or it can be smeared across build files?
9. Do we need some dedicated build type section with specific parameters?
10. Do we need some dedicated test section?