# Kargo Package and Wrapper Guidelines

**Goal:** Enable safe extension and modification of Amper code in Kargo while maintaining low delta and proper package isolation under the `build.kargo` namespace.

---

## Core Principles

### 1. Namespace Isolation

- All new code must live under `build.kargo.*`.
- Do **not** modify `org.jetbrains.amper.*` directly unless absolutely necessary.

### 2. Wrappers and Extensions

- Prefer subclassing or extensions to modify behavior.
- Override only necessary methods.
- Keep backward compatibility with original Amper classes.

### 3. Direct Modifications (Last Resort)

- Only modify Amper packages when wrappers cannot achieve the required behavior.
- Document all changes explicitly:
  - README
  - CHANGELOG
  - NOTICE

### 4. Subpackage Organization

- Organize new features into clear subpackages:
  - `build.kargo.cinterop`
  - `build.kargo.native`
  - `build.kargo.plugins`
- Keep each subpackage focused on a single responsibility.

### 5. Low Delta Enforcement

- Avoid structural changes unless necessary.
- Do not rename files, reorganize directories, or reformat code unnecessarily.
- Minimize impact on original logic and public APIs.

### 6. Documentation and Testing

- Document every package and class added or modified.
- Provide tests for all new or modified behavior.
- Track differences with upstream Amper for future merges.

### 7. Upstream Integration

- Apply upstream commits selectively.
- Only bring changes that do not conflict with `build.kargo` namespaces.
- Prefer cherry-pick or rebase over full merges when possible.

---

## Examples of Usage

- Creating `CInteropTask` in `build.kargo.cinterop`.
- Wrapping `NativeLinkTask` from Amper to adjust behavior.
- Adding new plugins in isolated `build.kargo` namespaces.
- Selectively applying upstream commits to main while maintaining low delta.
