package org.jetbrains.deft.proto.frontend.fragments.yaml

import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.ModelInit
import org.jetbrains.deft.proto.frontend.PotatoModule
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.name

class YamlFragmentsModelInit : ModelInit {
    override fun getModel(root: Path): Model {
        if (!root.exists()) {
            throw RuntimeException("Can't find ${root.absolutePathString()}")
        }
        val modules = Files.walk(root)
            .filter { it.name == "Pot.yaml" }
            .map(::parsePotato)
            .collect(Collectors.toList())

        return object : Model {
            override val modules: List<PotatoModule> = modules
        }
    }
}
