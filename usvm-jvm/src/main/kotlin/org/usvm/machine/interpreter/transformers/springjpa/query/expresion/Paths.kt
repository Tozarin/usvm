package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.springjpa.generatedInit
import org.usvm.machine.interpreter.transformers.springjpa.methodRef
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.GeneralPathCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.table.TableCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Path
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx
import org.usvm.machine.interpreter.transformers.springjpa.toArgument
import org.usvm.machine.state.concreteMemory.toTypedMethod
import org.usvm.util.TableInfo

class SyntacticPath() : ExpressionCtx() { // TODO:

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class GeneralPath(val path: GeneralPathCtx) : ExpressionCtx() {

    val simplePath = path.fullPath() // TODO: indexing

    override val type = Path(path.fullPath())

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val tbl = ctx.common.tblAliases[simplePath.root]
        return if (simplePath.cont.isEmpty() && tbl != null) genObj(ctx, tbl)
        else genField(ctx)
    }

    // SELECT ptype FROM PetType ptype
    private fun genObj(ctx: MethodCtx, tbl: TableCtx): JcLocalVar {
        // tbl != null invariant

        val common = ctx.common
        val cp = ctx.cp
        val pos = tbl.positions(common)
        val indexes = pos.map { common.positions.indexOf(it) }
        val size = indexes.size
        val newRow = ctx.newVar(common.objectArrType)
        val arr = JcNewArrayExpr(common.objectArrType, listOf(JcInt(size, cp.int)))
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, newRow, arr) }

        val row = common.method.parameters.first().toArgument
        indexes.forEachIndexed { ix, oldIx ->
            val elem = JcArrayAccess(newRow, JcInt(ix, cp.int), cp.objectType)
            val value = JcArrayAccess(row, JcInt(oldIx, cp.int), cp.objectType)
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
        }

        val tblInfo = tbl.getTbl(common)
        val origClass = tblInfo.origClass
        val res = ctx.newVar(origClass.toType())
        val obj = JcNewExpr(origClass.toType())
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, res, obj) }

        val init = origClass.declaredMethods.single { it.generatedInit }
        if (common.fetched.isEmpty()) {
            ctx.addFetched(genFetch(ctx, tblInfo))
        }
        val arg = common.fetched.toPersistentList().add(0, newRow)
        val initCall = JcSpecialCallExpr(init.toTypedMethod.methodRef, res, arg)
        ctx.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }

        return res
    }

    private fun genFetch(ctx: MethodCtx, tblInfo: TableInfo.TableWithIdInfo): List<JcLocalVar> {
        val common = ctx.common
        return tblInfo.orderedRelations().map { rel ->
            val relTblName = rel.toTableName(ctx.cp)
            val tblField = common.databases.fields.single { it.name == relTblName }
            val v = ctx.newVar(tblField.type)
            val field = JcFieldRef(null, tblField)
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, field) }
            v
        }
    }

    private fun genField(ctx: MethodCtx): JcLocalVar {

        val common = ctx.common
        val fieldName = simplePath.cont.single() // TODO:
        val pos = common.positions.single { it.origField.name == fieldName }
        val ix = common.positions.indexOf(pos)
        val row = common.method.parameters.first().toArgument

        val f = ctx.newVar(ctx.cp.objectType)
        val elem = JcArrayAccess(row, JcInt(ix, ctx.cp.int), ctx.cp.objectType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, f, elem) }

        val fType = pos.type.toJcType(ctx.cp)!!
        val casted = ctx.newVar(fType)
        val cast = JcCastExpr(fType, f)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, casted, cast) }

        return casted
    }
}
