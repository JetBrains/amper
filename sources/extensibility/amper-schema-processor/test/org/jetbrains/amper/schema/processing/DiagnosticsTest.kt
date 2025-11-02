/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.junit.jupiter.api.Test

class DiagnosticsTest : SchemaProcessorTestBase() {

    @Test
    fun `schema smoke`() = runTest {
        givenPluginSettingsClassName("com.example.MySettings")
        givenSourceFile(
            $$"""
typealias MyBoolean = Boolean?
typealias ListOfString = List<String>
typealias MapToListOfPath = Map<String, ListOfString>
typealias ListsSchema = Lists

enum class MyKind {
    Kind1,
    Kind2,
    Kind3,
}

interface NoSchema { val foo: Boolean }

@Configurable /*{{*/class/*}} [Amper] `@Configurable` declaration must be an interface */ NoSchema2 { val foo: Boolean }
@Configurable enum /*{{*/class/*}} [Amper] `@Configurable` declaration must be an interface */ NoSchemaEnum { FOO }

@Configurable interface Empty

@Configurable interface Lists {                 
  val enabled: Boolean
  val data: List<String>
  val data2: List<Lists>
  val aliasList: ListOfString
  val malformedType1: /*{{*/List/*}} [Amper] Unexpected schema type `List`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
  val malformedType2: /*{{*/List<Int, String>/*}} [Amper] Unexpected schema type `List`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
}

@Configurable interface Maps {
   val mapProperty1: Map<String, String>
   val mapProperty2: Map</*{{*/Int/*}} [Amper] Only `String` is allowed as a `Map` key type in `@Configurable` interfaces */, String>
   val mapProperty3: Map<String, /*{{*/NoSchema/*}} [Amper] Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */>
   val mapProperty4: Map<String, Lists>
   val mapProperty5: Map<String, Maps>
   val aliasMap: MapToListOfPath
   
   val malformedType3: /*{{*/Map/*}} [Amper] Unexpected schema type `Map`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
   val malformedType4: /*{{*/Map/*}} [Amper] Unexpected schema type `Map`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
}

@Configurable interface Nullable {
  val enum: MyKind?
  val boolean: Boolean?
  val string: String?
  val int: Int?
  val path: Path?
  val booleanAlias: MyBoolean
  val listsOptional: ListsSchema?
}

@Configurable interface WithValidDefaults {
  val boolean0: Boolean get() = false && true
  val boolean1: Boolean get() = DEFAULT_TRUE
  val boolean3 get() = true
  val string1 get() = "hello"
  val string2 get() = "hello $CONST_STR"
  val string3: String? get() = "hello" + "World"
  val string4: String? get() = /*{{*/null/*}} [Amper] No need to specify `null` explicitly: nullable properties are null by default */
  val int: Int get() = 1 + 2
  val int2: Int get() = Int.MAX_VALUE
  val listOfString get() = listOf("a", "b", "c")
  val listOfString2: List<String> get() = emptyList()
  val listOfString1: List<String?> get() = listOf(null, "hello" + "foo", CONST_STR)
  val listOfMaps: List<Map<String, String>> get() = listOf(emptyMap(), emptyMap())
  val mapOfList: Map<String, List<String>> get() = emptyMap()
  val map: Map<String, Int> get() = emptyMap()
  val obj1: Maps? get() = /*{{*/null/*}} [Amper] No need to specify `null` explicitly: nullable properties are null by default */
  val path: Path? get() = /*{{*/null/*}} [Amper] No need to specify `null` explicitly: nullable properties are null by default */
  val enum: MyKind? get() = /*{{*/null/*}} [Amper] No need to specify `null` explicitly: nullable properties are null by default */
 
  companion object {
     const val DEFAULT_TRUE = true
     const val CONST_STR = "World!"
  }
}

@Configurable interface WithInvalidDefaults {
  /*{{*/val something get() = null/*}} [Amper] Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
  /*{{*/val listOfSomething get() = listOf(null)/*}} [Amper] Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */

  val boolean1: Boolean get() = /*{{*/System.getProperty("hello") != null/*}} [Amper] Invalid primitive default expression: only simple constant expressions are allowed */                  
  // TODO: Support this case in some capacity?
  val boolean2: Boolean get() = /*{{*/boolean1/*}} [Amper] Invalid primitive default expression: only simple constant expressions are allowed */
  val int1: Int get() = /*{{*/0 / 0/*}} [Amper] Invalid primitive default expression: only simple constant expressions are allowed */
  val int2: Int? get() = /*{{*/System.getProperty("hello")?.length/*}} [Amper] Invalid primitive default expression: only simple constant expressions are allowed */
  // TODO: Support this case?
  val path: Path get() = /*{{*/kotlin.io.path.Path("foo/bar")/*}} [Amper] Defaults for `Path`s are not yet supported */
  val list1: List<String> get() = /*{{*/arrayOf("hello", "foo").toList()/*}} [Amper] Invalid list default expression: only `emptyList()` or `listOf(...)` calls are allowed */
  val list2: List<List<String>> get() = listOf(/*{{*/arrayOf("a").toList()/*}} [Amper] Invalid list default expression: only `emptyList()` or `listOf(...)` calls are allowed */)
  // TODO: Support this case
  val map: Map<String, Int> get() = /*{{*/mapOf("a" to 1, "b" to 2, "c" to 3)/*}} [Amper] Invalid map default expression: only `emptyMap()` call are allowed */
  val obj: WithValidDefaults get() = /*{{*/object : WithValidDefaults {}/*}} [Amper] Explicit defaults for `@Configurable` interfaces are not supported. Every non-nullable configurable interface is instantiated by default when all its properties are set. */
  val obj1 get() = /*{{*/object : WithValidDefaults {}/*}} [Amper] Explicit defaults for `@Configurable` interfaces are not supported. Every non-nullable configurable interface is instantiated by default when all its properties are set. */

  val enum: MyKind get() = MyKind.Kind1

  val withBlockBody: Int 
    /*{{*/get() { return 0 }/*}} [Amper] Default property getter must have an expression body */
}

@Configurable interface Invalid /*{{*/<T>/*}} [Amper] Generics are not allowed in `@Configurable` interfaces */ : /*{{*/Empty/*}} [Amper] Supertypes for user-defined `@Configurable` interfaces are reserved for future use */ {
   val foo: Boolean
   
   val /*{{*/<T>/*}} [Amper] Generics are not allowed in `@Configurable` interfaces */ /*{{*/T/*}} [Amper] Extension properties are not allowed in `@Configurable` interfaces */.withReceiver: Int
   val /*{{*/Int/*}} [Amper] Extension properties are not allowed in `@Configurable` interfaces */.withReceiver2: String get() = ""

   /*{{*/context(_: String)/*}} [Amper] Context parameters are not allowed in `@Configurable` interfaces */
   val withContext: Int
   
   /*{{*/var/*}} [Amper] Mutable properties are not allowed in `@Configurable` interfaces */ mutable: String
   
   /*{{*/fun forbidden() {
      exitProcess(0)
   }/*}} [Amper] Functions are not allowed in `@Configurable` interfaces */
   class Unrelated {
       fun allowed() {}
   }
}

// Main schema
@Configurable interface MySettings {
   val /*{{*/enabled/*}} [Amper] `enabled` property name is reserved in the plugin's settings (`{0}` is specified as the `settingsClass` in `module.yaml`) */: String
   
   val booleanProperty: Boolean
   val stringProperty: String
   val intProperty: Int
   val pathProperty: Path
   val floatProperty: /*{{*/Float/*}} [Amper] Unexpected schema type `kotlin.Float`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
   val unresolved: /*{{*/SomeOtherType/*}} [Amper] Unexpected schema type `SomeOtherType`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
   val functionType: /*{{*/() -> Unit/*}} [Amper] Unexpected schema type `() -> kotlin.Unit`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
   
   val nonSchema: /*{{*/NoSchema/*}} [Amper] Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - `Boolean`, `String`, `Int`, `Path`, enums
 - `@Configurable` interface (must be declared in the same source directory)
 - `List<T>`, `Map<String, T>`, where `T` is a supported type. */
   val empty: Empty
   val lists: ListsSchema
   val maps: Maps
}

@Configurable
/*{{*/internal/*}} [Amper] `@Configurable` interface must be public */
interface Config {
  val builtinTypeReference1: Dependency
  val builtinTypeReference2: Dependency.Local
  val builtinTypeReference3: Classpath
  val map: Map<String, List<Path>>
}

object Hello {
    /*{{*/@[JvmStatic TaskAction]
    fun nested()/*}} [Amper] `@TaskAction` function must be a top-level function */
}

@TaskAction fun /*{{*/overloaded/*}} [Amper] Illegal overload for `com.example.overloaded`: `@TaskAction` functions can't be overloaded */() {}
@TaskAction
/*{{*/private/*}} [Amper] `@TaskAction` function must be public */
fun /*{{*/overloaded/*}} [Amper] Illegal overload for `com.example.overloaded`: `@TaskAction` functions can't be overloaded */(int: Int) {}

@TaskAction
/*{{*/context(_: String)/*}} [Amper] Context parameters are not allowed in a `@TaskAction` function */
/*{{*/suspend/*}} [Amper] Suspending `@TaskAction` functions are not yet supported */ /*{{*/inline/*}} [Amper] `@TaskAction` function can't be marked as inline */ fun /*{{*/<T>/*}} [Amper] `@TaskAction` function can't be generic */ /*{{*/T/*}} [Amper] `@TaskAction` function can't be an extension function */.invalidTaskAction(
  int: Int = 0,
  map: Map<String, String> = emptyMap(),
  /*{{*/list: List<Path> = emptyList()/*}} [Amper] Parameter of a `Path`-referencing type must be annotated with either `@Input` or `@Output` */,
  @Input inputDir: Path? = /*{{*/null/*}} [Amper] No need to specify `null` explicitly: nullable properties are null by default */,
  @Output outputDir: Path,
  /*{{*/somePath: Path/*}} [Amper] Parameter of a `Path`-referencing type must be annotated with either `@Input` or `@Output` */,
  /*{{*/@Input @Output anotherPath: Path/*}} [Amper] Both `@Input` and `@Output` annotations can't be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */,
  /*{{*/@[Input Output] crazyPath: Path/*}} [Amper] Both `@Input` and `@Output` annotations can't be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */,
  /*{{*/config: Config/*}} [Amper] Parameter of a `Path`-referencing type must be annotated with either `@Input` or `@Output` */,
  @Input inputConfig: Config,
  @Output outputList: Map<String, Path>,
): /*{{*/String/*}} [Amper] `@TaskAction` function must return Unit */ {
  error("woof!")
}
"""
        )

        expectPluginData(
            javaClass.classLoader.getResourceAsStream("schema-smoke.json")!!
                .bufferedReader().useLines { it.joinToString(separator = "\n") }
        )
    }

    @Test
    fun `enum constant names`() = runTest {
        givenSourceFile("""
@Configurable interface Settings { val prop: MyEnum }
enum class MyEnum {
    MY_CONSTANT,
    MyConstant,
    `hello-3world`,
    @EnumValue("yaml-name")
    MY_CONSTANT2,
}
        """.trimIndent())

        expectPluginData("""
{
  "enums": [
    {
      "schemaName": "com.example/MyEnum",
      "entries": [
        {
          "name": "MY_CONSTANT",
          "schemaName": "MY_CONSTANT"
        },
        {
          "name": "MyConstant",
          "schemaName": "MyConstant"
        },
        {
          "name": "hello-3world",
          "schemaName": "hello-3world"
        },
        {
          "name": "MY_CONSTANT2",
          "schemaName": "yaml-name"
        }
      ]
    }
  ],
  "classes": [
    {
      "name": "com.example/Settings",
      "properties": [
        {
          "name": "prop",
          "type": {
            "type": "enum",
            "schemaName": "com.example/MyEnum"
          }
        }
      ]
    }
  ]
}
        """.trimIndent())
    }
}
