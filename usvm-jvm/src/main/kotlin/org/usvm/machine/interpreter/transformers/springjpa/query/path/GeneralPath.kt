package org.usvm.machine.interpreter.transformers.springjpa.query.path

import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.ExpressionCtx

class GeneralPathCtx(
    val path: SimplePathCtx,
    val index: Index?
) {
    class Index(val ix: ExpressionCtx, val cont: GeneralPathCtx?)

    // TODO: indexing
    fun fullPath(): SimplePathCtx {
        return path
    }
}
