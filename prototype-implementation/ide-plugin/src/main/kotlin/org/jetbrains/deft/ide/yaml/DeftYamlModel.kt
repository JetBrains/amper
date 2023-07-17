@file:Suppress("UnstableApiUsage")

package org.jetbrains.deft.ide.yaml

import com.intellij.psi.PsiReference
import org.jetbrains.deft.ide.DeftPotReference
import org.jetbrains.yaml.meta.model.Field
import org.jetbrains.yaml.meta.model.YamlArrayType
import org.jetbrains.yaml.meta.model.YamlMetaClass
import org.jetbrains.yaml.meta.model.YamlReferenceType
import org.jetbrains.yaml.psi.YAMLScalar
import java.util.regex.Pattern

internal class DeftYamlModel : YamlMetaClass("deft-root") {
    init {
        addFeature(Field("dependencies", DeftDependenciesListMetaType())
            .withNamePattern(Pattern.compile("^(test-)?dependencies(@.+)?\$")))
        addFeature(Field("apply", DeftTemplatesListMetaType()))
    }

    private class DeftDependencyMetaType : YamlReferenceType("deft-dependency") {
        override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
            return arrayOf(DeftPotReference(valueScalar, false))
        }
    }

    private class DeftTemplateMetaType : YamlReferenceType("deft-template") {
        override fun getReferencesFromValue(valueScalar: YAMLScalar): Array<PsiReference> {
            return arrayOf(DeftPotReference(valueScalar, true))
        }
    }

    private class DeftDependenciesListMetaType : YamlArrayType(DeftDependencyMetaType())

    private class DeftTemplatesListMetaType : YamlArrayType(DeftTemplateMetaType())
}
