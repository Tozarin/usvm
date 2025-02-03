package org.usvm.machine.interpreter.transformers.springjpa.query.path

class SimplePathCtx(
    val root: String, // not case-sensitive
    val cont: List<String> // case-sensitive
) {

    fun flat(): String {
        return root + cont.joinToString(prefix = ".")
    }
}
