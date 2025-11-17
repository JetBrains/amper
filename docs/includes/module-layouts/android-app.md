```shell
my-android-app/
├─ assets/ # (1)!
├─ res/ # (2)!
│  ├─ drawable/
│  │  ╰─ graphic.png
│  ├─ layout/
│  │  ├─ main.xml
│  │  ╰─ info.xml
│  ╰─ ...
├─ resources/
├─ src/
│  ├─ AndroidManifest.xml # (3)!
│  ╰─ MainActivity.kt # (4)!
├─ test/
│  ╰─ MainTest.kt
├─ module.yaml
╰─ proguard-rules.pro # (5)!
```

1. `assets` and `res` are standard Android resource directories. See the [official Android docs](https://developer.android.com/guide/topics/resources/providing-resources).
2. `assets` and `res` are standard Android resource directories. See the [official Android docs](https://developer.android.com/guide/topics/resources/providing-resources).
3. The manifest file of your application.
4. An activity (screen) of your application.
5. Optional configuration for R8 code shrinking and obfuscation. See [code shrinking](/user-guide/builtin-tech/android.md#code-shrinking).