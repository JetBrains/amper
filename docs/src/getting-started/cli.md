# Amper CLI

## Installation

To use the Amper CLI, you need to download the Amper wrapper script to your project's root folder.
The script is a small file that downloads and runs the actual Amper CLI distribution, and serves as an entry point for
all Amper commands. It is meant to be checked into your VCS, so your team can build and run your project without any
installation, no matter their OS.

??? success "IntelliJ IDEA can take care of this for you"

    New projects created using the IntelliJ IDEA wizard will already contain the wrapper scripts. Also, if you create an
    Amper `module.yaml` file in a blank project, IntelliJ IDEA will offer to setup the wrapper scripts for you.

Use the following command in your project directory to download the script and set up Amper:

=== ":material-linux: Linux / :material-apple: macOS"
    
    ```shell
    curl -fsSL -o amper https://jb.gg/amper/wrapper.sh && chmod +x amper && ./amper update -c
    ```

=== ":material-microsoft-windows: Windows"
    
    ```powershell title="PowerShell"
    Invoke-WebRequest -OutFile amper.bat -Uri https://jb.gg/amper/wrapper.bat; ./amper update -c
    ```
    
    ```shell title="cmd.exe"
    curl -fsSL -o amper.bat https://jb.gg/amper/wrapper.bat && call amper update -c
    ```

The `./amper update -c` command following the download is not strictly necessary, but it will automatically get the 
wrapper script for the other OS. It is good practice to check them both into your VCS so your team can build and run 
your project without any installation, on any OS.

!!! note

    The first time you run the Amper script, it will take some time to download the Amper CLI distribution.
    Subsequent runs will be faster, as the downloaded files will be cached locally.

    The `./amper update` call that is part of the above installation command will actually do this first run for you.

## Exploring Amper commands

The root `./amper` command and all subcommands support the `-h` (or `--help`) option to explore what is possible:

```shell
./amper --help       # shows the available commands and general options
./amper build --help # shows the options for the 'build' command specifically
```

Useful commands:

- `amper init` to create a new Amper project
- `amper build` to compile and link all code in the project
- `amper run` to run your application
- `amper test` to run tests in the project
- `amper show (modules|settings|dependencies|tasks)` to introspect the project's configuration
- `amper clean` to remove the project's build output and caches

!!! example "Try it out!"

    Create a new project using the `./amper init` command and select the *JVM console application* template.

    Then build and run the application using `./amper run`.


## Tab-completion

If you’re using `bash`, `zsh`, or `fish`, you can generate a completion script to source as part of your shell’s
configuration, to get tab completion for Amper commands.

First, generate the completion script using the `generate-completion` command, specifying the shell you use:

=== "bash"

    ```shell
    ./amper generate-completion bash > ~/amper-completion.sh
    ```

=== "zsh"

    ```shell
    ./amper generate-completion zsh > ~/amper-completion.sh
    ```

=== "fish"

    ```shell
    ./amper generate-completion fish > ~/amper-completion.sh
    ```

Then load the script in your shell (this can be added to `.bashrc`, `.zshrc`, or similar configuration files to load it
automatically):

```shell
source ~/amper-completion.sh
```

You should now have tab completion available for Amper subcommands, options, and option values.

## Updating Amper to a newer version

Run `./amper update` to update the Amper scripts and distribution to the latest released version.
Use the `--dev` option if you want to try the bleeding edge dev build of Amper (no guarantees are made on these builds).

See `./amper update -h` for more information about the available options.

!!! tip "Don't forget to regenerate your tab-completion script, if you have one."
