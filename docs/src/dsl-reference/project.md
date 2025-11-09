# Project file

## Modules list

The `modules:` section lists all the modules in the project, except the root module.
If a `module.yaml` is present at the root of the project, the root module is implicitly included and doesn't need to be
listed.

Each element in the list must be the path to a module directory, relative to the project root.
A module directory must contain a `module.yaml` file.

Example:

```yaml
# include the `app` and `lib1` modules explicitly:
modules:
  - ./app
  - ./libs/lib1
```

You can also use [Glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)) to include multiple module
directories at the same time.
Only directories that contain a `module.yaml` file will be considered when matching a glob pattern.

- `*` matches zero or more characters of a path name component without crossing directory boundaries
- `?` matches exactly one character of a path name component
- `[abc]` matches exactly one character of the given set (here `a`, `b`, or `c`). A dash (`-`) can be used to match a range, such as `[a-z]`.

Using `**` to recursively match directories at multiple depth levels is not supported.

Example:

```yaml
# include all direct subfolders in the `plugins` dir that contain `module.yaml` files:
modules:
  - ./plugins/*
```
