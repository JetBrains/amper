# Core concepts

- Should product type (library vs binary) be a property of a module or of a specific target?
  If it's a property of a module, then how to define test additional executable targets?

- Should build type(Debug/Release) or flavor also be tags along with platform (os and arch)?

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

- What makes defining a new module a simple and lightweight process?
    - The need to define version, coordinates, and anything else just increases heaviness

# Locking

- Can we represent toolchain versions (deft, jdk, kotlin) in lock files as well?
- When and how is lock file should be updated?
    - It should not happen unexpectedly, and should not change on every build file modification.
    - Locking could be in several modes : restrictive and strict

# Testing

- How to configure integration tests

# Build pipeline

- How to manage resources (E.g. android)

# Aleksandr.Tsarev questions

-- TODO