```shell
my-module/
├─ resources/ # (1)!
│  ╰─ logback.xml # (2)!
├─ src/
│  ├─ main.kt
│  ╰─ Util.java # (3)!
├─ test/
│  ╰─ MainTest.java # (4)!
│  ╰─ UtilTest.kt
├─ testResources/
│  ╰─ logback-test.xml # (5)!
╰─ module.yaml
```

1. Resources placed here are copied into the resulting jar.
2. This is just an example resource and can be omitted.
3. You can mix Kotlin and Java source files in a single module, all in the `src` folder.
4. You can test Java code with Kotlin tests or Kotlin code with Java tests.
5. This is just an example resource and can be omitted.
