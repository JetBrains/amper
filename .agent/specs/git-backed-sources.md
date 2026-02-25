# Git-backed Sources for Kargo

## Status

Experimental / Design Validation

This document specifies a feature for Kargo (Amper fork) that enables consuming Git repositories as artifact sources, inspired by Go Modules and Crystal shards.

Sources are built locally using Kargo and automatically injected as dependencies, unless explicitly configured otherwise.

---

## Motivation

Kargo currently consumes dependencies exclusively as prebuilt artifacts from Maven-compatible repositories.

This limits Git-first workflows, tight iteration between related projects, and native/multiplatform-centric development.

Go and Crystal demonstrate that Git repositories can act as reliable dependency sources when references are explicit and builds are deterministic.

This proposal explores that model while preserving Kargo’s existing architecture and dependency semantics.

---

## Goals

- Enable Git repositories as artifact sources
- Use Kargo-only builds for external projects
- Preserve deterministic builds
- Avoid changes to Kargo core architecture (where possible)
- Avoid duplicate dependency declarations
- Avoid mandatory project-level lockfiles

---

## Explicit Scope Constraints

### Amper-only Projects

Only Git repositories built with Kargo are supported.

A source repository MUST:
- Contain a valid `module.yaml`
- Be buildable via the Kargo CLI
- Publish its artifacts explicitly

Other build systems are out of scope.

---

## Non-Goals

This plugin does not attempt to:
- Replace Maven repositories
- Add Git URLs to the dependency DSL
- Resolve transitive Git sources
- Support non-Kargo projects
- Perform implicit updates
- Define a stable public API

---

## Conceptual Model

A source represents a complete external Kargo project.

Sources are artifact producers.

By default, declaring a source also makes its primary published artifacts available to the consuming project.

High-level flow:

1. Clone Git repository
2. Resolve explicit `version` to a concrete commit
3. Build using Kargo
4. Collect published artifacts
5. Publish artifacts to a local repository
6. Inject artifacts as resolved dependencies

Kargo itself remains unaware of Git in its core dependency resolution logic, delegating this to the source resolver.

---

## Configuration

## Configuration

### Definition Location

Sources are defined in a dedicated `sources:` block within `module.yaml`.

This separates source definitions from standard Maven dependencies.

---

## Example

```yaml
# module.yaml
product: ...

sources:
  # Short-hand syntax (GitHub)
  - github: JetBrains/kotlin
    version: v1.9.20
    
  # Short-hand syntax (GitLab)
  - gitlab: my-group/my-project
    version: main
    
  # Short-hand syntax (Bitbucket)
  - bitbucket: my-org/my-repo
    version: commit-hash-123
    # Optional fields are supported for all types
    path: subdir/project
    publish-only: true

  # Full version (Custom Git URL)
  - git: ssh://git@private.server.com:22/repo.git
    version: develop
    path: subdir/project # optional
    publish-only: true   # optional, default false
```

---

## Configuration Fields

### Sources Block

The `sources` list accepts two types of definitions: **Short-hand** and **Full version**.

### Common Optional Fields (All Types)

The following fields are supported for both **Short-hand** and **Full version** sources:

- `path`: (Optional) Subdirectory within the repository containing the Kargo project
- `publish-only`: (Optional) `true` to build and publish artifacts without injecting them as dependencies (default: `false`)

### Short-hand Syntax (Known Hosts)

Used for public repositories on common platforms.

Keys:
- `github`: `user/repo`
- `gitlab`: `group/project`
- `bitbucket`: `user/repo`
- `version`: branch, tag, or commit hash

### Full Version Syntax (Generic Git)

Used for private repositories, self-hosted instances, or raw Git URLs.

Keys:
- `git`: Full Git URL (HTTPS or SSH)
- `version`: branch, tag, or commit hash

---

## Behavior

1. **Short-hand**: The plugin resolves the URL automatically based on the host provider.
2. **Full version**: The plugin uses the provided URL directly.
3. **Injection**: Unless `publish-only: true`, artifacts are automatically added to the compile classpath.

---

## Reference Resolution

## Reference Resolution

- `version` is mandatory
- All versions resolve to a concrete commit
- No floating or implicit updates are allowed
- The resolved commit is the single source of truth

---

## Determinism Guarantees

Determinism is ensured through:
- Explicit `version` refs (tag/commit)
- Commit pinning
- Isolated builds
- Platform-scoped artifacts
- Deterministic cache keys

Identical inputs always produce identical outputs.

---

## Cache Model

Artifacts are cached locally using keys derived from:
- Repository URL
- Resolved commit hash
- Build configuration fingerprint
- Target platform

### Cache Layout Example

```
~/.kargo/sources-cache/
  mylib/
    9f3a21d/
      metadata.json
      jvm/
        mylib-core-1.4.2.jar
```

### Metadata

Stored metadata includes:
- Repository URL
- Original version (ref)
- Resolved commit hash
- Build configuration hash
- Build timestamp

This metadata acts as an implicit, cache-scoped lock.

No project files are generated.

---

## Artifact Injection

The plugin uses **Direct Injection** into the compiler arguments.

1. Sources are built in isolation.
2. Resulting artifacts (specifically `.klib` for Kotlin Native, or `.jar` for JVM) are located in `~/.kargo/sources-cache/...`.
3. These absolute paths are passed directly to the Kargo compiler invocation.
4. Transitive dependencies must be resolved by the source build itself.

This avoids polluting `mavenLocal` and ensures strict isolation.

---

## Publish-only Mode

When `publishOnly: true` is set:
- Artifacts are published locally
- No dependencies are injected
- The user may declare dependencies manually if desired

This acts as an explicit opt-out from automatic consumption.

---

## Build Execution

- Clean Git checkout per source
- Isolated build execution
- Kargo CLI invocation only
- No module-level filtering

---

## Error Handling

Errors are terminal and explicit:
- Missing `version`
- Invalid repository
- Missing `module.yaml`
- Build failures
- Artifact ambiguity
- Cache corruption

No silent fallbacks are permitted.

---

## Security Considerations

- Only Kargo builds are executed
- No arbitrary scripts
- Explicit, user-controlled repositories

---

## Related Ecosystems

- Go Modules
- Crystal shards
- Rust Cargo (reference model)

All validate Git-based, deterministic dependency workflows.

---

## Future Work (Non-binding)

- **Locking mechanism**: Concurrent builds should not corrupt the cache (currently deferred).
- **Private Repositories**: SSH agent or credential helper support (currently deferred).
- **Transitive Source Resolution**: Handling dependencies of dependencies.
- Source graph visualization

---

## Summary

This document defines a Kargo feature that introduces Git-backed sources by building Kargo projects locally and automatically injecting their artifacts as dependencies, while preserving Kargo’s existing dependency model and determinism guarantees.
