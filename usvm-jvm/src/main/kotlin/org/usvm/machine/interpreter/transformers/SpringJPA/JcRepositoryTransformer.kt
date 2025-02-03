package org.usvm.machine.interpreter.transformers.SpringJPA

import kotlinx.collections.immutable.toPersistentList
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hibernate.grammars.hql.HqlLexer
import org.hibernate.grammars.hql.HqlParser
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.util.contains
import org.usvm.util.nameEquals

const val REPOSITORY_LAMBDA = "\$queryLambda"

private val JcClassOrInterface.isJpaRepository : Boolean get() =
    interfaces.any { it.name ==  "org.springframework.data.repository.Repository"}

private val JcMethod.repositoryLambda : Boolean get() = contains(annotations, REPOSITORY_LAMBDA)

private val JcMethod.query : String? get() =
    annotations.find { nameEquals(it, "Query") }?.values?.get("value") as String?

object JcRepositoryTransformer : JcClassExtFeature {

    private val visitedCtx : MutableMap<String, SelectCtx> = mutableMapOf()

    private fun visitedName(method : JcMethod) : String {
        return "${method.enclosingClass.name}.${method.name}"
    }

    fun addCtx(method : JcMethod, ctx : SelectCtx) {
        visitedCtx[visitedName(method)] = ctx
    }

    fun getCtx(method : JcMethod) : SelectCtx? {
        return visitedCtx[visitedName(method)]
    }

    override fun methodsOf(clazz : JcClassOrInterface, originalMethods : List<JcMethod>) : List<JcMethod>? {

        if (!clazz.isJpaRepository) return null

        val lambdas = originalMethods.flatMap {
            val query = it.query // TODO: by name
            if (query == null) {
                listOf()
            }
            else if (query != "SELECT ptype FROM PetType ptype ORDER BY ptype.name") {
                listOf()
            }
            else {
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

object JcRepositoryQueryTransformer : JcMethodExtFeature {

    override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {

        val repo = method.enclosingClass

        if (!repo.isJpaRepository || method.repositoryLambda) return null

        val cp = repo.classpath
        val query = method.query

        val filler = JcMethodBodyFiller(method)

        if (query.isNullOrBlank()) { filler.generateReplacementBlock {  } } // TODO: query by name
        else { filler.generateReplacementBlock { generateByQuery(cp, repo, method) } }

        return filler.buildBody()
    }

    private fun JcSingleInstructionTransformer.BlockGenerationContext.generateByQuery(
        cp : JcClasspath,
        repo : JcClassOrInterface,
        method : JcMethod
    ) {
        val parserRes = JcRepositoryTransformer.getCtx(method)!!
        val res = parserRes.genInst(cp, repo, method, this)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
