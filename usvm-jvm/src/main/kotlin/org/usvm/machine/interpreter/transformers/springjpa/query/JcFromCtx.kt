package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.table.TableCtx
import org.usvm.util.TableInfo


class FromCtx(
    val tables: List<TableWithJoinsCtx>
) {

    fun getLambdas(): List<JcMethod> {
        return tables.flatMap { it.getLambdas() }
    }

    fun collectAliases(): Map<String, TableCtx> {
        return tables.mapNotNull { tbl -> tbl.root.alias?.let { it to tbl.root } }
            .associate { p -> p }
    }

    fun collectPositions(info: CommonInfo): List<TableInfo.ColumnInfo> {
        return tables.map { it.collectPositions(info) }.flatten()
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {
        val tbl = tables.single() // TODO: make "FROM Foo, Bar, Baz" as join
        return tbl.genInst(ctx)
    }
}
