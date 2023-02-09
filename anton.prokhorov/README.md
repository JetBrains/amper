# Kmod

# How to use

## Getting started

To create a module, it's enough to create module.yaml file in module folder.
There are a lot of conventions is applied right after module created.
Standard kmod module is a Kotlin/JVM module with stdlib included as a dependency, where sources are allowed to be in 
`src/` folder.

The simplest project consists of:

```
project/
    src/
        Main.kt
    module.yaml
```

where `module.yaml` is empty.

By default, all yaml has parent. There is invisible parent for all modules which has the following content:

```yaml

parent: ~

build-for:
  - jvm

toolchain:
  kotlin-sdk: 1.8.0
  jdk:
    vendor: amzn
    version: 11.0.18
    
project-layout: plain

```

Content above seems small, however there are a lot of stuff written here:

```yaml

parent: ~  # parent discovery should stop here, ~ is null, there is no parent for this file

build-for: # here we build a hierarchy of "things" we build for. It's hard to give a name for this entity, but maybe 
           # we shouldn't
  - jvm    # this will be unfolded by template in effective module 

```

There are two important concepts mentioned here:
- templates
- effective yaml

Templates are declarative rules for performing transmutation from one part of yaml to another
For example: template for `- jvm` could be defined as:

```yaml

source: jvm
target:
  name: jvm
  default: true # for run shortcut needs (despite, run is out of scope for build system, there is shortcut for it, 
                # which asks required things from build system, for example, where classes are located)
  artifacts:
    - type: executable
      name: main-executable
      default: true
      entry-point: MainKt
```

So we can type: `kmod run` which means `kmod run jvm main-executable` because of defaults.
Convention over configuration is important concept for kmod, because build system should be invisible.

You can use regular expression as source:
```yaml

context: dependencies # context (not required) is a key in which transmutation allowed (any level of nesting)
source: .*:.*:.* # named groups are allowed too, you could reference named groups as $named-group
target:
  type: maven
  notation: $0
  scope: compile
  for: common
  hosted-by: maven-central
  exposed: false

```

Template context was introduced because there is maven notation in kotlin plugins where there is another way to unfold
the same construction

## Parenting

If your module has parent, all keys declared in parent will be applied to module. If you have the same key in module
it means you **OVERRIDE** this key.

There is also $build-root built-in variable, which allows to build absolute path to parent

```yaml

parent: ${build-root}/workspace.yaml

```

Build root determines if you have `lock.yaml`
When you run kmod CLI in folder and there is no lock.yaml if we move forward until home directory, it proposes to create
it (Like in Deft)

## Kmod expressions

There are 3 types of expressions:

- referencing other fields (including parent)

```yaml

a:
  - b
  - c
  - d

e: $a

```

effective module:

```yaml
a:
  - b
  - c
  - d

e:
  - b
  - c
  - d
```

It's also possible to use dot-notation:

```yaml

a: $a.b.c.0.e # where 0 is element of list

```

- string interpolation

```yaml

# Number of examples should be enough to understand how interpolation works
a: $b$c
b: ${b}text-in-the-middle$c
c: ${e.f.d}text-in-the-middle$x.y.z # there is no errors except of missing references or impossible yaml key
# references have to have type string, no other objects allowed

```

- list concatenation

```yaml

a: ${b + c} # behaves as reference (not string interpolation, because result is not a string)
```

example:

```yaml

common-dependencies:
  - a
  - b
  - c
    
my-dependencies:
  - e
  - f
  - g
  
dependencies: ${common-dependencies + my-dependencies}

```


## Additional notes

There is important note: you can't create plain java project, because plain java project is kotlin project without stdlib.
All you need to do is to add `no-stdlib: true` to module configuration.

Also, every possible project user creates is multiplatform by its nature.
If you need only one platform, you can just not to use common part.