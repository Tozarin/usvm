package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx

abstract class ExprOrPredCtx {
    abstract val type: TypeCtx
    abstract fun genInst(ctx: MethodCtx): JcLocalVar
}
