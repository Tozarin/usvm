package org.usvm.machine.interpreter.transformers.springjpa.query.sortspec

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.jvmName
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import org.usvm.machine.interpreter.transformers.springjpa.JcMethodBuilder
import org.usvm.machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import org.usvm.machine.interpreter.transformers.springjpa.generateLambda
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.ExpressionCtx
import org.usvm.machine.interpreter.transformers.springjpa.repositoryLambda

class ByExpr(val expr: ExpressionCtx) : SortSpec() {

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOf(getTranslateMethod(info))
    }

    fun getTranslateMethod(info: CommonInfo): JcMethod {
        val methodName = info.names.getMethodName()
        val retType = expr.type.getType(info).typeName.jvmName()
        return JcMethodBuilder(info.repo)
            .setName(methodName)
            .setDesc("([Ljava/lang/Object;)$retType")
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam("java.lang.Object[]")
            .addFillerFuture(TranslateFeature(info, expr, methodName))
            .buildMethod()
    }

    override fun getTranslate(ctx: MethodCtx): JcLocalVar {
        val method = getTranslateMethod(ctx.common)
        val lambda = ctx.genCtx.generateLambda("${ctx.getLambdaName()}Var", method)
        return lambda
    }

    override fun getComparer(ctx: MethodCtx): JcLocalVar {

        val exprType = expr.type.getType(ctx.common) as JcClassType
        val method = ctx.common.orderType.declaredMethods.single {
            it.isStatic && it.parameters.first().type == exprType
        }
            .method

        val lambda = ctx.genCtx.generateLambda("${ctx.getLambdaName()}Var", method)
        return lambda
    }
}

class TranslateFeature(val info: CommonInfo, val expr: ExpressionCtx, val methodName: String) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return method.name == methodName && method.repositoryLambda
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, this)
        val expr = expr.genInst(ctx)
        addInstruction { loc -> JcReturnInst(loc, expr) }
    }
}
