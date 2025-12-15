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