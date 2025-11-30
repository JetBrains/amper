# Product types

Each module has a product type defined by the `product` field in `module.yaml`, indicating what is created when building
this module.

This section contains subsections describing the specific aspects of each product type.
Here is the list of supported product types:

| Product type(s)                                      | Description                                                                                    |
|------------------------------------------------------|------------------------------------------------------------------------------------------------|
| [`jvm/lib`](jvm-lib.md)                              | A JVM library                                                                                  |
| [`jvm/app`](jvm-app.md)                              | A JVM console or desktop application                                                           |
| [`lib`](kmp-lib.md)                                  | A Kotlin Multiplatform library                                                                 |
| [`windows/app`](native-app.md)                       | A Kotlin/Native mingw-w64 application                                                          |
| [`linux/app`](native-app.md)                         | A Kotlin/Native Linux application                                                              |
| [`macos/app`](native-app.md)                         | A Kotlin/Native macOS application                                                              |
| [`android/app`](android-app.md)                      | An Android application                                                                         |
| [`ios/app`](ios-app.md)                              | An iOS application                                                                             |
| [`js/app`](js-app.md)                                | A JavaScript application using the Kotlin/JS technology                                        |
| [`wasmJs/app`](wasm-app.md)                          | A WebAssembly application with browser APIs                                                    |
| [`wasmWasi/app`](wasm-app.md)                        | A WebAssembly application with WASI APIs                                                       |
| [`jvm/amper-plugin`](../plugins/topics/structure.md) | An [Amper plugin](../plugins/overview.md), to extend the Amper build with custom functionality |
