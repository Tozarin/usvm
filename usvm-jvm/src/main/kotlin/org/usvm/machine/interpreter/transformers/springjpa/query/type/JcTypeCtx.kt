package org.usvm.machine.interpreter.transformers.springjpa.query.type

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.Parameter
import org.usvm.machine.interpreter.transformers.springjpa.query.path.SimplePathCtx

abstract class TypeCtx {
    abstract fun getType(info: CommonInfo): JcType
}

class Null : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}

class Param(val param: Parameter) : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}

class Path(val name: SimplePathCtx) : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        val tbl = info.tblAliases[name.root]
        return if (tbl != null && name.cont.isEmpty()) {
            val tblInfo = tbl.getTbl(info)
            val origClass = tblInfo.origClass
            origClass.toType()
        } else {
            val fieldName = name.cont.single() // TODO:
            val pos = info.positions.single { it.origField.name == fieldName }
            pos.origField.type.toJcType(info.cp)!!
        }
    }
}

class Tuple(val types: List<TypeCtx>) : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}
