# Spring Boot

To enable Spring boot support, add the following to the `module.yaml` file:

```yaml

settings:
  springBoot: enabled
```

Setting `springBoot: enabled` performs the following actions:

*   Applies the Spring Dependencies BOM
*   Adds the `spring-boot-starter` dependency
*   Adds the `spring-boot-starter-test` test dependency
*   Configures `all-open` and `no-arg` Kotlin compiler plugins with the `spring` preset
*   Adds the necessary compiler arguments for `kotlinc` and `javac`
*   Contributes Spring Boot-related entries to the built-in library catalog

Mixed projects (containing java and kotlin sources simultaneously) are supported.

Examples of Spring Boot projects:

* [spring-petclinic]({{ examples_base_url }}/spring-petclinic)
* [spring-petclinic-kotlin]({{ examples_base_url }}/spring-petclinic-kotlin)
