package org.usvm.machine.interpreter.transformers.springjpa.query.predicate

import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.boolean
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.ExpressionCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.LNull
import org.usvm.machine.interpreter.transformers.springjpa.query.path.PathCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.SimplePathCtx

abstract class Function : PredicateCtx() {

    class IsNull(val expression: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsEmpty(val expression: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsTrue(val expression: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            return expression.genInst(ctx)
        }
    }

    class IsDistinct(val expression: ExpressionCtx, val from: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Member(val expression: ExpressionCtx, val of: PathCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class In(val expression: ExpressionCtx, val list: ListCtx) : Function() {
        class ListCtx()

        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Between(val expression: ExpressionCtx, val left: ExpressionCtx, val right: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Like(
        val expression: ExpressionCtx,
        val pattern: ExpressionCtx,
        val escape: ExpressionCtx?,
        val caseSenc: Boolean
    ) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            val expr = expression.genInst(ctx)
            val pattern = pattern.genInst(ctx)
            val esc = escape?.genInst(ctx) ?: LNull().genInst(ctx)
            val senc = JcBool(caseSenc, ctx.cp.boolean)
            val name = "${ctx.getVarName()}#like"
            return ctx.genStaticCall(name, "like", listOf(expr, pattern, esc, senc))
        }
    }

    class Compare(val left: ExpressionCtx, val right: ExpressionCtx, val operator: Operator) : Function() {
        enum class Operator {
            Equal, NotEqual, Greater, GreaterEqual, Less, LessEqual
        }

        private fun getMethodName(): String {
            return when (operator) {
                Operator.Equal -> "equals"
                Operator.NotEqual -> "notEquals"
                Operator.Greater -> "greater"
                Operator.GreaterEqual -> "greaterEq"
                Operator.Less -> "less"
                Operator.LessEqual -> "lessEq"
            }
        }

        override fun genInst(ctx: MethodCtx): JcLocalVar {
            val l = left.genInst(ctx)
            val r = right.genInst(ctx)

            return ctx.genStaticCall(ctx.getVarName(), getMethodName(), listOf(l, r))
        }
    }

    class ExistCollection(val quantifier: ColQuantifierCtx, val path: SimplePathCtx) : Function() {
        class ColQuantifierCtx()

        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Exist(val expression: ExpressionCtx) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}
