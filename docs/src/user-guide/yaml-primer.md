---
description: A brief introduction to the YAML language used in Amper configuration files.
---
# Brief YAML primer

Amper uses (a subset of) the YAML language for configuration files.

YAML describes a tree of mappings and values.
Mappings have key-value pairs and can be nested.
Values can be scalars (string, numbers, booleans) and sequences (lists, sets).
YAML is indent-sensitive.

Here is a [cheat-sheet](https://quickref.me/yaml.html) and [YAML 1.2 specification](https://yaml.org/spec/1.2.2/).

Strings can be quoted or unquoted. These are equivalent:

```yaml
string1: foo bar
string2: "foo bar"
string3: 'foo bar'
```

Mapping:

```yaml
mapping-name:
  field1: foo bar
  field2: 1.2  
```

List of values (strings):

```yaml
list-name:
  - foo bar
  - "bar baz"  
```

List of mapping:

```yaml
list-name:
  - named-mapping:
      field1: x
      field2: y
  - field1: x
    field2: y
```
