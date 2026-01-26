---
description: |
  Part of Amper's philosophy is to avoid the hassle of setting up toolchains, including the JDK and Amper 
  itself. This is why we recommend checking the Amper wrapper script into your project's root folder.
---
# Wrapper script & provisioning

Part of Amper's philosophy is to avoid the hassle of setting up toolchains, including the JDK and Amper itself.

The recommended way to use Amper is to check the Amper wrapper script into your project's root folder, so that anyone 
cloning your project can just run `./amper build` and start working right away — that's it.
No installation needed, no matter their OS.

## What's the wrapper script?

The **Amper wrapper script** is a small file (`amper` or `amper.bat`) that downloads and runs the actual Amper CLI 
application[^1], and serves as an entry point for all Amper commands.

Of course, the Amper CLI application is only downloaded once (per version) and subsequent calls to the wrapper 
immediately delegate to it.

[^1]: The Amper CLI is, at the moment, a JVM application. The Amper distribution is therefore a bunch of JAR files, and
      they need a Java Runtime Environment (JRE) to run. This is an implementation detail and may change in the future,
      so you should not rely on it.

## Concurrency

The provisioning mechanism and all relevant behaviors in Amper are designed to be safe to use concurrently.
This means you can run as many Amper commands as you want in parallel, and they won't disturb each other.

## Bootstrap cache location

By default, when downloading the Amper distribution, the wrapper script places it in a cache directory that is suitable
for the current OS:

| OS                                   | Cache directory                        |
|--------------------------------------|----------------------------------------|
| :material-apple: macOS               | `$HOME/Library/Caches/JetBrains/Amper` |
| :material-linux: Linux               | `$HOME/.cache/JetBrains/Amper`[^2]     |
| :material-microsoft-windows: Windows | `%LOCALAPPDATA%\JetBrains\Amper`       |

[^2]: The XDG convention is not supported at the moment for the bootstrap cache. 
      It is, however, respected for the regular Amper cache.

This location can be customized by setting the `AMPER_BOOTSTRAP_CACHE_DIR` environment variable.

## Disabling the welcome banner

When the wrapper script downloads a distribution for the first time, it displays a welcome message to the user.
This might be too much output if you're running Amper in a CI environment, and provisioning the distribution on every
build.

You can disable the welcome banner by setting the `AMPER_NO_WELCOME_BANNER` environment variable to a non-empty value.
For instance, `AMPER_NO_WELCOME_BANNER=1`.

## Uncharted customization territories

!!! danger "Use at your own risk"

    While Amper currently is a JVM application, this may change in the future and all the functionality below will break
    without notice (for instance, we could publish Amper as a GraalVM native image).

    Moreover, using these customization features is generally not recommended and may break Amper in unexpected ways, 
    including the Amper update mechanism.

### Customizing Amper's own JVM options

To add JVM options to the JVM running Amper, use the `AMPER_JAVA_OPTIONS` environment variable.

### Customizing the JRE used to run Amper

The Amper runtime is not provisioned if the `AMPER_JAVA_HOME` environment variable is already provided.
Customizing this variable prevents the auto-provisioning of a JRE by Amper, but it puts the responsibility on you
to provide a valid JRE. The requirements for the JRE are subject to change without notice and are not documented at the
moment.

You can look inside the wrapper scripts to see which JRE is provisioned.

### Customizing the Amper distribution URL

The Amper distribution is downloaded from a Maven repository by fetching the following URL:
```
$AMPER_DOWNLOAD_ROOT/org/jetbrains/amper/amper-cli/$amper_version/amper-cli-$amper_version-dist.tgz
```
By default, `$AMPER_DOWNLOAD_ROOT` is `https://packages.jetbrains.team/maven/p/amper/amper`.
Changing this variable allows you to use your own Maven repository to host the Amper distribution.

This is, again, not recommended — please use with care.
