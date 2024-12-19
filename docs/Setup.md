## Check your IDE

* Use the latest [IntelliJ IDEA EAP](https://www.jetbrains.com/idea/nextversion/) with Amper plugin for JVM and Android projects.
* Use the latest [JetBrains Fleet](https://www.jetbrains.com/fleet/) for JVM, Android, and Kotlin Multiplatform projects.
 
Fleet comes preconfigured with the Amper support. If you use IntelliJ IDEA, install the Amper plugin: ![](images/ij-plugin.png)

The best way to get the most recent IDE versions is by using the [Toolbox App](https://www.jetbrains.com/lp/toolbox/).

## Prepare the environment

Use the [KDoctor](https://github.com/Kotlin/kdoctor) tool to ensure that your development environment is configured correctly:

1. Install KDoctor with [Homebrew](https://brew.sh/):

    ```text
    brew install kdoctor
    ```

2. Run KDoctor in your terminal:

    ```text
    kdoctor
    ```

   If everything is set up correctly, you'll see valid output:

   ```text
   Environment diagnose (to see all details, use -v option):
   [✓] Operation System
   [✓] Java
   [✓] Android Studio
   [✓] Xcode
   [✓] Cocoapods
   
   Conclusion:
     ✓ Your system is ready for Kotlin Multiplatform Mobile development!
   ```

Otherwise, KDoctor will highlight which parts of your setup still need to be configured and will suggest a way to fix
them.


- Try opening [an example project](../examples-gradle/jvm) in IntelliJ IDEA or in Fleet. 
  You should get completion in the module.yaml files:
  ![](images/amper-in-ij.png)
  ![](images/amper-in-fleet.png)

### What's next
See the [tutorial](Tutorial.md) and [documentation](Documentation.md). Read how to use Amper in [IntelliJ IDEA](Usage.md#using-amper-in-intellij-idea) and [Fleet](Usage.md#using-amper-in-fleet). Try opening the example projects:
  - [JVM "Hello, World!"](../examples-gradle/jvm)
  - [Compose Desktop](../examples-gradle/compose-desktop)
  - [Compose Multiplatform](../examples-gradle/compose-multiplatform)
