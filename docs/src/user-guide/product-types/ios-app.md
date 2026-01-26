---
description: Learn how to use the `ios/app` product type in a module to build an iOS application.
---
# :simple-apple: iOS application

Use the `ios/app` product type in a module to build an iOS application.

!!! tip "Using IntelliJ IDEA?"

    Make sure to install the 
    [:jetbrains-kotlin-multiplatform: Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)
    to get proper support for Kotlin/Native and iOS.

## Module layout

Here is an overview of the module layout for an iOS application:

--8<-- "includes/module-layouts/ios-app.md"

## Entry point

For iOS applications, the entry point is expected to be a `@main` struct in any Swift file in the `src` folder.

<div class="grid" markdown>
``` hl_lines="2"
├─ src/
│  ├─ main.swift
│  ╰─ ...
├─ module.yaml
╰─ module.xcodeproj
```

```swift title="src/main.swift"
...
@main
struct iosApp: App {
   ...
}
```
</div>

This is not customizable at the moment.

## Xcode Project

Currently, an Xcode project is required to build an iOS application in Amper.
It has to be named `module.xcodeproj` and located in the `ios/app` module root directory.

Normally, when the Amper project is created via `amper init` or via the IDE's Wizard, the appropriate Xcode project is
already there. This is currently the recommended way of creating projects that have an iOS app module.

However, if the Amper project is created from scratch, the default buildable Xcode project will be created automatically
after the first project build.
This project can later be customized and checked into a VCS.

If you want to migrate an existing Xcode project so it has Amper support, you must manually ensure that:

1. it is named `module.xcodeproj` and is located in the root of the `ios/app` module
2. it has a single iOS application target
3. the target has `Debug` & `Release` build configurations, each containing `AMPER_WRAPPER_PATH = <relative path to amper wrapper script>`.
   The path is relative to the Amper module root.
4. the target has a script build phase called `Build Kotlin with Amper` with the code:
   ```bash
    # !AMPER KMP INTEGRATION STEP!
    # This script is managed by Amper, do not edit manually!
    "${AMPER_WRAPPER_PATH}" tool xcode-integration
   ```
5. The _Framework Search Paths_ (`FRAMEWORK_SEARCH_PATHS`) option contains the `$(TARGET_BUILD_DIR)/AmperFrameworks` value

Changes to the Xcode project that do not break these requirements are allowed.

So the iOS app module layout looks like this:
```
├─ src/
│  ├─ KotlinCode.kt      # optional, if all the code is in the libraries
│  ├─ EntryPoint.swift
│  ├─ Info.plist
├─ module.yaml           # ios/app
╰─ module.xcodeproj      # xcode project
```

!!! tip

    The Xcode project can be built normally from the Xcode IDE, if needed.

## Swift support

!!! info

    Swift sources are only fully supported in the `src` directory of the `ios/app` module.

While swift sources are, strictly speaking, managed by the Xcode project and, as such,
can reside in arbitrary locations, it's not recommended to have them anywhere outside the `src` directory - the tooling
might not work correctly.

To use Kotlin code from Swift, one must import the `KotlinModules` framework.
This framework is built from:

1. the code inside the `ios/app` module itself
2. the modules that `ios/app` module depends on (e.g. `- ../shared`)
3. all the external dependencies, transitively

!!! note

    All declarations from the source Kotlin code are accessible to Swift, but external dependencies are not.

