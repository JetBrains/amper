# :simple-webassembly: Kotlin/Wasm application

Use the `wasmJs/app` or `wasmWasi/app` product type in a module to build a WebAssembly application using the 
[Kotlin/Wasm](https://kotlinlang.org/docs/wasm-overview.html) technology.
These applications can be run in browsers or Node.js.

!!! warning "Incomplete preview"

    The support for this product type is currently in an incomplete preview state.

    For example, running a WebAssembly application is not supported out of the box at the moment like other application 
    types, and needs some manual work (see the [Running your application](#running-your-application)).

    We're eager to hear more about your use cases and how we can improve this experience!
    Please let us know in a [:jetbrains-youtrack: YouTrack](https://youtrack.jetbrains.com/issues/AMPER) issue, or in
    our [:material-slack: Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8).

!!! tip "Using IntelliJ IDEA?"

    Make sure to install the 
    [:jetbrains-kotlin-multiplatform: Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)
    to get proper support for Kotlin/Wasm.

## Module layout

Here is an overview of the module layout for a Kotlin/Wasm application:

```shell
my-module/
├─ src/
│  ├─ main.kt
│  ╰─ Util.kt
├─ test/
│  ╰─ UtilTest.kt
╰─ module.yaml
```

## Entry point

The entry point of a Kotlin/Wasm application is a top-level `main` function in the `src` folder.

Multiple `main` functions are not supported. If you have multiple main functions, the one chosen by the compiler as 
an entry point is unspecified.

## Packaging

Using the `build` command compiles your code to WebAssembly (`.wasm` file) and generate a JavaScript wrapper file 
(`.mjs`) to load it.

These files are produced in the `build/tasks/_<module-name>_linkWasmJs` (for `wasm-js/app`) or
`build/tasks/_<module-name>_linkWasmWasi` (for `wasm-wasi/app`) folder at the moment, but this is subject to change.

There is no extra packaging facilities at the moment, and the `package` command is not supported for this product type.

## Running your application

!!! warning "Kotlin/Wasm applications cannot be run directly by Amper at the moment."

To run your application, you need to:

1. Install a JavaScript runtime that supports WebAssembly (e.g., Node.js, D8, a browser, ...).
2. Build your module with `./amper build`
3. Using your JS runtime, run the `.mjs` wrapper file that calls the `.wasm` code produced by your module.
   See the [Packaging](#packaging) section above to know where this file is located.
