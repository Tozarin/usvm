package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.DATABASES
import org.usvm.machine.interpreter.transformers.springjpa.DISTINCT_TABLE
import org.usvm.machine.interpreter.transformers.springjpa.FILTER_TABLE
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_BIG_DECIMAL
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_BIG_INT
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_BOOL
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_STRING
import org.usvm.machine.interpreter.transformers.springjpa.JOIN_TABLE
import org.usvm.machine.interpreter.transformers.springjpa.LIST_WRAPPER
import org.usvm.machine.interpreter.transformers.springjpa.MAP_TABLE
import org.usvm.machine.interpreter.transformers.springjpa.SET_WRAPPER
import org.usvm.machine.interpreter.transformers.springjpa.SORTED_TABLE
import org.usvm.util.JcTableInfoCollector
import org.usvm.util.TableInfo
import org.usvm.util.genericTypes

data class CommonInfo(
    val cp: JcClasspath,
    val query: QueryCtx,
    val repo: JcClassOrInterface,
    val method: JcMethod
) {
    val collector: JcTableInfoCollector
        get() {
            return JcTableInfoCollector(cp).also {
                val dataClass = cp.findClass(repo.signature!!.genericTypes[0])
                it.collectTable(dataClass)
            }
        }

    val names = NamesManager()

    val origReturnGeneric: String = method.signature?.let { it.genericTypes[0] } ?: method.returnType.typeName

    val tblAliases = query.collectTblAliases()
    val selAliases = query.collectSelAliases()
    val positions = query.collectPositions(this)
    val fetched = listOf<JcLocalVar>()

    val databases = cp.findType(DATABASES) as JcClassType
    val setType = cp.findType(SET_WRAPPER) as JcClassType
    val listType = cp.findType(LIST_WRAPPER) as JcClassType
    val mapperType = cp.findType(MAP_TABLE) as JcClassType
    val filterType = cp.findType(FILTER_TABLE) as JcClassType
    val distinctType = cp.findType(DISTINCT_TABLE) as JcClassType
    val orderType = cp.findType(SORTED_TABLE) as JcClassType
    val joinType = cp.findType(JOIN_TABLE) as JcClassType

    val boolType = cp.findType(JAVA_BOOL) as JcClassType
    val strType = cp.findType(JAVA_STRING) as JcClassType
    val bigIntType = cp.findType(JAVA_BIG_INT) as JcClassType
    val bigDecimalType = cp.findType(JAVA_BIG_DECIMAL) as JcClassType
    val byteArrType = cp.arrayTypeOf(cp.byte, false, listOf()) // TODO: check nullability = false
    val objectArrType = cp.arrayTypeOf(cp.objectType, false, listOf())

    val booleanValue = boolType.declaredMethods.single { it.name == "booleanValue" }
    val castToBool = boolType.declaredMethods.single {
        it.isStatic && it.name == "valueOf" && it.parameters.first().type.typeName == "boolean"
    }

    val jcTrue = JcBool(true, cp.boolean)
    val jcFalse = JcBool(false, cp.boolean)
}

class NamesManager {
    var namesCounter = 0
    fun getLambdaName(): String {
        return "\$lambda#${namesCounter++}"
    }

    fun getMethodName(): String {
        return "\$method#${namesCounter++}"
    }

    fun getPredicateName(): String {
        return "\$predicate#${namesCounter++}"
    }

    fun getVarName(): String {
        return "#${namesCounter++}"
    }
}

class MethodCtx(
    val cp: JcClasspath,
    query: QueryCtx,
    repo: JcClassOrInterface,
    method: JcMethod,
    val genCtx: JcSingleInstructionTransformer.BlockGenerationContext
) {

    constructor(info: CommonInfo, genCtx: JcSingleInstructionTransformer.BlockGenerationContext)
            : this(info.cp, info.query, info.repo, info.method, genCtx)

    val common = CommonInfo(cp, query, repo, method)
    val names = common.names

    fun getLambdaName(): String {
        return names.getLambdaName()
    }

    fun getMethodName(): String {
        return names.getMethodName()
    }

    fun getVarName(): String {
        return names.getVarName()
    }

    fun getPredicateName(): String {
        return names.getPredicateName()
    }

    fun addPositions(names: List<TableInfo.ColumnInfo>) {
        common.positions.toMutableList().addAll(names)
    }

    fun addFetched(fetched: List<JcLocalVar>) {
        common.fetched.toMutableList().addAll(fetched)
    }

    fun newVar(type: JcType): JcLocalVar {
        return genCtx.nextLocalVar(common.names.getVarName(), type)
    }
}
