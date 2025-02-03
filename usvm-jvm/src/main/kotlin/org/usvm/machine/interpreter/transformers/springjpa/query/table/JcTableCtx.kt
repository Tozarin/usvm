package org.usvm.machine.interpreter.transformers.springjpa.query.table

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.util.TableInfo

abstract class TableCtx(
    val alias: String?
) {
    abstract fun genLambas(): List<JcMethod>
    abstract fun positions(info: CommonInfo): List<TableInfo.ColumnInfo>
    abstract fun genInst(ctx: MethodCtx): JcLocalVar
    abstract fun getTbl(info: CommonInfo): TableInfo.TableWithIdInfo
}
