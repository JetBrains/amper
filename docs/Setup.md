## Setup your IDE

1. Preferably use the latest [IntelliJ IDEA EAP](https://www.jetbrains.com/idea/nextversion/). 
   The best way to get the most recent IDE versions is by using the [Toolbox App](https://www.jetbrains.com/lp/toolbox/).

2. Make sure to install the [Amper plugin](https://plugins.jetbrains.com/plugin/23076-amper):

   ![](images/ij-plugin.png)

3. [Optional] If you want to write code for Apple platforms or share code between several platforms, install the
   [Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform)

4. [Optional] If you want to write some Android-specific code, also install the
   [Android plugin](https://plugins.jetbrains.com/plugin/22989-android)

## Check your environment with KDoctor (macOS only)

If you're on macOS, you can use the [KDoctor](https://github.com/Kotlin/kdoctor) tool to ensure that your development
environment is configured correctly:

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

> [!NOTE]
> Xcode has to be 16 or newer.

> [!WARNING]
> KDoctor has a [known issue](https://youtrack.jetbrains.com/issue/KMT-1077) that triggers an incorrect warning:
> `Kotlin Multiplatform Mobile Plugin: not installed`. This should be disregarded.
> The proper plugin to install for multiplatform development is the
> [Kotlin Multiplatform plugin](https://plugins.jetbrains.com/plugin/14936-kotlin-multiplatform).

> [!IMPORTANT]  
> Despite what the KDoctor checks imply, you do not need to install Android Studio to work with Amper.
> There is no Amper plugin for Android Studio at the moment. As mentioned above, we recommend using IntelliJ IDEA.

## What's next

Take your pick:

* Discover the ins and outs of Amper in our comprehensive [documentation](Documentation.md).
* Create a project from scratch by following the [tutorial](Tutorial.md).
* Read how to use Amper in [IntelliJ IDEA](Usage.md#using-amper-in-intellij-idea).
  Try downloading and opening [one of the example projects](../examples/README.md) to see Amper in action.
