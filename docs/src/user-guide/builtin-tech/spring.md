# :spring-boot: Spring Boot

[Spring Boot](https://spring.io/projects/spring-boot) is a framework that simplifies the creation of stand-alone,
Spring based applications.

To enable Spring Boot support, add the following to the `module.yaml` file:
```yaml
settings:
  springBoot: enabled
```

Setting `springBoot: enabled` performs the following actions:

* Applies the [Spring Boot Dependencies BOM](https://mvnrepository.com/artifact/org.springframework.boot/spring-boot-dependencies)
* Adds the `spring-boot-starter` dependency
* Adds the `spring-boot-starter-test` test dependency
* Configures `all-open` and `no-arg` Kotlin compiler plugins with the `spring` preset
* Adds the necessary compiler arguments for `kotlinc` and `javac`:
  * For Java, `-parameters` is passed to the compiler to preserve parameter names.
  * For Kotlin, `-java-parameters` is passed to the compiler for the same reason. Also `-Xjsr305` is set to `strict`
    to favor the null-safety annotations.
* Contributes Spring Boot-related entries to the built-in library catalog
* Makes `amper run` run with classes instead of JARs (aka the `jvm.runtimeClasspathMode` setting).  
  This way the [Spring Dev Tools](https://docs.spring.io/spring-boot/reference/using/devtools.html) can provide automatic restarts.

Mixed projects (containing Java and Kotlin sources simultaneously) are supported.

Examples of Spring Boot projects:

* [spring-petclinic]({{ examples_base_url }}/spring-petclinic)
* [spring-petclinic-kotlin]({{ examples_base_url }}/spring-petclinic-kotlin)

You can also customize the version of the Spring Boot libraries using the full form of the configuration:
```yaml
settings:
  springBoot:
    enabled: true
    version: 3.5.7
```

If you don't want the Spring Boot Dependencies BOM to be applied, you can disable it explicitly:
```yaml
settings:
  springBoot:
    enabled: true
    applyBom: false
```