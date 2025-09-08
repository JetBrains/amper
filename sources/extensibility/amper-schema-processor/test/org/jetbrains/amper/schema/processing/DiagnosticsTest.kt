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

@Schema /*{{*/class/*}} [Amper Plugin Schema] @Schema declaration must be an interface */ NoSchema2 { val foo: Boolean }
@Schema enum /*{{*/class/*}} [Amper Plugin Schema] @Schema declaration must be an interface */ NoSchemaEnum { FOO }

@Schema interface Empty

@Schema interface Lists {                 
  val enabled: Boolean
  val data: List<String>
  val data2: List<Lists>
  val aliasList: ListOfString
  val malformedType1: /*{{*/List/*}} [Amper Plugin Schema] Unexpected schema type `List`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
  val malformedType2: /*{{*/List<Int, String>/*}} [Amper Plugin Schema] Unexpected schema type `List`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
}

@Schema interface Maps {
   val mapProperty1: Map<String, String>
   val mapProperty2: Map</*{{*/Int/*}} [Amper Plugin Schema] Only String is allowed as a Map key type in @Schema interfaces */, String>
   val mapProperty3: Map<String, /*{{*/NoSchema/*}} [Amper Plugin Schema] Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */>
   val mapProperty4: Map<String, Lists>
   val mapProperty5: Map<String, Maps>
   val aliasMap: MapToListOfPath
   
   val malformedType3: /*{{*/Map/*}} [Amper Plugin Schema] Unexpected schema type `Map`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val malformedType4: /*{{*/Map/*}} [Amper Plugin Schema] Unexpected schema type `Map`.
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
  val string4: String? get() = /*{{*/null/*}} [Amper Plugin Schema] Nullable properties are already null by default, no need to specify this explicitly */
  val int: Int get() = 1 + 2
  val int2: Int get() = Int.MAX_VALUE
  val listOfString get() = listOf("a", "b", "c")
  val listOfString2: List<String> get() = emptyList()
  val listOfString1: List<String?> get() = listOf(null, "hello" + "foo", CONST_STR)
  val listOfMaps: List<Map<String, String>> get() = listOf(emptyMap(), emptyMap())
  val mapOfList: Map<String, List<String>> get() = emptyMap()
  val map: Map<String, Int> get() = emptyMap()
  val obj1: Maps? get() = /*{{*/null/*}} [Amper Plugin Schema] Nullable properties are already null by default, no need to specify this explicitly */
  val path: Path? get() = /*{{*/null/*}} [Amper Plugin Schema] Nullable properties are already null by default, no need to specify this explicitly */
  val enum: MyKind? get() = /*{{*/null/*}} [Amper Plugin Schema] Nullable properties are already null by default, no need to specify this explicitly */
 
  companion object {
     const val DEFAULT_TRUE = true
     const val CONST_STR = "World!"
  }
}

@Schema interface WithInvalidDefaults {
  /*{{*/val something get() = null/*}} [Amper Plugin Schema] Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
  /*{{*/val listOfSomething get() = listOf(null)/*}} [Amper Plugin Schema] Unexpected schema type `kotlin.Nothing?`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */

// TODO: Investigate, why is this not a constant expression
  val boolean0: Boolean get() = /*{{*/false && true/*}} [Amper Plugin Schema] Invalid primitive default expression. Only simple constant expressions are allowed */

  val boolean1: Boolean get() = /*{{*/System.getProperty("hello") != null/*}} [Amper Plugin Schema] Invalid primitive default expression. Only simple constant expressions are allowed */                  
  // TODO: Support this case in some capacity?
  val boolean2: Boolean get() = /*{{*/boolean1/*}} [Amper Plugin Schema] Invalid primitive default expression. Only simple constant expressions are allowed */
  val int1: Int get() = /*{{*/0 / 0/*}} [Amper Plugin Schema] Invalid primitive default expression. Only simple constant expressions are allowed */
  val int2: Int? get() = /*{{*/System.getProperty("hello")?.length/*}} [Amper Plugin Schema] Invalid primitive default expression. Only simple constant expressions are allowed */
  // TODO: Support this case?
  val path: Path get() = /*{{*/kotlin.io.path.Path("foo/bar")/*}} [Amper Plugin Schema] Explicit defaults for paths are not yet supported */
  val list1: List<String> get() = /*{{*/arrayOf("hello", "foo").toList()/*}} [Amper Plugin Schema] Invalid list default expression. Only `emptyList()` or `listOf(...)` calls are allowed */
  val list2: List<List<String>> get() = listOf(/*{{*/arrayOf("a").toList()/*}} [Amper Plugin Schema] Invalid list default expression. Only `emptyList()` or `listOf(...)` calls are allowed */)
  // TODO: Support this case
  val map: Map<String, Int> get() = /*{{*/mapOf("a" to 1, "b" to 2, "c" to 3)/*}} [Amper Plugin Schema] Invalid map default expression. Only `emptyMap()` or `mapOf(...)` calls are allowed */
  val obj: WithValidDefaults get() = /*{{*/object : WithValidDefaults {}/*}} [Amper Plugin Schema] Explicit defaults for @Schema interfaces are not supported. Every schema interface is instantiated by default using all user-provided and default values */
  val obj1 get() = /*{{*/object : WithValidDefaults {}/*}} [Amper Plugin Schema] Explicit defaults for @Schema interfaces are not supported. Every schema interface is instantiated by default using all user-provided and default values */

