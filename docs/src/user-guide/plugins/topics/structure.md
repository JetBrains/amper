# Plugin structure

A plugin is a standard Amper module with the `jvm/amper-plugin` product type. 
It has a regular `module.yaml` build file with an additional [`pluginInfo` section](../../../reference/module.md#plugininfo).

In addition, a plugin has a `plugin.yaml` file, where [tasks](tasks.md) are registered and configured.

Plugin build logic is written in Kotlin in the `src` directory.

??? question "Planned: simple cases – simpler structure"
    There can be simpler cases for custom build logic for which having a full-blown plugin may be overkill.
    For example, a single ad-hoc task action that is only needed in one module.

    We are planning to improve UX for such cases and provide a more laconic way to express them.

Plugin has an **ID**, which equals to its module name by default.
The **plugin ID** is used across the project to refer to the plugin, e.g., when enabling it or in diagnostic messages.

??? info "Plugin ID can be customized"
    For example:
    ```yaml title="build-config/module.yaml"
    pluginInfo:
      id: "build-konfig"
    ```
    This changes the ID from the default `build-config` to `build-konfig`.

    :warning: Changing the default ID makes little sense if we are not sharing the plugin between projects, which is not supported yet.
    Currently, the ID string doesn't have well-defined format, so leaving the ID at its default is a good idea. 

## Making plugins available in the project

Amper plugins are module-level plugins – they are enabled and applied per module.
There is no such concept as a project-wide plugin in Amper.

To make a plugin available in the project,
the dependency on it must be listed under the [`plugins`section](../../../reference/project.md#plugins) of the `project.yaml` file:
```yaml
modules:
  - ...
  - plugins/my-plugin

plugins:
  - ./plugins/my-plugin
```

Plugins listed this way are not yet enabled anywhere.
One must enable them manually in each module where they are needed.

There may be cases where a plugin is developed as part of the project, but not needed _in the project itself_.
Then such a plugin is present in the `modules` list, but not included in the `plugins` list.

??? note "Similar to `apply false` in Gradle..."
    Amper's approach to listing plugins project-wide and applying them per-module is somewhat similar to the 
    recommended approach in Gradle. There one lists plugins at the project level with the `apply false` clause and
    then just enables them where needed.

## Enabling plugins

Plugins can be enabled and [configured](configuration.md#plugin-settings) like this:

=== "module.yaml (shorthand)"

    Just to enable the plugin and leave the default configuration:
    ```yaml
    plugins:
      <plugin-id>: enabled
    ```

=== "module.yaml (expanded)"

    To enable the plugin and configure the plugin settings:
    ```yaml
    plugins:
      <plugin-id>:
        enabled: true
        # other options
    ```

!!! tip
    If many modules use the same plugin, potentially even using some common settings for it,
    then it may make sense to use a dedicated [module template](../../templates.md)
    that enables and configures the plugin.

