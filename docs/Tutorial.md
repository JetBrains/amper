This tutorial gives a short introduction to Amper and how to create a new project.

If you are looking for more detailed info, check [the documentation](Documentation.md).

### Before you start

Check the [setup instructions](Setup.md).

### Step 1. Hello, World

The first thing you’d want to try when getting familiar with a new tool is just a simple "Hello, World" application. Here
is what we do:

Create a `module.yaml` file:

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

> To use the standalone Amper version, you need to add a couple of shell scripts to your project folder.
> Copy the following files from a [template project](../examples-standalone/new-project-template):
>
> - [amper](../examples-standalone/new-project-template/amper)
> - [amper.bat](../examples-standalone/new-project-template/amper.bat)
>
> ```
> |-src/
> |  |-main.kt
> |-module.yaml
> |-amper
> |-amper.bat
> ```

> To use the [Gradle-based](Documentation.md#gradle-based-projects) Amper version,
> you need to create `settings.gradle.kts` and Gradle wrappers in the project root. Thes files are necessary to
> configure
> and launch Gradle. Copy the following files from a [template project](../examples-gradle/new-project-template):
>
> - [settings.gradle.kts](../examples-gradle/new-project-template/settings.gradle.kts),
> - [gradlew](../examples-gradle/new-project-template/gradlew)
    > and [gradlew.bat](../examples-gradle/new-project-template/gradlew.bat),
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

Documentation:

- [Using standalone Amper](Usage.md#using-the-standalone-amper-from-command-line)
- [Using Gradle-based Amper](Usage.md#using-the-gradle-based-amper-from-command-line)
- [Project layout](Documentation.md#project-layout)
- [module file anatomy](Documentation.md#module-file-anatomy)

### Step 2. Add dependencies

Let's add a dependency on a Kotlin library from the Maven repository:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0
```

We can now use this library in the `main.kt` file:

```kotlin
import kotlinx.datetime.*

fun main() {
    println("Hello, World!")
    println("It's ${Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())} here")
}
```

Documentation:
- [Dependencies](Documentation.md#dependencies)

### Step 3. Add tests

Now let’s add some tests. Amper configures the testing framework automatically,
we only need to add a test code into the `test/` folder:

```
|-src/
|  |-...
|-test/
|  |-MyTest.kt
|-module.yaml
```

```kotlin
class MyTest {
    @Test
    fun doTest() {
        assertTrue(true)
    }
}
```

To add test-specific dependencies, use the dedicated `test-dependencies:` section.
This should be very familiar to the Cargo, Flutter and Poetry users.
As an example, let's add a MockK library to the project:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - io.mockk:mockk:1.13.10
```

Examples: JVM "Hello, World!" ([standalone](../examples-standalone/jvm), [Gradle-based](../examples-gradle/jvm))

Documentation:
- [Tests](Documentation.md#tests)

### Step 4. Configure Java and Kotlin

Another typical task is configuring compiler settings, such as language level etc. Here is how we do it in Amper:

```YAML
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.4.0

test-dependencies:
  - io.mockk:mockk:1.13.10

settings:
  kotlin:
    languageVersion: 1.8  # Set Kotlin source compatibility to 1.8
  jvm:
    release: 17  # Set the minimum JVM version that the Kotlin and Java code should be compatible with.
```

Documentation:
- [Settings](Documentation.md#settings)

### Step 5. Add Compose Multiplatform

Now, let's turn the example into a GUI application.
To do that we'll the [Compose Multiplatform framework](https://github.com/JetBrains/compose-multiplatform):

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

and add the following code in the `main.kt` file:

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

- Compose
  Desktop ([standalone](../examples-standalone/compose-desktop), [Gradle-based](../examples-gradle/compose-desktop))
- Compose
  Android ([standalone](../examples-standalone/compose-android), [Gradle-based](../examples-gradle/compose-android))
- Compose iOS ([standalone](../examples-standalone/compose-ios), [Gradle-based](../examples-gradle/compose-ios))
- Compose
  Multiplatform ([standalone](../examples-standalone/compose-multiplatform), [Gradle-based](../examples-gradle/compose-multiplatform))

Documentation:
- [Configuring Compose Multiplatform](Documentation.md#configuring-compose-multiplatform)

### Step 6. Modularize

Let's split our project into a JVM application and a library module with a shared code, which we are going to reuse.
It will have the following structure:

```
|-jvm-app/
|  |-src/
|  |  |-main.kt
|  |-test/
|  |  |-...
|  |-module.yaml
|-shared/
|  |-src/
|  |  |-hello.kt
|  |-module.yaml
```

> In the case of the standalone Amper, we'll also add `project.yaml` file in the root,
> next to the existing `amper` and `amper.bat` files. It will help Amper find all modules in the project:
> ```
> |-jvm-app/
> |  |-...
> |  |-module.yaml
> |-shared/
> |  |-...
> |  |-module.yaml
> |-amper
> |-amper.bat
> |-project.yaml
> ```
>
> After that add the module to the `project.yaml` file:
>
> ```yaml
> modules:
>   - ./jvm-app
>   - ./lib
> ```
> Read more about the [project layout](Documentation.md#project-layout)

> In the case of the [Gradle-based](Documentation.md#gradle-based-projects) Amper version,
> the previously added Gradle files will remain in the project root:
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
>
> Read more about the [project layout](Documentation.md#project-layout)


The `jvm-app/module.yaml` will look like this
```YAML
product: jvm/app

dependencies:
  - ../shared # use the 'shared' module as a dependency

settings:
  compose:
    enabled: true
```

Note how a dependency on the `shared` module is declared using a relative path.

And the `shared/module.yaml`:
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

Let's extract the common code into the `shared/src/hello.kt` file:

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

Now we have a multi-module project with some neatly extracted shared code. 

Examples: Compose
Multiplatform ([standalone](../examples-standalone/compose-multiplatform), [Gradle-based](../examples-gradle/compose-multiplatform))

Documentation:
- [Project layout](Documentation.md#project-layout)
- [Module dependencies](Documentation.md#module-dependencies)
- [Dependency visibility and scope](Documentation.md#scopes-and-visibility)

### Step 7. Make project multi-platform

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
```

Don't forget to add the new modules to the project files.
For the standalone Amper, into the `project.yaml` file:

```yaml
modules:
  - ./android-app
  - ./ios-app
  - ./jvm-app
  - ./shared   
```

For the Gradle-based Amper, into the `settings.gradle.kts` file:
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
Read more about multi-platform configuration in the [documentation](Documentation.md#multi-platform-configuration). 

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

> In the case the Gradle-based Amper, you also need to add a couple of configuration files.
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
>  

Now you can build and run both apps using [the Fleet run configurations](Usage.md#using-amper-in-fleet).

Examples: Compose Multiplatform
([standalone](../examples-standalone/compose-multiplatform),
[Gradle-based](../examples-gradle/compose-multiplatform))

Documentation:
- [Multi-platform configuration](Documentation.md#multi-platform-configuration)
- [Configuring Compose Multiplatform](Documentation.md#configuring-compose-multiplatform)


### Step 8. Deduplicate common configuration

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

Documentation:
- [Templates](Documentation.md#templates)

### Further steps

Check the [documentation](Documentation.md) and explore examples [for the standalone Amper](../examples-standalone) and
[for the Gradle-based Amper](../examples-gradle).