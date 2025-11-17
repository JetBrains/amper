```shell
my-module/
├─ resources/       # common resources, used in all targets
├─ resources@ios/   # resources that are only available to the iOS code
├─ resources@jvm/   # resources that are only available to the JVM code
├─ src/             # common code, compiled for all targets
│  ├─ main.kt
│  ╰─ util.kt # (1)!
├─ src@native/      # code to be compiled for all native targets
├─ src@apple/       # code to be compiled for all Apple targets
├─ src@ios/         # code to be compiled only for iOS targets
│  ╰─ util.kt # (2)!
├─ src@jvm/         # code to be compiled only for JVM targets
│  ├─ util.kt
│  ╰─ MyClass.java # (3)!
├─ test/            # common tests, compiled for all targets
│  ╰─ MainTest.kt   
├─ test@ios/        # tests that are only run on iOS simulator
│  ╰─ SomeIosTest.kt
├─ test@jvm/        # tests that are only run on JVM
│  ╰─ SomeJvmTest.kt
├─ testResources/       # common test resources, used in all targets
├─ testResources@ios/   # test resources that are only available to the iOS code
├─ testResources@jvm/   # test resources that are only available to the JVM code
╰─ module.yaml
```

1. This file may define `expect` declarations to be implemented differently on different platforms.
2. This file defines the `actual` implementations corresponding to the `expect` declarations from `src`.
3. It's ok to have Java sources in JVM-only source directories.