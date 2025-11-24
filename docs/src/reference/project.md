# Project file reference

## `modules`

The `modules` section defines which modules are part of this Amper project.

Each list element must be the path to a module directory[^1], relative to the project root. 
If a `module.yaml` is present in the project root, that root module is always included implicitly and doesn’t need to 
be listed.

[^1]: That is, a directory that directly contains a `module.yaml`

Example:

```yaml
# include the `app` and `lib1` modules explicitly:
modules:
  - ./app
  - ./libs/lib1
```

You can also use [glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)) to include multiple module
directories at once. Only directories that contain a `module.yaml` file are taken into account:

```yaml
# include all direct subfolders in the `plugins` dir that contain `module.yaml` files:
modules:
  - ./plugins/*
```

Globs may contain the following special characters:

- `*` matches zero or more characters of a path name component without crossing directory boundaries
- `?` matches exactly one character of a path name component
- `[abc]` matches exactly one character of the given set (here `a`, `b`, or `c`). A dash (`-`) can be used to match a range, such as `[a-z]`.

!!! failure "Using `**` to recursively match directories at multiple depth levels is not supported."

## `plugins`

The `plugins` section lists plugin dependencies that should be made available to project modules.
Listing a plugin here does not enable it by itself; it only makes it available so that modules can opt in (by enabling 
the plugin).

Example:
```yaml
plugins:
  - ./my-plugin
  - ./plugins/my-another-plugin
```

!!! info
    Currently, only dependencies on **local** plugin modules are supported here (Amper doesn't support publishing 
    plugins yet, ).
    Entries use the same notation as [module dependencies](../user-guide/dependencies.md#module-dependencies).

To enable and configure a plugin for a specific module, use the `plugins` block in that module:

```yaml title="app/module.yaml"
plugins:
  my-plugin:
    enabled: true
    # plugin-specific settings here
```

Learn more about the [plugin structure](../plugins/topics/structure.md).