/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.amperFailure
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.nodes.*
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.reader

context(BuildFileAware, ProblemReporterContext)
fun parseModuleParts(
    config: YamlNode.Mapping,
): Result<ClassBasedSet<ModulePart<*>>> {
    // Collect parts.
    val parseRepositoryResult = parseRepositories(config)
    val parseMetaSettingsResult = parseMetaSettings(config)
    if (parseRepositoryResult !is Result.Success || parseMetaSettingsResult !is Result.Success) {
        return amperFailure()
    }

    return Result.success(buildClassBasedSet {
        add(parseRepositoryResult.value)
        add(parseMetaSettingsResult.value)
    })
}

context(BuildFileAware, ProblemReporterContext)
private fun parseMetaSettings(config: YamlNode.Mapping): Result<MetaModulePart> {
    val meta = config.getMappingValue("module")
        ?: return Result.success(MetaModulePart()) // TODO Check for type.
    val metaNode = meta["layout"]
    if (!metaNode.castOrReport<YamlNode.Scalar?> { FrontendYamlBundle.message("element.name.module.layout") }) {
        return amperFailure()
    }
    val layout = when (metaNode?.value) {
        null -> Layout.AMPER
        "default" -> Layout.AMPER
        "gradle-kmp" -> Layout.GRADLE
        "gradle-jvm" -> Layout.GRADLE_JVM
        else -> return amperFailure()
    }

    return Result.Success(MetaModulePart(layout))
}


context(BuildFileAware, ProblemReporterContext)
private fun reportMissingField(fieldName: String, presentationName: String, node: YamlNode) {
    problemReporter.reportNodeError(
        FrontendYamlBundle.message("field.must.be.present", presentationName, fieldName),
        node = node,
        file = buildFile,
    )
}

context(BuildFileAware, ProblemReporterContext)
private fun parseRepositories(config: YamlNode.Mapping): Result<RepositoriesModulePart> {
    val repos = config.getSequenceValue("repositories") ?: emptyList()

    // Parse repositories.
    val parsedRepos = repos.map {
        when (it) {
            is YamlNode.Scalar -> {
                RepositoriesModulePart.Repository(it.value, it.value)
            }

            is YamlNode.Mapping -> {
                val url = it.getStringValue("url")
                if (url == null) {
                    reportMissingField("url", FrontendYamlBundle.message("element.name.repository.url"), it)
                    return amperFailure()
                }
                val id = it.getStringValue("id") ?: url
                val shouldPublish = it.getBooleanValue("publish") ?: false

                val credentials = it.getMappingValue("credentials")
                if (credentials == null) {
                    RepositoriesModulePart.Repository(id, url, shouldPublish)
                } else {
                    val credentialsPath = credentials.getStringValue("file")
                    if (credentialsPath == null) {
                        reportMissingField(
                            "file",
                            FrontendYamlBundle.message("element.name.credentials.file"),
                            credentials
                        )
                        return amperFailure()
                    }
                    val userNameKey = credentials.getStringValue("usernameKey")
                    if (userNameKey == null) {
                        reportMissingField(
                            "usernameKey",
                            FrontendYamlBundle.message("element.name.credentials.usernameKey"),
                            credentials
                        )
                        return amperFailure()
                    }
                    val passwordKey = credentials.getStringValue("passwordKey")
                    if (passwordKey == null) {
                        reportMissingField(
                            "passwordKey",
                            FrontendYamlBundle.message("element.name.credentials.passwordKey"),
                            credentials
                        )
                        return amperFailure()
                    }

                    val credentialsRelative = buildFile.parent.resolve(credentialsPath).normalize()
                    val credentialsFile = credentialsRelative.takeIf { file -> file.exists() }
                    if (credentialsFile == null) {
                        problemReporter.reportNodeError(
                            FrontendYamlBundle.message("credentials.file.does.not.exist", credentialsRelative),
                            node = credentials,
                            file = buildFile,
                        )
                        return amperFailure()
                    }
                    val credentialProperties = Properties().apply { load(credentialsFile.reader()) }

                    val userName = credentialProperties.getProperty(userNameKey)
                    if (userName == null) {
                        problemReporter.reportNodeError(
                            FrontendYamlBundle.message(
                                "credentials.file.does.not.have.user.key",
                                credentialsRelative,
                                userNameKey
                            ),
                            node = credentials,
                            file = buildFile,
                        )
                        return amperFailure()
                    }
                    val password = credentialProperties.getProperty(passwordKey)
                    if (password == null) {
                        problemReporter.reportNodeError(
                            FrontendYamlBundle.message(
                                "credentials.file.does.not.have.password.key",
                                credentialsRelative,
                                passwordKey,
                            ),
                            node = credentials,
                            file = buildFile,
                        )
                        return amperFailure()
                    }

                    RepositoriesModulePart.Repository(
                        id = id,
                        url = url,
                        publish = shouldPublish,
                        userName = userName,
                        password = password,
                    )
                }
            }

            else -> {
                problemReporter.reportNodeError(
                    FrontendYamlBundle.message("wrong.repository.format"),
                    node = it,
                    file = buildFile,
                )
                return amperFailure()
            }
        }
    }

    return Result.success(RepositoriesModulePart(parsedRepos))
}

