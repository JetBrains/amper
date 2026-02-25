---
description: |
  Part of Kargo's philosophy is to avoid the hassle of setting up toolchains, including the JDK and Kargo 
  itself. This is why we recommend checking the Kargo wrapper script into your project's root folder.
---
# Wrapper script & provisioning

Part of Kargo's philosophy is to avoid the hassle of setting up toolchains, including the JDK and Kargo itself.

The recommended way to use Kargo is to check the Kargo wrapper script into your project's root folder, so that anyone 
cloning your project can just run `./amper build` and start working right away — that's it.
No installation needed, no matter their OS.

## What's the wrapper script?

The **Kargo wrapper script** is a small file (`amper` or `amper.bat`) that downloads and runs the actual Kargo CLI 
application[^1], and serves as an entry point for all Kargo commands.

Of course, the Kargo CLI application is only downloaded once (per version) and subsequent calls to the wrapper 
immediately delegate to it.

[^1]: The Kargo CLI is, at the moment, a JVM application. The Kargo distribution is therefore a bunch of JAR files, and
      they need a Java Runtime Environment (JRE) to run. This is an implementation detail and may change in the future,
      so you should not rely on it.

## Concurrency

The provisioning mechanism and all relevant behaviors in Kargo are designed to be safe to use concurrently.
This means you can run as many Kargo commands as you want in parallel, and they won't disturb each other.

## Bootstrap cache location

By default, when downloading the Kargo distribution, the wrapper script places it in a cache directory that is suitable
for the current OS:

| OS                                   | Cache directory                        |
|--------------------------------------|----------------------------------------|
| :material-apple: macOS               | `$HOME/Library/Caches/JetBrains/Kargo` |
| :material-linux: Linux               | `$HOME/.cache/JetBrains/Kargo`[^2]     |
| :material-microsoft-windows: Windows | `%LOCALAPPDATA%\JetBrains\Kargo`       |

[^2]: The XDG convention is not supported at the moment for the bootstrap cache. 
      It is, however, respected for the regular Kargo cache.

This location can be customized by setting the `AMPER_BOOTSTRAP_CACHE_DIR` environment variable.

## Disabling the welcome banner

When the wrapper script downloads a distribution for the first time, it displays a welcome message to the user.
This might be too much output if you're running Kargo in a CI environment, and provisioning the distribution on every
build.

You can disable the welcome banner by setting the `AMPER_NO_WELCOME_BANNER` environment variable to a non-empty value.
For instance, `AMPER_NO_WELCOME_BANNER=1`.

## Uncharted customization territories

!!! danger "Use at your own risk"

    While Kargo currently is a JVM application, this may change in the future and all the functionality below will break
    without notice (for instance, we could publish Kargo as a GraalVM native image).

    Moreover, using these customization features is generally not recommended and may break Kargo in unexpected ways, 
    including the Kargo update mechanism.

### Customizing Kargo's own JVM options

To add JVM options to the JVM running Kargo, use the `AMPER_JAVA_OPTIONS` environment variable.

### Customizing the JRE used to run Kargo

The Kargo runtime is not provisioned if the `AMPER_JAVA_HOME` environment variable is already provided.
Customizing this variable prevents the auto-provisioning of a JRE by Kargo, but it puts the responsibility on you
to provide a valid JRE. The requirements for the JRE are subject to change without notice and are not documented at the
moment.

You can look inside the wrapper scripts to see which JRE is provisioned.

### Customizing the Kargo distribution URL

The Kargo distribution is downloaded from a Maven repository by fetching the following URL:
```
$AMPER_DOWNLOAD_ROOT/org/jetbrains/amper/amper-cli/$amper_version/amper-cli-$amper_version-dist.tgz
```
By default, `$AMPER_DOWNLOAD_ROOT` is `https://packages.jetbrains.team/maven/p/amper/amper`.
Changing this variable allows you to use your own Maven repository to host the Kargo distribution.

This is, again, not recommended — please use with care.
