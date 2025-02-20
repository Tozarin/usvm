package org.usvm.machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.toPersistentList
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hibernate.grammars.hql.HqlLexer
import org.hibernate.grammars.hql.HqlParser
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.query.JPAQueryVisitor
import org.usvm.util.genericTypes
import org.usvm.util.getTableName
import org.usvm.util.isVoid


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
            if (query == null
                || query == "SELECT DISTINCT owner FROM Owner owner left join  owner.pets WHERE owner.lastName LIKE :lastName% "
            ) {
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
        return !method.repositoryLambda && method.enclosingClass.isJpaRepository && method.query != null
    }

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath

        val parserRes = JcRepositoryTransformer.getCtx(method)!! // TODO: query by name
        val res = parserRes.genInst(cp, repo, method, this)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}

object JcRepositoryCrudTransformer : JcBodyFillerFeature() {

    val crudNames = listOf(
        "save",
        "saveAll",
        "delete",
        "deleteAll",
        "deleteAllById",
        "existById",
        "findAll",
        "findById",
        "findAllById"
    )

    val JcMethod.isCrud: Boolean get() = crudNames.contains(name)

    override fun condition(method: JcMethod): Boolean {
        return method.isCrud && method.enclosingClass.isJpaRepository && method.query == null
    }

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath
        val clazz = cp.findClass(repo.signature!!.genericTypes[0])

        val tblV = nextLocalVar("tbl", cp.findType(ITABLE))
        val tblField = (cp.findType(DATABASES) as JcClassType).fields.single { it.name == getTableName(clazz) }
        val tblRef = JcFieldRef(null, tblField)
        addInstruction { loc -> JcAssignInst(loc, tblV, tblRef) }

        val baseTyp = cp.findType(BASE_TABLE)
        val castV = nextLocalVar("castedV", baseTyp)
        val cast = JcCastExpr(baseTyp, tblV)
        addInstruction { loc -> JcAssignInst(loc, castV, cast) }

        val serializer = clazz.declaredMethods.single { it.generatedSerializer }
        val serLmbd = generateLambda("serilizer", serializer)

        val deserializer = clazz.declaredMethods.single { it.generatedFetchInit }
        val desLmbd = generateLambda("deserilizer", deserializer)

        val crudTyp = cp.findType(CRUD_MANAGER) as JcClassType
        val crudManager = generateNewWithInit("crud", crudTyp, listOf(castV, serLmbd, desLmbd))

        val resType = if (method.isVoid) cp.objectType else method.returnType.toJcType(cp)!!
        val resV = nextLocalVar("res", resType)
        val crudMethod = crudTyp.declaredMethods.single {
            it.name == method.name && it.parameters.size == method.parameters.size
        }.let {
            VirtualMethodRefImpl.of(crudTyp, it)
        }
        val args = method.parameters.map { it.toArgument }
        val call = JcVirtualCallExpr(crudMethod, crudManager, args)
        addInstruction { loc -> JcAssignInst(loc, resV, call) }

        val res = if (method.isVoid) null else resV
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