  val enum: MyKind get() = MyKind.Kind1

  val withBlockBody: Int 
    /*{{*/get() { return 0 }/*}} [Amper Plugin Schema] Default property getter must have an expression body */
}

@Schema interface Invalid /*{{*/<T>/*}} [Amper Plugin Schema] Generics are not allowed in @Schema interfaces */ : /*{{*/Empty/*}} [Amper Plugin Schema] Superinterfaces for @Schema interfaces are not yet supported */ {
   val foo: Boolean
   
   val /*{{*/<T>/*}} [Amper Plugin Schema] Generics are not allowed in @Schema interfaces */ /*{{*/T/*}} [Amper Plugin Schema] Extension properties are not allowed in @Schema interfaces */.withReceiver: Int
   val /*{{*/Int/*}} [Amper Plugin Schema] Extension properties are not allowed in @Schema interfaces */.withReceiver2: String get() = ""

   /*{{*/context(_: String)/*}} [Amper Plugin Schema] Context parameters are not allowed in @Schema interfaces */
   val withContext: Int
   
   /*{{*/var/*}} [Amper Plugin Schema] Mutable properties are not allowed in @Schema interfaces */ mutable: String
   
   /*{{*/fun forbidden() {
      exitProcess(0)
   }/*}} [Amper Plugin Schema] Functions are not allowed in @Schema interfaces */
   class Unrelated {
       fun allowed() {}
   }
}

// Main schema
@Schema interface MySettings {
   val /*{{*/enabled/*}} [Amper Plugin Schema] `enabled` property name is reserved in the plugins schema extension */: String
   
   val booleanProperty: Boolean
   val stringProperty: String
   val intProperty: Int
   val pathProperty: Path
   val floatProperty: /*{{*/Float/*}} [Amper Plugin Schema] Unexpected schema type `kotlin.Float`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val unresolved: /*{{*/SomeOtherType/*}} [Amper Plugin Schema] Unexpected schema type `SomeOtherType`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val functionType: /*{{*/() -> Unit/*}} [Amper Plugin Schema] Unexpected schema type `() -> kotlin.Unit`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   
   val nonSchema: /*{{*/NoSchema/*}} [Amper Plugin Schema] Unexpected schema type `com.example.NoSchema`.
Supported types are:
 - Boolean, String, Int, Path, enums
 - @Schema interface (must be declared in the same source directory)
 - List<T>, Map<String, T>, where `T` is a supported type. */
   val empty: Empty
   val lists: ListsSchema
   val maps: Maps
}

@Schema
/*{{*/internal/*}} [Amper Plugin Schema] @Schema interface must be public */
interface Config {
  val map: Map<String, List<Path>>
}

object Hello {
    /*{{*/@[JvmStatic TaskAction]
    fun nested()/*}} [Amper Plugin Schema] @TaskAction function must be a top-level function */
}

@TaskAction fun /*{{*/overloaded/*}} [Amper Plugin Schema] Illegal overload for `com.example.overloaded`: @TaskAction functions cant be overloaded */() {}
@TaskAction
/*{{*/private/*}} [Amper Plugin Schema] @TaskAction function must be public */
fun /*{{*/overloaded/*}} [Amper Plugin Schema] Illegal overload for `com.example.overloaded`: @TaskAction functions cant be overloaded */(int: Int) {}

@TaskAction
/*{{*/context(_: String)/*}} [Amper Plugin Schema] Context parameters are not allowed in @TaskAction function */
/*{{*/suspend/*}} [Amper Plugin Schema] Suspending @TaskAction functions are not yet supported */ /*{{*/inline/*}} [Amper Plugin Schema] @TaskAction function cant be marked as inline */ fun /*{{*/<T>/*}} [Amper Plugin Schema] @TaskAction function cant be generic */ /*{{*/T/*}} [Amper Plugin Schema] @TaskAction function cant be an extension function */.invalidTaskAction(
  int: Int = 0,
  map: Map<String, String> = emptyMap(),
  /*{{*/list: List<Path> = emptyList()/*}} [Amper Plugin Schema] Parameter of a Path-referencing type must be annotated with either @Input or @Output */,
  @Input inputDir: Path? = /*{{*/null/*}} [Amper Plugin Schema] Nullable properties are already null by default, no need to specify this explicitly */,
  @Output outputDir: Path,
  /*{{*/somePath: Path/*}} [Amper Plugin Schema] Parameter of a Path-referencing type must be annotated with either @Input or @Output */,
  /*{{*/@Input @Output anotherPath: Path/*}} [Amper Plugin Schema] Both @Input and @Output annotations cant be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */,
  /*{{*/@[Input Output] crazyPath: Path/*}} [Amper Plugin Schema] Both @Input and @Output annotations cant be specified for a single parameter. File updates in-place are not supported. Use separate input/output instead. */,
  /*{{*/config: Config/*}} [Amper Plugin Schema] Parameter of a Path-referencing type must be annotated with either @Input or @Output */,
  @Input inputConfig: Config,
  @Output outputList: Map<String, Path>,
): /*{{*/String/*}} [Amper Plugin Schema] @TaskAction function must return Unit */ {
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
