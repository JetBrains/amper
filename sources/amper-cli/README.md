See the [instructions how to use Amper CLI](../../docs/Usage.md#using-amper-from-the-command-line) 

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
./amper tool jaeger
```

### Profiling

Amper has an embedded profiling feature, so you can enable the 
[async-profiler](https://github.com/async-profiler/async-profiler)
right from the command line and collect a .jfr profiling snapshot.
Drag and drop .jfr file to IntelliJ Ultimate window to analyze the snapshot.

```
./amper --profile build
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

It is possible to use Amper CLI built from sources, please check out the Amper project and use 
`./amper-from-sources` instead of `./amper` (or `./amper-from-sources.bat` instead of `./amper.bat`).
