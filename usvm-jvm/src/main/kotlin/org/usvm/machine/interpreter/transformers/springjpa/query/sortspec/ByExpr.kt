package org.usvm.machine.interpreter.transformers.springjpa.query.sortspec

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.generateLambda
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.ExpressionCtx

class ByExpr(val expr: ExpressionCtx) : SortSpec() {

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOf(getTranslateMethod(info))
    }

    private var translateMethod: JcMethod? = null
    fun getTranslateMethod(info: CommonInfo): JcMethod {
        translateMethod?.also { return it }
        translateMethod = expr.toLambda(info)
        return translateMethod!!
    }

    override fun getTranslate(ctx: MethodCtx): JcLocalVar {
        val method = getTranslateMethod(ctx.common)
        val lambda = ctx.genCtx.generateLambda("${ctx.getLambdaName()}Var", method)
        return lambda
    }

    override fun getComparer(ctx: MethodCtx): JcLocalVar {

        val exprType = expr.type.getType(ctx.common) as JcClassType
        val method = ctx.common.utilsType.declaredMethods.single {
            it.isStatic && it.parameters.size == 2 && it.parameters.all { p -> p.type == exprType }
        }
            .method

        val lambda = ctx.genCtx.generateLambda("${ctx.getLambdaName()}Var", method)
        return lambda
    }
}
