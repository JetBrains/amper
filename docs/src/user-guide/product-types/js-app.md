# :material-language-javascript: Kotlin/JS application

Use the `js/app` product type in a module to build a JavaScript application using the 
[Kotlin/JS](https://kotlinlang.org/docs/js-overview.html) technology.
These applications can be run in browsers or Node.js.

!!! warning "Incomplete preview"

    The support for this product type is currently in an incomplete preview state.

    For example, running a JavaScript application is not supported out of the box at the moment like other application 
    types, and needs some manual work (see the [Running your application](#running-your-application)).

    We're eager to hear more about your use cases and how we can improve this experience!
    Please let us know in a [:jetbrains-youtrack: YouTrack](https://youtrack.jetbrains.com/issues/AMPER) issue, or in
    our [:material-slack: Slack channel](https://kotlinlang.slack.com/archives/C062WG3A7T8).

!!! tip "Using IntelliJ IDEA?"

    Make sure to install the 
    [:jetbrains-kotlin-multiplatform: Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)
    to get proper support for Kotlin/JS.

## Module layout

Here is an overview of the module layout for a JavaScript application:

```
my-module/
├─ src/
│  ├─ main.kt
│  ╰─ Util.kt
├─ test/
│  ╰─ UtilTest.kt
╰─ module.yaml
```

## Entry point

The entry point of a Kotlin/JS application is a top-level `main` function in the `src` folder.

Multiple `main` functions are not supported. If you have multiple main functions, the one chosen by the compiler as 
an entry point is unspecified.

## Packaging

You can use the `build` command to compile your code to a JavaScript module file (`.mjs`) for your application.
It cannot be run directly by Amper, but you can run it using Node.js or in a browser via an HTML page.

The `.mjs` file is produced in the `build/tasks/_<module-name>_linkJs` folder at the moment, but this is subject to 
change.

There is no extra packaging facilities at the moment, and the `package` command is not supported for this product type.

## Running your application

!!! warning "Kotlin/JS applications cannot be run directly by Amper at the moment."

To run your application, you need to:

1. Install a JavaScript runtime (e.g., Node.js or a browser)
2. Build your module with `./amper build`
3. Run the `.mjs` file produced by your module using your JavaScript runtime. 
   See the [Packaging](#packaging) section above to know where this file is located.
