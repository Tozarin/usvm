package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectType
import org.usvm.machine.interpreter.transformers.springjpa.methodRef
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Null
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Primitive
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx
import java.time.LocalDateTime

enum class Datetime {
    Year, Month, Day, Week, Quarter, Hour, Minute, Second, Nanosecond, Epoch
}

class LString(val value: String) : ExpressionCtx() {

    override val type = Primitive.String()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.common.strType)
        val str = JcStringConstant(value, ctx.common.strType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, str) }
        return v
    }
}

class LNull : ExpressionCtx() {

    override val type = Null()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.objectType)
        val n = JcNullConstant(ctx.cp.objectType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, n) }
        return v
    }
}

class LBool(val value: Boolean) : ExpressionCtx() {

    override val type = Primitive.Bool()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.boolean)
        val b = JcBool(value, ctx.cp.boolean)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, b) }
        return v
    }
}

class LInt(val value: Int) : ExpressionCtx() {

    override val type = Primitive.Int()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.int)
        val i = JcInt(value, ctx.cp.int)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, i) }
        return v
    }
}

class LLong(val value: Long) : ExpressionCtx() {

    override val type = Primitive.Long()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.long)
        val l = JcLong(value, ctx.cp.long)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, l) }
        return v
    }
}

class LBigInt(val value: String) : ExpressionCtx() {

    override val type = Primitive.BigInt()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val common = ctx.common
        val v = ctx.newVar(common.bigIntType)
        val init = JcNewExpr(common.bigIntType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

        val bigInit = common.bigIntType.declaredMethods
            .single { it.name == "<init>" && it.parameters.first().type == common.strType }
        val str = JcStringConstant(value, common.strType)
        val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
        ctx.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
        return v
    }
}

class LFloat(val value: Float) : ExpressionCtx() {

    override val type = Primitive.Float()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.float)
        val f = JcFloat(value, ctx.cp.float)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, f) }
        return v
    }
}

class LDouble(val value: Double) : ExpressionCtx() {

    override val type = Primitive.Double()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val v = ctx.newVar(ctx.cp.double)
        val d = JcDouble(value, ctx.cp.double)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
        return v
    }
}

class LBigDecimal(val value: String) : ExpressionCtx() {

    override val type = Primitive.BigDecimal()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val common = ctx.common
        val v = ctx.newVar(common.bigDecimalType)
        val init = JcNewExpr(common.bigDecimalType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

        val bigInit = common.bigDecimalType.declaredMethods
            .single { it.name == "<init>" && it.parameters.first().type == common.strType }
        val str = JcStringConstant(value, common.strType)
        val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
        ctx.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
        return v
    }
}

class LBinary(val bins: ByteArray) : ExpressionCtx() {

    override val type = Primitive.Binary()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val size = bins.size
        val v = ctx.newVar(ctx.common.byteArrType)
        val arr = JcNewArrayExpr(ctx.common.byteArrType, listOf(JcInt(size, ctx.cp.int)))
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, arr) }

        bins.forEachIndexed { ix, b ->
            val elem = JcArrayAccess(v, JcInt(ix, ctx.cp.int), ctx.cp.byte)
            val value = JcByte(b, ctx.cp.byte)
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
        }

        return v
    }
}

class LTime(val time: LocalDateTime) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
