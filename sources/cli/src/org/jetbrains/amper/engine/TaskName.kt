package org.jetbrains.amper.engine

data class TaskName(val name: String) {
    init {
        require(name.isNotBlank())
    }

    companion object {
        fun fromHierarchy(path: List<String>) = TaskName(path.joinToString(":", prefix = ":"))
    }
}
