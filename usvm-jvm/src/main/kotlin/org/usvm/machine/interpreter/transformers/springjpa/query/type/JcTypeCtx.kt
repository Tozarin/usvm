package org.usvm.machine.interpreter.transformers.springjpa.query.type

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassType
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
        val pos = param.position(info)
        return info.origMethod.parameters.get(pos).type.toJcType(info.cp)!!
    }
}

class Path(val name: SimplePathCtx) : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        val aliased = info.aliases[name.root]
        val positions = info.positions[aliased]!!
        return if (name.isSimple()) {
            positions.values.first().first.enclosingClass.toType()
        } else {
            val fst = positions[name.cont.first()]!!.first.type.toJcType(info.cp)!!
            name.cont.toPersistentList().removeAt(0).fold(fst) { acc, nameField ->
                (acc as JcClassType).declaredFields.single { it.name == nameField }.type
            }
        }
    }
}

class Tuple(val types: List<TypeCtx>) : TypeCtx() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}
