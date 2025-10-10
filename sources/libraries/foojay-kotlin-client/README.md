# Kotlin Client for the Foojay Discovery API

This library provides a Kotlin client for interacting with the Foojay Discovery API.

We do not reuse the [Foojay Discoclient](https://github.com/foojayio/discoclient) library for the following reasons:

* it transitively brings Gson, and that's obsolete and redundant in the Kotlin world (we try to reduce our deps)
* the API doesn't make it clear how to handle the latest/version/jdk_version parameters)
* (minor) the API isn't Kotlin-esque: `CompletableFuture` return types instead of suspend functions
