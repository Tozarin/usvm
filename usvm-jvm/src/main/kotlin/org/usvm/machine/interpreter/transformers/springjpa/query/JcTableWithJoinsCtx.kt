package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.join.JoinCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.table.TableCtx
import org.usvm.util.TableInfo

class TableWithJoinsCtx(
    val root: TableCtx,
    val joins: List<JoinCtx>
) {

    fun getLambdas(): List<JcMethod> {
        // TODO: joins
        return root.genLambas()
    }

    fun collectPositions(info: CommonInfo): List<TableInfo.ColumnInfo> {
        val rootPos = root.positions(info)
        // TODO: joinPos
        return rootPos
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {
        // TODO: joins
        return root.genInst(ctx)
    }
}
