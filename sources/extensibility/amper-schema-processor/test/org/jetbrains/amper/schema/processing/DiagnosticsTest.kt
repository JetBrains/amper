/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.schema.processing

import org.junit.jupiter.api.Test

class DiagnosticsTest : SchemaProcessorTestBase() {

    @Test
    fun `schema smoke`() = runTest {
        givenSchemaExtensionClassName("com.example.MySettings")
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

@Schema /*{{*/class/*}} @Schema declaration must be an interface */ NoSchema2 { val foo: Boolean }
@Schema enum /*{{*/class/*}} @Schema declaration must be an interface */ NoSchemaEnum { FOO }

@Schema interface Empty

@Schema interface Lists {                 
  val enabled: Boolean
  val data: List<String>
  val data2: List<Lists>
  val aliasList: ListOfString
  val malformedType1: /*{{*/List/*}} Unexpected schema type `List`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
  val malformedType2: /*{{*/List<Int, String>/*}} Unexpected schema type `List`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
}

@Schema interface Maps {
   val mapProperty1: Map<String, String>
   val mapProperty2: Map</*{{*/Int/*}} Only String is allowed as a Map key type in @Schema interfaces */, String>
   val mapProperty3: Map<String, /*{{*/NoSchema/*}} Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */>
   val mapProperty4: Map<String, Lists>
   val mapProperty5: Map<String, Maps>
   val aliasMap: MapToListOfPath
   
   val malformedType3: /*{{*/Map/*}} Unexpected schema type `Map`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val malformedType4: /*{{*/Map/*}} Unexpected schema type `Map`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
}

@Schema interface Nullable {
  val enum: MyKind?
  val boolean: Boolean?
  val string: String?
  val int: Int?
  val path: Path?
  val booleanAlias: MyBoolean
  val listsOptional: ListsSchema?
}

@Schema interface WithValidDefaults {
  val boolean1: Boolean get() = DEFAULT_TRUE
  val boolean3 get() = true
  val string1 get() = "hello"
  val string2 get() = "hello $CONST_STR"
  val string3: String? get() = "hello" + "World"
  val string4: String? get() = null
  val int: Int get() = 1 + 2
  val int2: Int get() = Int.MAX_VALUE
  val listOfString get() = listOf("a", "b", "c")
  val listOfString2: List<String> get() = emptyList()
  val listOfString1: List<String?> get() = listOf(null, "hello" + "foo", CONST_STR)
  val listOfMaps: List<Map<String, String>> get() = listOf(emptyMap(), emptyMap())
  val mapOfList: Map<String, List<String>> get() = emptyMap()
  val map: Map<String, Int> get() = emptyMap()
  val obj1: Maps? get() = null
  val path: Path? get() = null
  val enum: MyKind? get() = null
 
  companion object {
     const val DEFAULT_TRUE = true
     const val CONST_STR = "World!"
  }
}

@Schema interface WithInvalidDefaults {
  /*{{*/val something get() = null/*}} Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
  /*{{*/val listOfSomething get() = listOf(null)/*}} Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */

// TODO: Investigate, why is this not a constant expression
  val boolean0: Boolean get() = /*{{*/false && true/*}} Invalid primitive default expression. Only simple constant expressions are allowed */

  val boolean1: Boolean get() = /*{{*/System.getProperty("hello") != null/*}} Invalid primitive default expression. Only simple constant expressions are allowed */                  
  // TODO: Support this case in some capacity?
  val boolean2: Boolean get() = /*{{*/boolean1/*}} Invalid primitive default expression. Only simple constant expressions are allowed */
  val int1: Int get() = /*{{*/0 / 0/*}} Invalid primitive default expression. Only simple constant expressions are allowed */
  val int2: Int? get() = /*{{*/System.getProperty("hello")?.length/*}} Invalid primitive default expression. Only simple constant expressions are allowed */
  // TODO: Support this case?
  val path: Path get() = /*{{*/kotlin.io.path.Path("foo/bar")/*}} Explicit defaults for paths are not yet supported */
  val list1: List<String> get() = /*{{*/arrayOf("hello", "foo").toList()/*}} Invalid list default expression. Only `emptyList()` or `listOf(...)` calls are allowed */
  val list2: List<List<String>> get() = listOf(/*{{*/arrayOf("a").toList()/*}} Invalid list default expression. Only `emptyList()` or `listOf(...)` calls are allowed */)
  // TODO: Support this case
  val map: Map<String, Int> get() = /*{{*/mapOf("a" to 1, "b" to 2, "c" to 3)/*}} Invalid map default expression. Only `emptyMap()` or `mapOf(...)` calls are allowed */
  val obj: WithValidDefaults get() = /*{{*/object : WithValidDefaults {}/*}} Explicit defaults for @Schema interfaces are not supported. Every schema interface is instantiated by default using all user-provided and default values */
  val obj1 get() = /*{{*/object : WithValidDefaults {}/*}} Explicit defaults for @Schema interfaces are not supported. Every schema interface is instantiated by default using all user-provided and default values */

  val enum: MyKind get() = MyKind.Kind1

  val withBlockBody: Int 
    /*{{*/get() { return 0 }/*}} Default property getter must have an expression body */
}

@Schema interface Invalid /*{{*/<T>/*}} Generics are not allowed in @Schema interfaces */ : /*{{*/Empty/*}} Superinterfaces for @Schema interfaces are not yet supported */ {
   val foo: Boolean
   
   val /*{{*/<T>/*}} Generics are not allowed in @Schema interfaces */ /*{{*/T/*}} Extension properties are not allowed in @Schema interfaces */.withReceiver: Int
   val /*{{*/Int/*}} Extension properties are not allowed in @Schema interfaces */.withReceiver2: String get() = ""

   /*{{*/context(_: String)/*}} Context parameters are not allowed in @Schema interfaces */
   val withContext: Int
   
   /*{{*/var/*}} Mutable properties are not allowed in @Schema interfaces */ mutable: String
   
   /*{{*/fun forbidden() {
      exitProcess(0)
   }/*}} Functions are not allowed in @Schema interfaces */
   class Unrelated {
       fun allowed() {}
   }
}

// Main schema
@Schema interface MySettings {
   val /*{{*/enabled/*}} `enabled` property name is reserved in the plugins schema extension */: String
   
   val booleanProperty: Boolean
   val stringProperty: String
   val intProperty: Int
   val pathProperty: Path
   val floatProperty: /*{{*/Float/*}} Unexpected schema type `kotlin.Float`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val unresolved: /*{{*/SomeOtherType/*}} Unexpected schema type `SomeOtherType`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val functionType: /*{{*/() -> Unit/*}} Unexpected schema type `() -> kotlin.Unit`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   
   val nonSchema: /*{{*/NoSchema/*}} Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val empty: Empty
   val lists: ListsSchema
   val maps: Maps
}

@Schema interface Config {
  val map: Map<String, List<Path>>
}

object Hello {
    /*{{*/@[JvmStatic TaskAction]
    fun nested()/*}} @TaskAction function must be a top-level function */
}

@TaskAction
/*{{*/context(_: String)/*}} Context parameters are not allowed in @TaskAction function */
/*{{*/suspend/*}} Suspending @TaskAction functions are not yet supported */ /*{{*/inline/*}} @TaskAction function cant be marked as inline */ fun /*{{*/<T>/*}} @TaskAction function cant be generic */ /*{{*/T/*}} @TaskAction function cant be an extension function */.invalidTaskAction(
  int: Int = 0,
  map: Map<String, String> = emptyMap(),
  // FIXME: contains paths, should be marked
  list: List<Path> = emptyList(),
  @Input inputDir: Path? = null,
  @Output outputDir: Path,
  somePath: Path,
  /*{{*/@Input/*}} Both @Input and @Output annotations cant be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */ @Output anotherPath: Path,
  @[/*{{*/Input/*}} Both @Input and @Output annotations cant be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */ Output] crazyPath: Path,
  // FIXME: contains paths, should be marked/forbidden/marked here?
  config: Config,
): /*{{*/String/*}} @TaskAction function must return Unit */ {
  error("woof!")
}
"""
        )

        expectPluginData(
            javaClass.classLoader.getResourceAsStream("schema-smoke.json")!!
                .bufferedReader().useLines { it.joinToString(separator = "\n") }
        )
    }
}
