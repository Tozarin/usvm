package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.findType
import org.usvm.machine.interpreter.transformers.springjpa.generateNewWithInit
import org.usvm.machine.interpreter.transformers.springjpa.query.selectfun.SelectFunCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.table.TableCtx
import org.usvm.util.TableInfo


class QueryCtx(
    val from: FromCtx?,
    val where: WhereCtx?,
    val select: SelectFunCtx?
) {

    fun collectTblAliases(): Map<String, TableCtx> {
        return from?.collectAliases() ?: mapOf()
    }

    fun collectSelAliases(): Map<String, SelectFunCtx.SelectionCtx> {
        return select?.collectAliases() ?: mapOf()
    }

    fun collectPositions(info: CommonInfo): List<TableInfo.ColumnInfo> {
        return from?.collectPositions(info) ?: listOf()
    }

    fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOf(
            from?.getLambdas() ?: listOf(),
            where?.getLambdas(info) ?: listOf(),
            select?.getLambdas(info) ?: listOf()
        )
            .flatten()
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {

        val fromRes = from?.genInst(ctx)!! // TODO: no FROM part
        val whereFun = where?.getLambdaVar(ctx)
        val selFun = select?.getLambdaVar(ctx)!! // TODO: no SELECT part

        val filtred = whereFun?.let {
            ctx.genCtx.generateNewWithInit("resFiltred", ctx.common.filterType, listOf(fromRes, it))
        }
            ?: fromRes

        val retType = ctx.cp.findType(ctx.common.origReturnGeneric)
        val classType = ctx.cp.findType("java.lang.Class")
        val typeVar = ctx.genCtx.nextLocalVar("type", classType)
        val type = JcClassConstant(retType, classType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapped = ctx.genCtx
            .generateNewWithInit("mapped", ctx.common.mapperType, listOf(filtred, selFun, typeVar))
        if (select.isDistinct)
            return ctx.genCtx.generateNewWithInit("dis", ctx.common.distinctType, listOf(mapped))

        return mapped
    }
}
