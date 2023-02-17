# Misc

- What makes defining a new module a simple and lightweight process?
  - The need to define version, coordinates, and anything else just increases heaviness
  - Maybe by default empty module.toml should define a single library target?
-
- Are platform-dependent settings module-specific or target-specific?
  Why it can be both? Module have default settings for tools for platform e.g. using tags: `[koltin.jvm+ios]` Though,
  it can be confusing see `kotlin`, `[koltin.android]` `[target.koltin]`, `[android.koltin]`

- What's the semantic link between 'tag' on dependency and a file and `platform = "ios"` in target definition?
  Is it 'target define the set of constraints, like platform or arch' which are applied to files and settings?

- Should build type(Debug/Release) or flavor also be tags along with platform (os and arch)?

# Targets

- Should product type (library vs binary) be a property of a module or of a specific target?
  If it's a property of a module, then how to define test additional executable targets?
-
- Can a single module several targets?
  Can we have several libraries, or only one? (e.g in case of libraries for several platform)
  Can we have several binaries? (e.g. app+testing)
  Can we have library and binary together? (e.g. lib+testing)

- How to configure a library target for several platforms?
  Should there be several targets (one per per-platform) or a single target.
  If it's single, how to specify architectures?

- How to define/create a library for pure JVM project, which is expected to be a jar?
  Is it still a library or a binary? Is it a KMP library with only one platform, or a native JVM binary.
  - There is no such things as multiplatform KLib.
    -No possible to so build publish common library for all platforms (e.g. math)
    -need to build for each platform, probably on different CI nodes, which means publishing in Maven is problematic (
    multi-step)
  - Problematic case: a JVM-only  *.jar library )
    - idea (from Max): such reusable library can be 'instantiated' into jar when we build the main JVM module

- Can a library also have different packaging type (KLIB, jar) and dependency mechanism could in future be extended
  to support such dependencies directly?

# Schema

- We might want to have model versioning in order to offer some automated assistance,
  like compatibility and schema checks, migration etc.
  It can be either schema version or a required build tool version, or both.

# Defaults

- Default 'JVM binary' target (when no other is defined) can be confusing.
  It also means that to create a library module one need to explicitly declare a library target

- Different defaults for binary targets (with is jvm) and library targets (with is multi-platform) could be confusing.
  Should we by default always target JVM? Or maybe it's not a problem at all?

- Should the native targets have explicitly specified platform, or can we have 'current platform' as default?

# Dependencies

- Is it OK for the IDE support that the presence of koltin-stdlib can only be determined on the runtime?
  - How can it be determined it on read-time?
  - Or maybe it ok, since we anyway need to resolve and download dependencies
  - And once the dependency in the lock file, it can be done in read-time
- Should dependency always explicitly specify the source if it's not default Maven cenral? (to avoid attack)
- Should dependency spec also include hash sum to avoid main-in-the-middle attack?
  Probably it won't help since there are also transitive deps

# Coordinates

- Should we follow Maven schema or invent a new?
  E.g. replace group/name with a single FQN e.g. `com.my.group.my-id:version`
- Are library coordinates part of a target declaration or a module declaration?
- Do we need coordinates for local dependencies?
  - How do we refer to a local dependency with coordinates? What should be 'version'?
  - Can be useful for partial-checkout/opening, but versioning is unclear.
  - Coordinates also simplifies mental model and tooling (no need to update paths then moving/refactoring)
  - need to consider CI, remote build/cache etc.


- What about source-dependency coordinates? (could be commit hash?)

# Publishing

- How are platform-specific native dependencies should be distributed? (e.g. native dlls that are required for a lin)

# Toolchain

- will the required toolchain (like JDK) be always downloaded or also can be found in local installations?
  Maybe a user can override the used tools (e.g. path to JDK) in their .workspace.user.toml-like file?

# Locking

- Can we represent toolchain versions (deft, jdk, kotlin) in lock files as well?
- When and how is lock file should be updated?
    - It should not happen unexpectedly, and should not change on every build file modification.
    - Locking could be in several modes : restrictive and strict

# Testing

- How to configure integration tests

# Build pipeline
                                                   
- How do we do cross-compilation for native binary targets, multiplatform libraries?
- How to manage resources (E.g. android)

# IDE support
- Would it be possible/scalable to determine the list of files that belong to a target by their @tags?

# Aleksandr.Tsarev questions

-- TODO