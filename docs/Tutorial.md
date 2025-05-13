# Tutorial

This tutorial gives a short introduction to Amper and how to create a new project.

If you are looking for more detailed information, check [the documentation](Documentation.md).

## Before you start

Check the [setup instructions](Setup.md).

## Step 1. Hello, World

The first thing you’d want to try when getting familiar with a new tool is just a simple "Hello, World" application.
Here is what we do:

Create a `module.yaml` file at the root of your project:

```YAML
product: jvm/app
```

And add some code in the `src/` folder:

```
|-src/
|  |-main.kt
|-module.yaml
```

`main.kt` file:
```kotlin
fun main() {
    println("Hello, World!")
}
```

You also need to add the Amper shell scripts to your root project folder.
* If you're in IntelliJ IDEA, you can simply use the quick fix in `module.yaml` to "Configure standalone Amper".
* If not, follow the [CLI installation instructions](./Usage.md#installation) to download them.

Your project should now look like this:
```
|-src/
|  |-main.kt
|-module.yaml
|-amper
|-amper.bat
```

> To use Amper in a [Gradle-based project](Documentation.md#gradle-based-projects), instead of the 
> `amper` and `amper.bat` files you need to create `settings.gradle.kts` and Gradle wrappers in the project root. 
> These files are necessary to configure and launch Gradle. 
> Copy the following files from a [template project](../examples-gradle/new-project-template):
>
> - [settings.gradle.kts](../examples-gradle/new-project-template/settings.gradle.kts),
> - [gradlew](../examples-gradle/new-project-template/gradlew) and [gradlew.bat](../examples-gradle/new-project-template/gradlew.bat),
> - [gradle](../examples-gradle/new-project-template/gradle) folder
> ```
> |-gradle/...
> |-src/
> |  |-main.kt
> |-module.yaml
> |-settings.gradle.kts
> |-gradlew
> |-gradlew.bat
> ```

That’s it, we’ve just created a simple JVM application.

And since it’s a JVM project, you can add Java code. Java and Kotlin files can reside together,
no need to create separate Maven-like `java/` and `kotlin/` folders:

```
|-src/
|  |-main.kt
|  |-JavaClass.java
|-module.yaml
```

Examples: JVM "Hello, World!" ([standalone](../examples-standalone/jvm), [Gradle-based](../examples-gradle/jvm))

You can now build your application using `./amper build`, or run it using `./amper run`.

> To go further, you can check these sections of the documentation:
> 
> - [Project layout](Documentation.md#project-layout)
> - [Module file anatomy](Documentation.md#module-file-anatomy)
> - [Using Amper from the command line](Usage.md#using-amper-from-the-command-line)
> - [Using Gradle-based Amper](Usage.md#using-the-gradle-based-amper-version-from-the-command-line)

## Step 2. Add dependencies

Let's add a dependency on a Kotlin library from the Maven repository:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.6.2
```

We can now use this library in the `main.kt` file:

```kotlin
import kotlinx.datetime.*

fun main() {
    println("Hello, World!")
    println("It's ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())} here")
}
```

> See the full documentation about [Dependencies](Documentation.md#dependencies).

## Step 3. Add tests

Now let’s add some tests. Amper configures the testing framework automatically,
we only need to add some test code into the `test/` folder:

```
|-src/
|  |-...
|-test/
|  |-MyTest.kt
|-module.yaml
```

```kotlin
import kotlin.test.*

class MyTest {
    @Test
    fun doTest() {
        assertTrue(true)
    }
}
```

To add test-specific dependencies, use the dedicated `test-dependencies:` section.
This should be very familiar to Cargo, Flutter and Poetry users.
As an example, let's add the MockK library to the project:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.6.2

test-dependencies:
  - io.mockk:mockk:1.13.10
```

Examples: JVM "Hello, World!" ([standalone](../examples-standalone/jvm), [Gradle-based](../examples-gradle/jvm))

> See the full documentation about [Tests](Documentation.md#tests).

## Step 4. Configure Java and Kotlin

Another typical task is configuring compiler settings, such as language level etc. Here is how we do it in Amper:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.6.2

test-dependencies:
  - io.mockk:mockk:1.13.10

settings:
  kotlin:
    languageVersion: 1.8  # Set Kotlin source compatibility to 1.8
  jvm:
    release: 17  # Set the minimum JVM version that the Kotlin and Java code should be compatible with.
```

> See the full documentation about [Settings](Documentation.md#settings).

## Step 5. Add a UI with Compose

Now, let's turn the example into a GUI application.
To do that we'll add the [Compose Multiplatform framework](https://github.com/JetBrains/compose-multiplatform).
It allows building plain JVM desktop apps, which are simple for now, and paves the way for turning multiplatform later.

Let's change our `module.yaml` to:
```YAML
product: jvm/app

dependencies:
  # ...other dependencies...

  # add Compose dependencies
  - $compose.foundation
  - $compose.material3
  - $compose.desktop.currentOs

settings:
  # ...other settings...

  # enable the Compose framework toolchain  
  compose:
    enabled: true
```

> [!NOTE]
> The `$compose.*` dependencies are declared with a special reference syntax here.
> These are references to the Compose toolchain library catalog, and are available because we enabled the toolchain.
> Read more about library catalogs in the [documentation](Documentation.md#library-catalogs-aka-version-catalogs).

We can then replace the contents of `main.kt` with the following code:

```kotlin
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        BasicText("Hello, World!")
    }
}
```

Now we have a GUI application!

Examples:

- Compose Desktop ([standalone](../examples-standalone/compose-desktop), 
  [Gradle-based](../examples-gradle/compose-desktop))
- Compose Android ([standalone](../examples-standalone/compose-android), 
  [Gradle-based](../examples-gradle/compose-android))
- Compose iOS ([standalone](../examples-standalone/compose-ios), 
  [Gradle-based](../examples-gradle/compose-ios))
- Compose Multiplatform ([standalone](../examples-standalone/compose-multiplatform),
  [Gradle-based](../examples-gradle/compose-multiplatform))

> See the full documentation about [Compose](Documentation.md#configuring-compose-multiplatform).

## Step 6. Modularize

Let's split our project into a JVM application and a library module, with shared code that we are going to reuse later
when making the project multiplatform.

Our goal here is to separate our app into a `shared` library module and a `jvm-app` application module and reach the 
following structure:
```
|-jvm-app/
|  |-...
|  |-module.yaml
|-shared/
|  |-...
|  |-module.yaml
|-amper
|-amper.bat
|-project.yaml
```

First let's move our current `src`, `test` and `module.yaml` files into a new `jvm-app` directory:
```
|-jvm-app/
|  |-src/
|  |  |-main.kt
|  |-test/
|  |  |-...
|  |-module.yaml
|-amper
|-amper.bat
```

Add a `project.yaml` file in the root, next to the existing `amper` and `amper.bat` files, with the following content:

```yaml
modules:
  - ./jvm-app
  - ./shared
```

If you're using IntelliJ IDEA, you should see a warning that the `shared` module is missing, and you can automatically
create it from here. Otherwise, just create a new `shared` directory manually, with `src` and `test` directories, and
a `module.yaml` with the following content:

```YAML
product:
  type: lib
  platforms: [jvm]

dependencies:
  - $compose.foundation: exported
  - $compose.material3: exported
  - $compose.desktop.currentOs: exported

settings:
  compose:
    enabled: true
```

Note how the library 'exports' its dependencies. The dependent module will 'see' these dependencies and don't need to
explicitly depend on them.

We can now change our `jvm-app/module.yaml` to depend on the `shared` module:

```YAML
product: jvm/app

dependencies:
  - ../shared # use the 'shared' module as a dependency

settings:
  compose:
    enabled: true
```

Note how the dependency on the `shared` module is declared using a relative path.

Let's extract the common code into a new `shared/src/hello.kt` file:

```kotlin
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable

@Composable
fun sayHello() {
    BasicText("Hello, World!")
}
```

And re-use it in the `jvm-app/src/main.kt` file:
```kotlin
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        sayHello()
    }
}
```

We now have a multi-module project with some neatly extracted shared code.

> In the case of a [Gradle-based project](Documentation.md#gradle-based-projects), 
> `settings.gradle.kts` is used instead of `project.yaml` file.
> So all previously added Gradle files will remain in the project root:
>
> ```
> |-jvm-app/
> |  |-...
> |  |-module.yaml
> |-shared/
> |  |-...
> |  |-module.yaml
> |-gradle/...
> |-settings.gradle.kts
> |-gradlew
> |-gradlew.bat
> ```
>
> Add modules to the `settings.gradle.kts` file:
> ```kotlin
> // ... existing code in the settings.gradle.kts file ...
> 
> // add new modules to the project
> include("jvm-app", "shared")
> ```

Examples: Compose Multiplatform ([standalone](../examples-standalone/compose-multiplatform), 
[Gradle-based](../examples-gradle/compose-multiplatform))

> See the full documentation about:
> - [Project layout](Documentation.md#project-layout)
> - [Module dependencies](Documentation.md#module-dependencies)
> - [Dependency visibility and scope](Documentation.md#scopes-and-visibility)

## Step 7. Make project multiplatform

So far we've been working with a JVM platform to create a desktop application.
Let's add an Android and an iOS application.
It will be straightforward, since we've already prepared a multi-module layout with a shared module that we can reuse.

Here is the project structure that we need:

```
|-android-app/
|  |-src/
|  |  |-main.kt
|  |  |-AndroidManifest.xml
|  |-module.yaml
|-ios-app/
|  |-src/
|  |  |-iosApp.swift
|  |  |-main.kt
|  |-module.yaml
|-jvm-app/
|  |-...
|-shared/
|  |-...
| project.yaml
```

Remember to add the new modules into the `project.yaml` file:

```yaml
modules:
  - ./android-app
  - ./ios-app
  - ./jvm-app
  - ./shared   
```

> In case of a Gradle-based Amper project, into the `settings.gradle.kts` file:
> ```kotlin
> // add new modules to the project
> include("android-app", "ios-app", "jvm-app", "shared")
> ``` 


The `android-app/module.yaml` will look like this way:
```YAML
product: android/app

dependencies:
  - ../shared

settings:
  compose:
    enabled: true
```

And the `ios-app/module.yaml`:

```YAML
product: ios/app

dependencies:
  - ../shared

settings:
  compose:
    enabled: true
  ios:
    teamId: <your team ID here> # See https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/
```

Let's update the `shared/module.yaml` and add the new platforms and a couple of additional dependencies for Android:

```YAML
product:
  type: lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, iosX64 ]

dependencies:
  - $compose.foundation: exported
  - $compose.material3: exported

dependencies@jvm:
  - $compose.desktop.currentOs: exported

dependencies@android:
  # Compose integration with Android activities
  - androidx.activity:activity-compose:1.7.2: exported
  - androidx.appcompat:appcompat:1.6.1: exported

settings:
  compose:
    enabled: true
```

Note how we used the `dependencies@jvm:` and `dependencies@android:` sections to specify JVM- and Android-specific dependencies.
These dependencies will be added to the JVM and Android versions of the `shared` library correspondingly.
They will also be available for the `jvm-app` and `android-app` modules, since they depend on the `shared` module.
Read more about multiplatform configuration in the [documentation](Documentation.md#multiplatform-configuration).

Now, as we have the module structure, we need to add platform-specific application code to the Android and iOS modules.
Create a `MainActivity.kt` file in `android-app/src` with the following content:

```kotlin
package hello.world

import sayHello
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            sayHello()
        }
    }
}
```

Next, create a `ViewController.kt` file in `ios-app/src`:

```kotlin
import sayHello
import androidx.compose.ui.window.ComposeUIViewController

