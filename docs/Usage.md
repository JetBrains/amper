### Using Amper from command line

Since Amper is currently [Gradle-based](Documentation.md#gradle-based-projects) you can use it as usually:
E.g. to build and run the [JVM Hello, World](../examples/jvm-hello-world):
```
cd jvm-hello-world
./gradlew run 
```
See the [Gradle tutorial](https://docs.gradle.org/current/samples/sample_building_java_applications.html) for more info.

_Note, that to use Amper with Kotlin Multiplatform (e.g. running on devices and simulators), [Fleet](#using-amper-in-fleet) is recommended._

### Using Amper in IntelliJ IDEA and Android Studio

See [the setup instructions](Setup.md) to configure your IDE and the environment.

Open an Amper project as usual by pointing at the folder with the main `settings.gradle.kts` file:

To run an application:

* use a 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `product: ` section in a module.yaml file:\
 ![img.png](images/ij-run-product.png)


* use a 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `main()` function:\
  ![](images/ij-run-main.png)


* use [Run/Debug configurations](https://www.jetbrains.com/help/idea/run-debug-configuration.html):\
  ![](images/ij-run-config-jvm.png)\
  ![](images/ij-run-config-android.png)


* launch a Gradle task directly:\
  ![](images/ij-run-gradle-task.png)
  

To run tests use the same 'run' (![](images/ij-run-gutter-icon.png)) gutter icon or Gradle run configuration. Read more on [testing in IntelliJ IDEA](https://www.jetbrains.com/help/idea/work-with-tests-in-gradle.html#run_gradle_test).\
![](images/ij-run-tests.png)


### Using Amper in Fleet
See [the setup instructions](Setup.md) to configure your IDE and the environment.

Open an Amper project as usual by pointing at the folder with the main settings.gradle.kts file:

To run an application:

* use a 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon next to the `product: ` section in a module.yaml file:\
 ![](images/fleet-run-product.png)


* use a 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon next to the `main()` function:\
  ![](images/fleet-run-main.png)


* use [Run configurations](https://www.jetbrains.com/help/fleet/getting-started-with-kotlin-in-fleet.html#create-rc):\
  ![](images/fleet-run-config.png)\
  ![](images/fleet-run-config-ios.png)


* launch a Gradle task directly:\
  ![](images/fleet-run-gradle-task.png)

To run tests use the same 'run' (![](images/fleet-run-gutter-icon.png)) gutter icon or Gradle run configuration.

#### Configuring device and simulators
To select a target device used to an Android or iOS application:

* Create a run configuration in `.fleet/run.json`:\  
  ![](images/fleet-create-run-configuration.png)

* Specify a type - `kmp-app` for iOS or `android-app` for Android - and a device or a simulator in the `destination` parameter:\   
  ![](images/fleet-select-ios-device.png)\
  ![](images/fleet-select-android-device.png)


