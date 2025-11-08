## Using Amper from the command line

### Installation

To use the Amper CLI, you need to download the Amper executable script to your project's root folder.
The script is a small file that downloads and runs the actual Amper CLI distribution, and serves as an entry point for
all Amper commands. It is meant to be checked into your VCS, so your team can build and run your project without any
installation, no matter their OS.

Use one of the following commands to download the script:

Linux/macOS:
```shell
curl -fsSL -o amper https://jb.gg/amper-latest-wrapper.sh && chmod +x amper && ./amper update -c
```

Windows PowerShell:
```powershell
Invoke-WebRequest -OutFile amper.bat -Uri https://jb.gg/amper-latest-wrapper.bat; ./amper update -c
```

!!! note

    The `./amper update -c` command will automatically get the wrapper script for the other OS. 
    You can check both into your VCS so your team can build and run your project without any installation, on any OS.

!!! note

    The first time you run the Amper script, it will take some time to download the Amper CLI distribution.
    Subsequent runs will be faster, as the downloaded files will be cached locally.

### Exploring Amper commands

The root `./amper` command and all subcommands support the `-h` (or `--help`) option to explore what is possible:

```shell
./amper --help       # shows the available commands and general options
./amper build --help # shows the options for the 'build' command specifically
```

Useful commands:

- `amper init` to create a new Amper project
- `amper build` to compile and link all code in the project
- `amper test` to run tests in the project
- `amper run` to run your application 
- `amper clean` to remove the project's build output and caches

For example, to build and run the [JVM "Hello, World"]({{ examples_base_url }}/jvm):
```shell
cd jvm
./amper run 
```

### Amper CLI tab-completion

If you’re using `bash`, `zsh`, or `fish`, you can generate a completion script to source as part of your shell’s
configuration, to get tab completion for Amper commands.

First, generate the completion script using the `generate-completion` command, specifying the shell you use:

```shell
./amper generate-completion zsh > ~/amper-completion.sh
```

Then load the script in your shell (this can be added to `.bashrc`, `.zshrc`, or similar configuration files to load it
automatically):

```shell
source ~/amper-completion.sh
```

You should now have tab completion available for Amper subcommands, options, and option values.

### Updating Amper to a newer version

Run `./amper update` to update the Amper scripts and distribution to the latest released version.
Use the `--dev` option if you want to try the bleeding edge dev build of Amper (no guarantees are made on these builds).

See `./amper update -h` for more information about the available options.

!!! tip  

    If you had generated a completion script before, you need to re-generate it with the new Amper version (see previous
    section).

## Using Amper in IntelliJ IDEA

> The latest [IntelliJ IDEA EAP](https://www.jetbrains.com/idea/nextversion/) can be used to work with 
> JVM and Android projects.

See [the setup instructions](setup.md) to configure your IDE and the environment.

Open an Amper project as usual by [pointing at the root folder](https://www.jetbrains.com/guide/java/tutorials/import-project/open-project/).

To run an application:

* use the 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `product: ` section in a `module.yaml` file:\
 ![img.png](images/ij-run-product.png)


* use the 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the `main()` function:\
  ![](images/ij-run-main.png)


* use [Run/Debug configurations](https://www.jetbrains.com/help/idea/run-debug-configuration.html):\
  ![](images/ij-run-config-jvm.png)\
  ![](images/ij-run-config-android.png)

To run tests, use the 'run' (![](images/ij-run-gutter-icon.png)) gutter icon next to the test functions or classes.
Read more on [testing in IntelliJ IDEA](https://www.jetbrains.com/help/idea/work-with-tests-in-gradle.html#run_gradle_test).
![](images/ij-run-tests.png)
