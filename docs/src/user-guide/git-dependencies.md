---
description: |
  Learn how to set up and use Git dependencies (sources) in your Kargo projects, including caching strategies, reproduciblity, and offline fallbacks.
---
# Git Dependencies

Often, you need to depend on a library that is not published to a Maven repository, or you want to depend on a specific fork or unreleased commit of an open-source project. Kargo allows you to declare dependencies directly on Git repositories.

Instead of declaring these in the `dependencies` block, Git dependencies are declared in a `sources` block in your `module.yaml`. Kargo will automatically clone the repository, check out the requested version, build the project locally, and link the resulting binaries into your compilation.

!!! note "Supported Build Systems"
    Kargo's source resolver natively builds projects configured with **Kargo** (`module.yaml`). However, it maintains full backward compatibility and is seamlessly capable of compiling libraries that still use the original **JetBrains Kargo**. Both formats are compiled using the same embedded Kargo engine on the fly.

## Declaring Git dependencies

To depend on a Git repository, add a `sources` list to your `module.yaml` file:

```yaml title="module.yaml"
sources:
  - github: kargo-build/kargo-native-git-lib #(1)!
    version: main #(2)!
```

1. Shorthand for a GitHub repository. This is equivalent to `url: https://github.com/kargo-build/kargo-native-git-lib.git`.
2. The Git reference to check out. This can be a branch name (`main`), a tag (`v1.4.2`), or a full commit SHA.

Once defined in the `sources` block, the source code and artifacts from the Git dependency will automatically be included in your module's classpath.

### Anatomy of a Git dependency

A Git dependency requires the following attributes:

| Attribute | Description |
|-----------|-------------|
| `git` | The full HTTP(s) or SSH URL of the Git repository. |
| `github` | A shorthand for `git` pointing to GitHub (e.g., `user/repo`). You can use this *instead* of `git`. |
| `gitlab` | A shorthand for GitLab (e.g., `group/repo`). |
| `bitbucket` | A shorthand for Bitbucket (e.g., `user/repo`). |
| `version` | **(Required)** The target branch, tag, or commit SHA to resolve. |
| `path` | *(Optional)* If the Kargo project is not at the root of the repository, specify the relative path to the directory containing the `module.yaml`. |
| `publishOnly` | *(Optional)* If true, builds and publishes artifacts without injecting them as dependencies (default: `false`). |
| `platforms` | *(Optional)* List of specific platforms to build (defaults to all platforms supported by the source). |

Example using full `git` URL and a specific `path`:

```yaml title="module.yaml"
sources:
  - git: ssh://git@server.com/internal-library.git
    version: develop
    path: core-module
```

## Caching & Reproducibility

Cloning and building Git dependencies on every run would be slow. Kargo intelligently caches the built artifacts (such as `.klib` files) locally in `~/.kargo/sources-cache/` to ensure builds remain fast.

### Cache Directory Structure

When a Git dependency is built, Kargo stores the persistent result in `~/.kargo/sources-cache/<hash>/`. Inside this directory, you will find:
- `artifacts/`: The actual compiled binaries (e.g., `.klib` files) that Kargo links against in subsequent builds.
- `repo/`: The full downloaded Git repository source code.
- `metadata.json`: Internal tracking information containing the original version requested and the exact resolved commit SHA.

How the cache behaves over time depends on the type of `version` you requested:

### Commit SHAs (Immutable)
If you provide a full 40-character commit SHA (e.g., `version: a3f9c12e8b1d4f07c3e92a5d6f1b8e4c7d2a9f0e`), Kargo treats this reference as strictly **immutable**. After the first build, the binaries are permanently cached. Subsequent builds will reuse the cache silently.

**This is the recommended approach for reproducible builds in CI/CD environments.**

### Semver Tags (Immutable)
Like SHAs, semantic version tags (e.g., `version: v1.0.0` or `version: 2.3.1-beta`) are treated as **immutable** by convention. Kargo will cache these permanently after the first build without attempting to re-fetch or re-validate the tag from the remote repository.

### Branches and Mutable Refs 
If you specify a branch name (e.g., `version: main` or `develop`), Kargo treats it as a **mutable reference**. 
Because a branch can receive new commits at any time, Kargo needs to ensure you are building against the latest code.

1. **First run**: Kargo clones the repository, checks out the branch, and resolves the real commit SHA at `HEAD`. It then builds the dependency and caches it along with the resolved SHA.
2. **Subsequent runs**: Kargo performs a fast remote check to see if the branch has moved upstream.
    - If the remote SHA matches the locally cached SHA, Kargo **silently reuses the cache**.
    - If the remote SHA has changed, Kargo **invalidates the cache**, fetches the new commits, and rebuilds the dependency.
    - An informative `WARN` will be printed to remind you that using a mutable ref may lead to non-reproducible builds across different machines.

### Offline Fallback

If you are working offline (e.g., no internet connection) and are using a **mutable ref** like `main`, Kargo's remote check will fail. 

Instead of failing your entire build, Kargo gracefully falls back to using the last known cached build for that branch. It will emit a warning letting you know it couldn't reach the network and is using a stale cache, allowing your local development workflow to continue uninterrupted.
