---
description: |
  Our philosophy is that you should be able to run your project without manually installing anything on your machine, 
  setting `JAVA_HOME`, or configuring anything. This is why Amper is able to provision a JDK automatically for you.
  This page describes how this can be configured to suit your needs.
---
# JDK provisioning

Amper needs a JDK (Java Development Kit) for various things in your project:

* compile Kotlin and Java sources
* run tests
* run JVM apps
* run a delegated Gradle build for Android bytecode processing
* ...and more

Our philosophy is that you should be able to run your project without manually installing anything on your machine, 
setting `JAVA_HOME`, or configuring anything. This is why Amper is able to provision a JDK automatically for you.

For many projects, it is important to be able to control the JDK version and sometimes even the distribution.
This page describes how this can be configured. 

## Default behavior

By default, Amper doesn't constrain the JDK distribution, but it expects a specific major version: **currently 21**.

Since the default [selectionMode](#jdk-selection-mode) is `auto`, Amper will look for a JDK 21 in 
`JAVA_HOME`, and if not found, will provision one.

## JDK requirements

Amper will always use a JDK that matches the requirements you configure in your module file, if it can't, the build 
will fail.
Even if nothing is explicitly configured, Amper will provision a JDK that matches the default requirements, or fail.

You can currently customize 2 criteria:

* the **major version** (e.g., 11, 21, 25)
* the acceptable **distributions** (e.g., Temurin, Zulu)

Here is how it looks:

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 21  # major JDK version
      distributions: [temurin, zulu]  # optional allowlist of distributions (accept all if omitted)
```

??? question "Can I use an exact JDK version, like `17.0.2+8`?"

    Yes, but not via the provisioning settings. It is unclear how to reliably work with such a configuration, because 
    exact versions/patches differ between vendors and even between OSes for the same vendor.

    If you want to control the exact version, you can use the `JAVA_HOME` environment variable to point to a specific 
    JDK, and make sure the JDK requirement settings match this JDK. You can also disable the provisioning by setting 
    `settings.jvm.jdk.selectionMode: javaHome` to ensure `JAVA_HOME` is used, and not a fallback. Read below about how 
    the JDK discovery works.

??? question "Why a list of distributions?"

    Some vendors don’t publish all versions of their distributions on all platforms.
    Because of this, even if you have a preference for one JDK distribution, you may have to accept another one as 
    fallback so that other developers can work on platforms that your preferred JDK doesn't support.

    For example, Amazon Corretto doesn't support Windows ARM64 machines at the moment, so you might want to set a 
    fallback to Microsoft's JDK for this case:

    ```yaml
    settings:
      jvm:
        jdk:
          distributions: [corretto, microsoft]
    ```

## JDK selection mode

Based on the requirements we've seen above, Amper will make sure that a matching JDK is available for the build.

There are 3 ways it can do this, which you can choose from via `settings.jvm.jdk.selectionMode`:

* `auto` (default): Amper will use the JDK from `JAVA_HOME` if it matches the requirements, otherwise it will 
  provision a JDK.
* `alwaysProvision`: Amper will always provision a JDK, even if `JAVA_HOME` matches the requirements.
  This setting improves the consistency between builds across machines.
* `javaHome`: Amper will exclusively use `JAVA_HOME`, and thus fail the build if `JAVA_HOME` does not match the
  requirements. In this mode, auto-provisioning is effectively disabled.

### How requirements are checked

When Amper is configured to attempt using `JAVA_HOME`, it reads the `release` file present in all modern JDKs, which
contains the JDK version and vendor. From that file, Amper checks that the major version matches the requested one, and
that the vendor is in the allowed `distributions`.

If the `release` file is not present (for instance, in an old JDK), the requirements are considered unsatisfied, and 
the consequence depends on the selection mode (in `auto` mode, Amper will provision a JDK; in `javaHome` mode, the 
build will fail).

## Provisioning mechanism

When Amper decides to provision a JDK, it uses the [Foojay Discovery API](https://github.com/foojayio/discoapi) to 
find the latest available JDK for the requested major version on your current OS/architecture, for each of the accepted
distributions (if specified).

Among all JDKs found, Amper will prefer the distribution that appears first in `settings.jvm.jdk.distributions` or in
the default list. The default list is ordered this way:

- Eclipse Temurin, a.k.a. Adoptium
- Azul Zulu
- Amazon Corretto
- JetBrains Runtime
- Oracle OpenJDK
- Microsoft
- BiSheng
- Alibaba Dragonwell
- Tencent Kona
- BellSoft Liberica
- Perforce OpenLogic
- SapMachine
- IBM Semeru Open Edition
- Oracle JDK (:warning: requires license)
- Azul Zulu Prime (:warning: requires license)
- IBM Semeru Certified (:warning: requires license)

## Licensing

Some JDK vendors require a commercial license for using some of their distributions in production.
Amper will let you know if you're trying to use such a distribution, and won't let you do it by accident.

If you want to use Amper with such a distribution, you must make sure you understand the terms of the license, and have
the appropriate contracts or agreements with the vendor.

If you do, acknowledge the license by adding the distribution name to `settings.jvm.jdk.acknowledgedLicenses`.

## Examples

### Specific major version

With the following configuration, Amper will use the `JAVA_HOME` JDK if it's any JDK 17.
If not, it will find the latest patch of JDK 17 in the first distribution of the default list, which means it will find
Temurin 17 (at least at the time of writing, when Temurin 17 is still available).

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 17
      distributions: [temurin, zulu]
```

### Limited distributions

With the following configuration, Amper will use the `JAVA_HOME` JDK if it's Amazon Corretto or Microsoft JDK 21.
If not, provision the latest patch of Amazon Corretto 21, or fall back to Microsoft 21 if not found (for example on a
Windows ARM64 machine).

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 21
      distributions: [corretto, microsoft]
```

### One specific commercial distribution

Require Oracle JDK 21 and acknowledge its license. Find it in `JAVA_HOME` or provision it if `JAVA_HOME` is not 
suitable.

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 21
      distributions: [oracle]
      acknowledgedLicenses: [oracle]
```

### One specific full JDK version

Manually place the specific `21.0.9+7-LTS-338` version of the Oracle JDK in `JAVA_HOME`, and ensures Amper uses it:

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 21
      distributions: [oracle]
      selectionMode: javaHome # (1)!
      acknowledgedLicenses: [oracle] # (2)!
```

1.   Ensures Amper never provisions another JDK, just fail if the machine is misconfigured
2.   Tell Amper that we know about Oracle's commercial license and accept it

### Ignoring `JAVA_HOME`

Always provision Corretto 21 regardless of JAVA_HOME

```yaml title="module.yaml"
settings:
  jvm:
    jdk:
      version: 21
      distributions: [corretto]
      selectionMode: alwaysProvision
```
