# Amper CLI

Amper CLI is a pure Amper command line interface that allows you to run Amper projects without using Gradle.

## Prerequisites

To use Amper CLI, you need to have the following:

- A `curl` installed on your system
- A valid Amper project

## Installation

To install Amper CLI, you need to download the wrapper script to your project's root folder. The wrapper script is 
a small file that downloads and runs the actual Amper CLI distribution.

Depending on your operating system, use one of the following commands to download the wrapper script:

### Linux/mac

```
curl -fsSL -o amper.sh "https://packages.jetbrains.team/maven/p/amper/amper/org/jetbrains/amper/cli/0.3.0-dev-487/cli-0.3.0-dev-487-wrapper.sh?download=true" && chmod +x amper.sh
```

### Windows (powershell)

```
Invoke-WebRequest -Uri https://packages.jetbrains.team/maven/p/amper/amper/org/jetbrains/amper/cli/0.3.0-dev-487/cli-0.3.0-dev-487-wrapper.bat?download=true -OutFile amper.bat
```

Alternatively, you can use any other method to download the wrapper script from the Maven repository, as long as you 
save it with the correct name and extension.

## Usage

To use Amper CLI, you need to run the wrapper script from your project's root folder. The wrapper script will download
the appropriate Amper CLI distribution for your system and execute it.

To run the wrapper script, use one of the following commands, depending on your operating system:

### Linux/mac

```
./amper.sh
```

### Windows (powershell)

```
./amper.bat
```

The first time you run the wrapper script, it will take some time to download the JDK and Amper CLI distribution. 
Subsequent runs will be faster, as the downloaded files will be cached locally.

To explore what is possible using Amper CLI, run the following command:

```
./amper.sh --help
```

This will show you the available options and parameters for the Amper CLI command.

## Troubleshooting

### Debug logs

Please refer to `amper-*-debug.log` and `amper-*-info.log` files under `build/logs` directory

### Deadlocks

If Amper does not write anything to DEBUG log for a minute, it will report current threads stacktrace and
current kotlin coroutines to `thread-dump-*.txt` files under `build/logs` directory

### OpenTelemetry traces

Amper provides information what exactly was executed and when.
Corresponding logs are written to `amper-*-jaeger.json` files under `build/logs` directory.

Traces format is very useful to understand exact compiler options or how tasks run in parallel.

To open and analyze traces, run [jaeger](https://www.jaegertracing.io) service,
navigate to [http://localhost:16686](http://localhost:16686),
go to `Search -> Upload` section to upload and explore `amper-*-jaeger.json` files.

Execute the following command to run [jaeger](https://www.jaegertracing.io):

```
./amper.sh tool jaeger
```

### Profiling

Amper has embedded profiling feature, so you can [async-profiler](https://github.com/async-profiler/async-profiler)
right from command line and collect .jfr profiling snapshot.
Drag and drop .jfr file to IntelliJ Ultimate window to analyze the snapshot.

```
./amper.sh --async-profiler build
```

### Reporting issues
Please report issues to our [YouTrack](https://youtrack.jetbrains.com/issues/AMPER) tracker. Provide as much information 
as possible, such as:
- Your operating system and architecture
- The Amper version
- The Amper project files
- The console output and error messages
- (optional, but advised) logs from `build/logs` directory
- The steps to reproduce the issue

## Running Amper CLI from sources

It is possible to use Amper CLI built from sources, please check out the Amper project
and use the following instead of `amper.sh` or `amper.bat`:

```
/path/to/amper/checkout/sources/cli/amper-from-sources.sh
```