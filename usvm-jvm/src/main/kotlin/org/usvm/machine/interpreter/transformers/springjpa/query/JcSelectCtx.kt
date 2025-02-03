package org.usvm.machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.OrderCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.QueryCtx

class SelectCtx(
    val orders: List<OrderCtx>,
    val query: QueryCtx
) {
    fun addOrder(order: List<OrderCtx>) {
        orders.toMutableList().addAll(order)
    }

    fun genInst(
        cp: JcClasspath,
        repo: JcClassOrInterface,
        method: JcMethod,
        genCtx: JcSingleInstructionTransformer.BlockGenerationContext
    ): JcLocalVar {
        val ctx = MethodCtx(cp, query, repo, method, genCtx)

        val queryVar = query.genInst(ctx)
        val ordered = orders.fold(queryVar) { p, order -> order.applyOrder(p, ctx) }

        val wrapperType = getWrapperType(ctx.common, method.returnType)!!
        val wrapper = ctx.genCtx.generateNewWithInit("wrappedRes", wrapperType, listOf(ordered))

        return wrapper
    }

    private fun getWrapperType(info: CommonInfo, type: TypeName): JcClassType? {
        return when (type.typeName) {
            "java.util.Set" -> info.setType
            "java.util.List" -> info.listType
            // TODO: more collections
            else -> null
        }
    }

    fun getLambdas(
        cp: JcClasspath,
        repo: JcClassOrInterface,
        method: JcMethod,
    ): List<JcMethod> {

        val info = CommonInfo(cp, query, repo, method)
        val queryLambdas = query.getLambdas(info)
        val orderLambas = orders.flatMap { it.getLambdas(info) }

        return queryLambdas.toPersistentList().addAll(orderLambas)
    }
}
