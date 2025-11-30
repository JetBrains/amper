# :fontawesome-solid-microchip: Kotlin/Native application

Native applications are applications that run on the host operating system.
There are several product types for the supported target platforms:

- `linux/app` - a native Linux application
- `macos/app` - a native macOS application
- `windows/app` - a native Windows application

!!! tip "Using IntelliJ IDEA?"

    Make sure to install the 
    [:jetbrains-kotlin-multiplatform: Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)
    to get proper support for Kotlin/Native.

## Module layout

Here is an overview of the module layout for a Kotlin/Native application:

```shell
my-module/
├─ src/
│  ├─ main.kt #(1)!
│  ╰─ Util.kt
├─ test/
│  ╰─ UtilTest.kt
╰─ module.yaml
```

1. This is the conventional entry point location (contains a top-level `fun main()`). 
   See [Entry point](#entry-point) below.

## Entry point

By default, the entry point of a Kotlin native application is expected to be a top-level `main` function in a `main.kt`
file (case-insensitive) in the `src` folder.

If you don't want to follow this convention, you can specifty the fully qualified name of the entry point function
explicitly in the module settings:

```yaml
product: linux/app

settings:
  native:
    entryPoint: org.example.myapp.myMainFun # (1)!
```

1. The fully qualified name of the entry point function.

The entry point function must either have **no parameters or one `Array<String>` parameter** (representing the command
line arguments).

## Packaging

You can use the `build` command to compile and link a native executable for your application (a `.exe` on Windows, or 
`.kexe` otherwise).

There is no extra packaging facilities at the moment, and the `package` command is not supported for these native 
product types.