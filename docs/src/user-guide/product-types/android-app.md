---
description: Learn how to use the `android/app` product type in a module to build an Android application.
---
# :android-head-flat: Android application

Use the `android/app` product type in a module to build an Android application.

!!! tip "Using IntelliJ IDEA?"

    Make sure to install the [:android-head-flat: Android plugin](https://plugins.jetbrains.com/plugin/22989-android) to
    get proper support for Android-specific features.

## Module layout

Here is an overview of the module layout for an Android application:

--8<-- "includes/module-layouts/android-app.md"

## Entry point

The application's entry point is specified in the `AndroidManifest.xml` file according to the
[official Android documentation](https://developer.android.com/guide/topics/manifest/manifest-intro):

```xml title="src/AndroidManifest.xml"
<manifest ... >
  <application ... >
    <activity android:name="com.example.myapp.MainActivity" ... >
    </activity>
  </application>
</manifest>
```

You can run your application using the `./amper run` command.

??? tip "Run in IntelliJ IDEA"

    IntelliJ IDEA with the Amper plugin automatically detects the `android/app` product type and provides a run
    configuration for it:

    ![](../../images/ij-run-config-android.png)

## Packaging

You can use the `build` command to create an APK, or the `package` command to create an Android Application Bundle (AAB).

The `package` command will not only build the APK, but also minify/obfuscate it with ProGuard, and sign it.
See the dedicated [signing](#signing) and [code shrinking](#code-shrinking) sections to learn how to configure this.

### Code shrinking

When creating a release build with Amper, R8 will be used automatically, with minification and shrinking enabled.
This is equivalent to the following Gradle configuration:

```kotlin
// in Gradle
isMinifyEnabled = true
isShrinkResources = true
proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
```

You can create a `proguard-rules.pro` file in the module folder to add custom rules for R8.

```
├─ src/
├─ test/
├─ proguard-rules.pro
╰─ module.yaml
```

It is automatically used by Amper if present.

An example of how to add custom R8 rules can be found [in the android-app module]({{ examples_base_url }}/compose-multiplatform/android-app/proguard-rules.pro) of the `compose-multiplatform` example project.

### Signing

In a module containing an Android application (using the `android/app` product type) you can enable signing under
settings:

```yaml
settings:
  android:
    signing: enabled
```

This will use a `keystore.properties` file located in the module folder for the signing details by default. This
properties file must contain the following signing details. **Remember that these details should usually not be added
to version control.**

```properties
storeFile=/Users/example/.keystores/release.keystore
storePassword=store_password
keyAlias=alias
keyPassword=key_password
```

To customize the path to this file, you can use the `propertiesFile` option:

```yaml
settings:
  android:
    signing:
      enabled: true
      propertiesFile: ./keystore.properties # default value
```

You can use `./amper tool generate-keystore` to generate a new keystore if you don't have one yet.
This will create a new self-signed certificate, using the details in the `keystore.properties` file.

!!! note

    You can also pass in these details to `generate-keystore` as command line arguments. Invoke the tool with `--help`
    to learn more.

## Parcelize

If you want to automatically generate your `Parcelable` implementations, you can enable
[Parcelize](https://developer.android.com/kotlin/parcelize) as follows:

```yaml
settings:
  android:
    parcelize: enabled
```

With this simple toggle, the following class gets its `Parcelable` implementation automatically without spelling it out
in the code, just thanks to the `@Parcelize` annotation:
```kotlin
import kotlinx.parcelize.Parcelize

@Parcelize
class User(val firstName: String, val lastName: String, val age: Int): Parcelable
```

While this is only relevant on Android, sometimes you need to share your data model between multiple platforms.
However, the `Parcelable` interface and `@Parcelize` annotation are only present on Android.
But fear not, there is a solution described in the
[official documentation](https://developer.android.com/kotlin/parcelize#setup_parcelize_for_kotlin_multiplatform).
In short:

* For `android.os.Parcelable`, you can use the `expect`/`actual` mechanism to define your own interface as typealias of
  `android.os.Parcelable` (for Android), and as an empty interface for other platforms.
* For `@Parcelize`, you can simply define your own annotation instead, and then tell Parcelize about it (see below).

For example, in common code:
```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class MyParcelize

expect interface MyParcelable
```
Then in Android code:
```kotlin
actual typealias MyParcelable = android.os.Parcelable
```
And in other platforms:
```kotlin
// empty because nothing is generated on non-Android platforms
actual interface MyParcelable
```

You can then make Parcelize recognize this custom annotation using the `additionalAnnotations` option:

```yaml
settings:
  kotlin:
    # for the expect/actual MyParcelable interface
    freeCompilerArgs: [ -Xexpect-actual-classes ]
  android:
    parcelize:
      enabled: true
      additionalAnnotations: [ com.example.MyParcelize ]
```


## Google Services and Firebase

To enable the [`google-services` plugin](https://developers.google.com/android/guides/google-services-plugin), place
your `google-services.json` file in the module containing an `android/app` product, next to `module.yaml`.

```
╰─ androidApp/
   ├─ src/
   ├─ google-services.json
   ╰─ module.yaml
```

This file will be found and consumed automatically.
