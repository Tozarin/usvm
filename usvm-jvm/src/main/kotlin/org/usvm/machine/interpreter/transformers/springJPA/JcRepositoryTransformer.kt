package org.usvm.machine.interpreter.transformers.springJPA

import kotlinx.collections.immutable.toPersistentList
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hibernate.grammars.hql.HqlLexer
import org.hibernate.grammars.hql.HqlParser
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springJPA.query.JPAQueryVisitor


object JcRepositoryTransformer : JcClassExtFeature {

    private val visitedCtx: MutableMap<String, SelectCtx> = mutableMapOf()

    private fun visitedName(method: JcMethod): String {
        return "${method.enclosingClass.name}.${method.name}"
    }

    fun addCtx(method: JcMethod, ctx: SelectCtx) {
        visitedCtx[visitedName(method)] = ctx
    }

    fun getCtx(method: JcMethod): SelectCtx? {
        return visitedCtx[visitedName(method)]
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isJpaRepository) return null

        val lambdas = originalMethods.flatMap {
            val query = it.query // TODO: by name
            if (query == null) {
                listOf()
            } else {
                val queryCtx = HqlLexer(CharStreams.fromString(query))
                    .let { CommonTokenStream(it) }
                    .let { HqlParser(it) }
                    .statement()

                val parserRes = JPAQueryVisitor().visit(queryCtx) as SelectCtx
                addCtx(it, parserRes)

                val repo = it.enclosingClass
                parserRes.getLambdas(repo.classpath, repo, it)
            }
        }

        return originalMethods.toPersistentList().addAll(lambdas)
    }
}

object JcRepositoryQueryTransformer : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return !method.repositoryLambda && method.enclosingClass.isJpaRepository
    }

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath

        val parserRes = JcRepositoryTransformer.getCtx(method)!! // TODO: query by name
        val res = parserRes.genInst(cp, repo, method, this)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
