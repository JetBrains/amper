# Native Interoperability with C

Amper provides a powerful and flexible way to interoperate with C and Objective-C code in your native projects using the Kotlin/Native `cinterop` tool. This guide explains how to configure and use this feature.

## How it Works

The `cinterop` process in Amper allows you to create Kotlin bindings for C libraries by defining modules in your `module.yaml` file. 
Each module references a `.def` file that describes the C API and can include additional compiler and linker options.

## Configuration

The `cinterop` settings are configured under `settings.native.cinterop` in your `module.yaml`.

```yaml
settings:
  native:
    cinterop:
      <module-name>:
        defFile: <path-to-def-file>
        packageName: <package-name>
        compilerOpts:
          - <flag1>
        linkerOpts:
          - <source-file.c>
          - <linker-flag>
```

### API Details

| Option | Description |
| :--- | :--- |
| `defFile` | Path to the Kotlin/Native definition (`.def`) file. **Optional** if the module is discovered by convention. |
| `packageName` | The Kotlin package name for the generated bindings. |
| `compilerOpts`| A list of flags to be passed to the C compiler (e.g., `-I/path/to/includes`). |
| `linkerOpts` | A list of C/C++ source files (`.c`, `.cpp`) to be compiled and/or flags to be passed to the linker (e.g., `-lm`). |

### Example

To create a cinterop module, define it in your module.yaml with the path to your .def file:

**`module.yaml`:**
```yaml
settings:
  native:
    cinterop:
      # Define a completely new 'bar' module
      bar:
        defFile: external/libbar/api.def
        packageName: com.example.bar
        linkerOpts:
          - external/libbar/bar.c