fun ViewController() = ComposeUIViewController { 
    sayHello() 
}
```

And the last step, copy
the [AndroidManifest.xml file from an example project](../examples-gradle/compose-multiplatform/android-app/src/AndroidManifest.xml)
into `android-app/src` folder, and the [iosApp.swift file](../examples-gradle/compose-multiplatform/ios-app/src/iosApp.swift) into the `ios-app/src`.
These files bind the Compose UI code with the native application entry points.

Make sure that your project structure looks like this:
```
|-android-app/
|  |-src/
|  |  |-main.kt
|  |  |-AndroidManifest.xml
|  |-module.yaml
|-ios-app/
|  |-src/
|  |  |-iosApp.swift
|  |  |-main.kt
|  |-module.yaml
|-jvm-app/
|-shared/
|-...
```

> In the case of a Gradle-based project, you also need to add a couple of configuration files.
> Copy the [gradle.properties](../examples-gradle/compose-multiplatform/gradle.properties) into your project root,
> and create a `local.properties` file nearby with the follwoing content:
> 
> ```properties 
> ## This file must *NOT* be checked into Version Control Systems
> 
> sdk.dir=<path to the Android SDK>
> ```
> [Check the instructions](https://stackoverflow.com/a/48155800) on how to set the `sdk.dir` on StackOverflow 
>
> Your project root content will look like this: 
> ```
> |-android-app/
> |-...
> |-settings.gradle.kts
> |-gradle.properties
> |-local.properties
> ```

Now you can build and run both apps using [the IntelliJ IDEA run configurations](Usage.md#using-amper-in-intellij-idea).

Examples: Compose Multiplatform
([standalone](../examples-standalone/compose-multiplatform),
[Gradle-based](../examples-gradle/compose-multiplatform))

> See the full documentation about [multiplatform configuration](Documentation.md#multiplatform-configuration) and
> [configuring Compose Multiplatform](Documentation.md#configuring-compose-multiplatform) more specifically.

## Step 8. Deduplicate common configuration

You might have noticed that there are some settings present in  the `module.yaml` files. To redce duplication we can extract them into a template.

Let's create a couple of `<name>.module-template.yaml` files:
```
|-android-app/
|  |-...
|-ios-app/
|  |-...
|-jvm-app/
|  |-...
|-shared/
|  |-...
|-compose.module-template.yaml
|-app.module-template.yaml
```

A `/compose.module-template.yaml` with settings common to all modules:
```YAML
settings:
  compose:
    enabled: true
```

and `/app.module-template.yaml` with dependencies that are used in the application modules:
```YAML
dependencies:
  - ./shared
```

Now we will apply these templates to our module files:

`/shared/module.yaml`:
```YAML
product:
  type: lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64, iosX64 ]

apply:
  - ../compose.module-template.yaml

dependencies:
  - $compose.foundation: exported
  - $compose.material3: exported

dependencies@jvm:
  - $compose.desktop.currentOs

dependencies@android:
  # Compose integration with Android activities
  - androidx.activity:activity-compose:1.7.2: exported
  - androidx.appcompat:appcompat:1.6.1: exported
```

`/jvm-app/module.yaml`:
```YAML
product: jvm/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml
```

`/android-app/module.yaml`:
```YAML
product: android/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml

```

`/ios-app/module.yaml`:
```YAML
product: ios/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml

settings:
  ios:
    teamId: <your team ID here> # See https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/
```

You can put all common dependencies and settings into the template. It's also possible to have multiple templates 
for various typical configurations in the project.

> See the full documentation about [Templates](Documentation.md#templates).

## Further steps

Check the [documentation](Documentation.md) and explore examples [for the standalone Amper projects](../examples-standalone) and
[for the Gradle-based Amper projects](../examples-gradle).