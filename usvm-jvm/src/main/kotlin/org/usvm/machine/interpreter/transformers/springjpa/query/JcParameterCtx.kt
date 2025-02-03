package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcLocalVar


// Argument from original function
abstract class Parameter {
    abstract fun genInst(ctx: MethodCtx): JcLocalVar
}

// Simple name
class Colon(val name: String) : Parameter() {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// ?3
class Positional(val pos: Int) : Parameter() {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
