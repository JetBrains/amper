# Tutorial

This tutorial gives a short introduction to Amper and shows how to create and configure a new project, step-by-step.
If you are looking for a detailed comprehensive documentation, check the [User guide](../user-guide/index.md) instead.

!!! tip "Before you start"

    Amper is designed with IDE support in mind, and much of the UX comes from the IDE.
    While the [Amper CLI](../cli/index.md) works well with any text editor, we recommend using 
    :jetbrains-intellij-idea: IntelliJ IDEA[^1] to get the most out of Amper.

    That said, it is not required for this tutorial – there are no IDE-specific steps.

[^1]: Since Amper is moving fast, it's best to use the latest 
      [IntelliJ IDEA EAP](https://www.jetbrains.com/idea/nextversion/) version.
      The best way to get the most recent IDE versions is by using the 
      [:jetbrains-toolbox-app: Toolbox App](https://www.jetbrains.com/lp/toolbox/).
      Also, don't forget to install the [Amper plugin](https://plugins.jetbrains.com/plugin/23076-amper). 

## Step 1. Hello, World

The first thing you’d want to try when getting familiar with a new tool is just a simple "Hello, World" application.
Here is what we do:

Create a `module.yaml` file at the root of your project:

```YAML title="module.yaml"
product: jvm/app
```

And add some code in the `src/` folder:

<div class="grid" markdown>
``` title="Project structure"  hl_lines="1 2"
├─ src/
│  ╰─ main.kt
╰─ module.yaml
```

```kotlin title="main.kt"
fun main() {
    println("Hello, World!")
}
```
</div>

You also need to add the Amper shell scripts to your root project folder.

* If you're in IntelliJ IDEA, you can simply use the quick fix in `module.yaml` to "Configure standalone Amper".
* If not, follow the [CLI installation instructions](./../cli/index.md#installation) to download them.

Your project should now look like this:
``` hl_lines="3 4"
├─ src/
│  ╰─ main.kt
├─ amper
├─ amper.bat
╰─ module.yaml
```

That’s it, we’ve just created a simple JVM application.

And since it’s a JVM project, you can add Java code. Java and Kotlin files can reside together,
no need to create separate Maven-like `java/` and `kotlin/` folders:

``` hl_lines="3"
├─ src/
│  ├─ main.kt
│  ╰─ JavaClass.java
├─ amper
├─ amper.bat
╰─ module.yaml
```

You can now build your application using `./amper build`, or run it using `./amper run`.

??? info "Run it directly from IntelliJ IDEA"

    If you're using IntelliJ IDEA, you can use the :intellij-run: Run icon in any of those places:

      * next to the `product: ` section in `module.yaml`:
      
      ![img.png](../images/ij-run-product.png)
      
      * next to the `main()` function in `main.kt`:
      
      ![](../images/ij-run-main.png)

      * in the main toolbar

!!! abstract "Related documentation"

    - [Project layout](../user-guide/basics.md#project-layout)
    - [Module file anatomy](../user-guide/basics.md#module-file-anatomy)
    - [Using Amper from the command line](../cli/index.md)

## Step 2. Add dependencies

Let's add a dependency on a Kotlin library using its Maven coordinates:

```YAML title="module.yaml" hl_lines="3 4"
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

!!! abstract "Related documentation: [Dependencies](../user-guide/dependencies.md)"

## Step 3. Add tests

Now let’s add some tests. Amper configures the testing framework automatically,
we only need to add some test code into the `test/` folder:

<div class="grid" markdown>
``` title="Project structure" hl_lines="3 4"
├─ src/
│  ╰─ ...
├─ test/
│  ╰─ MyTest.kt
├─ amper
├─ amper.bat
╰─ module.yaml
‎
```

```kotlin title="MyTest.kt"
import kotlin.test.*

class MyTest {
    @Test
    fun doTest() {
        assertTrue(true)
    }
}
```
</div>

To add test-specific dependencies, use the dedicated `test-dependencies` section.
This should be very familiar to Cargo, Flutter and Poetry users.
As an example, let's add the MockK library to the project:

```YAML title="module.yaml" hl_lines="6 7"
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.6.2

test-dependencies:
  - io.mockk:mockk:1.13.10
```

!!! example "Example: [JVM "Hello, World!"]({{ examples_base_url }}/jvm)"

!!! abstract "Related documentation: [Testing](../user-guide/testing.md)"

## Step 4. Configure Java and Kotlin

Another typical task is configuring compiler settings, such as language level etc. Here is how we do it in Amper:

```YAML title="module.yaml" hl_lines="9-13"
product: jvm/app

dependencies:
  - org.jetbrains.kotlinx:kotlinx-datetime:0.6.2

test-dependencies:
  - io.mockk:mockk:1.13.10

settings:
  kotlin:
    version: 2.2.21  # Set Kotlin compiler version to 2.2.21
  jvm:
    release: 17  # Set the minimum JVM version that the Kotlin and Java code should be compatible with.
```

!!! abstract "Related documentation: [Settings](../user-guide/basics.md#settings)"

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

!!! note

    The `$compose.*` dependencies are declared with a special reference syntax here.
    These are references to the Compose toolchain library catalog, and are available because we enabled the toolchain.
    Read more in the [Library catalogs](../user-guide/dependencies.md#library-catalogs) section.

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

!!! example "Examples"

    - [Compose Desktop]({{ examples_base_url }}/compose-desktop)
    - [Compose Android]({{ examples_base_url }}/compose-android)
    - [Compose iOS]({{ examples_base_url }}/compose-ios)
    - [Compose Multiplatform]({{ examples_base_url }}/compose-multiplatform)

!!! abstract "Related documentation: [Compose Multiplatform](../user-guide/builtin-tech/compose-multiplatform.md)"

## Step 6. Modularize

Let's split our project into a JVM application and a library module, with shared code that we are going to reuse later
when making the project multiplatform.

Our goal here is to separate our app into a `shared` library module and a `jvm-app` application module and reach the 
following structure:
```
├─ jvm-app/
│  ├─ ...
│  ╰─ module.yaml
├─ shared/
│  ├─ ...
│  ╰─ module.yaml
├─ amper
├─ amper.bat
╰─ project.yaml
```

First let's move our current `src`, `test` and `module.yaml` files into a new `jvm-app` directory:
``` hl_lines="1-6"
├─ jvm-app/
│  ├─ src/
│  │  ╰─ main.kt
│  ├─ test/
│  │  ╰─ ...
│  ╰─ module.yaml
├─ amper
╰─ amper.bat
```

Add a `project.yaml` file in the root, next to the existing `amper` and `amper.bat` files, with the following content:

```yaml title="project.yaml"
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
  - $compose.foundation: exported #(1)!
  - $compose.material3: exported
  - $compose.desktop.currentOs: exported

settings:
  compose:
    enabled: true
```

1. The `exported` keyword here means that the library exposes its dependencies to the dependent module's compilation.
   That module will therefore be able to use these dependencies in its code without depending on them.
   Read more about transitivity in the [Transitivity](../user-guide/dependencies.md#transitivity) section.

We can now change our `jvm-app/module.yaml` to depend on the `shared` module:

```yaml hl_lines="4"
product: jvm/app

dependencies:
  - ../shared #(1)!

settings:
  compose:
    enabled: true
```

1. The dependency on the `shared` module is declared using a relative path. Read more about module dependencies in the 
   [Module dependencies](../user-guide/dependencies.md#module-dependencies) section.

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

```kotlin hl_lines="6"
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        sayHello()
    }
}
```

We now have a multi-module project with some neatly extracted shared code.

!!! example "Example: [Compose Multiplatform]({{ examples_base_url }}/compose-multiplatform)"

!!! abstract "Related documentation"

    - [Project layout](../user-guide/basics.md#project-layout)
    - [Module dependencies](../user-guide/dependencies.md#module-dependencies)
    - [Dependency visibility and scope](../user-guide/dependencies.md#transitivity-and-scope)

## Step 7. Make the project multiplatform

So far we've been working with the JVM platform to create a desktop application.
Let's add an Android and an iOS application.
It will be straightforward, since we've already prepared a multi-module layout with a shared module that we can reuse.

Here is the project structure that we need:

```
├─ android-app/
│  ├─ src/
│  │  ├─ main.kt
│  │  ╰─ AndroidManifest.xml
│  ╰─ module.yaml
├─ ios-app/
│  ├─ src/
│  │  ├─ iosApp.swift
│  │  ╰─ main.kt
│  ├─ module.yaml
│  ╰─ module.xcodeproj
├─ jvm-app/
│  ╰─ ...
├─ shared/
│  ╰─ ...
╰─ project.yaml
```

Remember to add the new modules into the `project.yaml` file:

```yaml hl_lines="2-3"
modules:
  - ./android-app
  - ./ios-app
  - ./jvm-app
  - ./shared   
```

The new module files will look like this:

<div class="grid" markdown>
```yaml title="android-app/module.yaml"
product: android/app

dependencies:
  - ../shared

settings:
  compose:
    enabled: true
```

```yaml title="ios-app/module.yaml"
product: ios/app

dependencies:
  - ../shared

settings:
  compose:
    enabled: true
```
</div>

Let's update the `shared/module.yaml` and add the new platforms and a couple of additional dependencies for Android:

```yaml hl_lines="3 9-10 12-15"
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
Read more about multiplatform configuration in the [Multiplatform modules](../user-guide/multiplatform.md) section.

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

And the last step, copy the [AndroidManifest.xml file from an example project]({{ examples_base_url }}/compose-multiplatform/android-app/src/AndroidManifest.xml)
into `android-app/src` folder, and the [iosApp.swift file]({{ examples_base_url }}/compose-multiplatform/ios-app/src/iosApp.swift) into the `ios-app/src`.
These files bind the Compose UI code with the native application entry points.

Make sure that your project structure looks like this:
``` hl_lines="4 8"
├─ android-app/
│  ├─ src/
│  │  ├─ main.kt
│  │  ╰─ AndroidManifest.xml
│  ╰─ module.yaml
├─ ios-app/
│  ├─ src/
│  │  ├─ iosApp.swift
│  │  ╰─ main.kt
│  ╰─ module.yaml
├─ jvm-app/
├─ shared/
╰─ ...
```

Now you can build and run both apps using the corresponding IntelliJ IDEA run configurations, or use the CLI commands:
```shell
./amper run -m android-app
```
```shell
./amper run -m ios-app
```

!!! note

    After the first build, the Xcode project will appear beside the `module.yaml` in the `ios-app` module. 
    It can be checked into the VCS and customized (e.g. _Team_ (`DEVELOPMENT_TEAM`) setting).
    See [iOS Support](../user-guide/builtin-tech/ios.md) to learn more about the Xcode ↔ Amper interoperability.

!!! example "Example: [Compose Multiplatform]({{ examples_base_url }}/compose-multiplatform)"

!!! abstract "Related documentation"

    - [multiplatform configuration](../user-guide/multiplatform.md)
    - [Compose](../user-guide/builtin-tech/compose-multiplatform.md)

## Step 8. Deduplicate common configuration

You might have noticed that there are some settings present in multiple `module.yaml` files.
To reduce duplication we can extract them into a template.

Let's create a couple of template files:

<div class="grid" markdown>
<div>
``` title="Project structure" hl_lines="9-10"
├─ android-app/
│  ╰─ ...
├─ ios-app/
│  ╰─ ...
├─ jvm-app/
│  ╰─ ...
├─ shared/
│  ╰─ ...
├─ compose.module-template.yaml
╰─ app.module-template.yaml
```
</div>
<div>
```yaml title="compose.module-template.yaml"
settings:
  compose:
    enabled: true
```

```yaml title="app.module-template.yaml"
dependencies:
  - ./shared
```
</div>
</div>

Now we will apply these templates to our module files:

```yaml title="shared/module.yaml" hl_lines="5-6"
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

```yaml title="jvm-app/module.yaml"
product: jvm/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml
```

```yaml title="android-app/module.yaml"
product: android/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml
```

```yaml title="ios-app/module.yaml"
product: ios/app

apply:
  - ../compose.module-template.yaml
  - ../app.module-template.yaml
```

You can put all common dependencies and settings into the template. It's also possible to have multiple templates 
for various typical configurations in the project.

!!! abstract "Related documentation: [Templates](../user-guide/templates.md)"

## Further steps

Check the [user guide](../user-guide/index.md) and explore [example projects]({{ examples_base_url }}).
