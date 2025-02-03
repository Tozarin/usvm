package org.usvm.machine.interpreter.transformers.SpringJPA

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcNeqExpr
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.long
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.ParameterInfo
import org.objectweb.asm.Opcodes
import org.usvm.instrumentation.util.stringType
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.machine.state.concreteMemory.toTypedMethod
import org.usvm.util.JcTableInfoCollector
import org.usvm.util.TableInfo
import org.usvm.util.blancAnnotation
import org.usvm.util.genericTypes
import org.usvm.util.toArgument
import java.time.LocalDateTime

enum class Datetime {
    Year, Month, Day, Week, Quarter, Hour, Minute, Second, Nanosecond, Epoch
}

abstract class TypeCtx {

    abstract fun getType(info : CommonInfo) : JcType

    abstract class Primitive : TypeCtx() {

        class String : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.stringType() } }
        class Bool : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.boolean } }
        class Int : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.int } }
        class Long : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.long } }
        class Float : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.float } }
        class Double : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.cp.double } }
        class BigInt : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.bigIntType } }
        class BigDecimal : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.bigDecimalType } }
        class Binary : Primitive() { override fun getType(info : CommonInfo) : JcType { return info.byteArrType } }
    }

    class Null : TypeCtx() {
        override fun getType(info: CommonInfo): JcType {
            TODO("Not yet implemented")
        }
    }

    class Param(val param : Parameter) : TypeCtx() {
        override fun getType(info: CommonInfo): JcType {
            TODO("Not yet implemented")
        }
    }

    class Path(val name : SimplePathCtx) : TypeCtx() {
        override fun getType(info : CommonInfo) : JcType {
            val tbl = info.tblAliases[name.root]
            return if (tbl != null && name.cont.isEmpty()) {
                val tblInfo = tbl.getTbl(info)
                val origClass = tblInfo.origClass
                origClass.toType()
            }
            else {
                val fieldName = name.cont.single() // TODO:
                val pos = info.positions.single { it.origField.name == fieldName }
                pos.origField.type.toJcType(info.cp)!!
            }
        }
    }

    class Tuple(val types : List<TypeCtx>) : TypeCtx() {
        override fun getType(info: CommonInfo): JcType {
            TODO("Not yet implemented")
        }
    }
}

class OrderCtx(
    val sorts : List<SortSpec>,
    private var limit : ParamOrInt? = null,
    private var offset : ParamOrInt? = null
) {


    fun getLambdas(info : CommonInfo) : List<JcMethod> {
        return sorts.flatMap { it.getLambdas(info) }
    }

    abstract class ParamOrInt {

        abstract fun genInst(info : CommonInfo) : JcLocalVar

        class Param(val param : Parameter) : ParamOrInt() {
            override fun genInst(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }
        }

        class Num(val value : Int) : ParamOrInt() {
            override fun genInst(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }
        }
    }

    abstract class SortSpec(
        var dir : Boolean = true, // true -  ASC, false - DESC
        var nulls : Boolean = true // true - LAST, false - FIRST
    ) {

        abstract fun getLambdas(info : CommonInfo) : List<JcMethod>

        abstract fun getTranslate(info : CommonInfo) : JcLocalVar
        abstract fun getComparer(info : CommonInfo) : JcLocalVar

        class ByIdent(val name : String) : SortSpec() {
            override fun getLambdas(info: CommonInfo): List<JcMethod> {
                TODO("Not yet implemented")
            }

            override fun getTranslate(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }

            override fun getComparer(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }
        }

        class ByPos(val pos : Int) : SortSpec() {
            override fun getLambdas(info: CommonInfo): List<JcMethod> {
                TODO("Not yet implemented")
            }

            override fun getTranslate(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }

            override fun getComparer(info: CommonInfo): JcLocalVar {
                TODO("Not yet implemented")
            }
        }

        class ByExpr(val expr : ExpressionCtx) : SortSpec() {

            override fun getLambdas(info : CommonInfo) : List<JcMethod> {
                return listOf(getTranslateMethod(info))
            }

            fun getTranslateMethod(info : CommonInfo) : JcMethod {
                val methodName = info.getLambdaName()
                val newFeatures = JcFeaturesChain(
                    info.cp.features!!.toPersistentList().add(0, TranslateFeature(info, expr, methodName))
                )
                val retType = expr.type.getType(info).typeName.jvmName()

                val methodInfo = MethodInfo(
                    methodName,
                    "([Ljava/lang/Object;)$retType",
                    null,
                    Opcodes.ACC_STATIC,
                    listOf(blancAnnotation(REPOSITORY_LAMBDA)),
                    listOf(),
                    listOf(ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf()))
                )
                return JcMethodImpl(methodInfo, newFeatures, info.repo)
            }

            override fun getTranslate(info : CommonInfo) : JcLocalVar {
                val method = getTranslateMethod(info)
                val lambda = info.genCtx.generateLambda("${info.getLambdaName()}Var", method)
                return lambda
            }

            class TranslateFeature(val info : CommonInfo, val expr : ExpressionCtx, val methodName : String) : JcMethodExtFeature {
                override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {
                    if (method.name != methodName) return null

                    val filler = JcMethodBodyFiller(method)
                    filler.generateReplacementBlock { generateBody(method) }
                    return filler.buildBody()
                }

                private fun BlockGenerationContext.generateBody(method : JcMethod) {
                    val newInfo = CommonInfo(info.cp, info.query, info.repo, method, this)
                    val expr = expr.genInst(newInfo)
                    addInstruction { loc -> JcReturnInst(loc, expr) }
                }
            }

            override fun getComparer(info : CommonInfo) : JcLocalVar {

                val exprType = expr.type.getType(info) as JcClassType
                val method = info.orderType.declaredMethods.single {
                    it.isStatic && it.parameters.first().type == exprType }
                    .method

                val lambda = info.genCtx.generateLambda("${info.getLambdaName()}Var", method)
                return lambda
            }
        }
    }

    fun setLimit(lim : ParamOrInt?) { limit = lim }
    fun setOffset(off : ParamOrInt?) { offset = off }

    fun applyOrder(
        tbl : JcLocalVar,
        info : CommonInfo
    ) : JcLocalVar {

        return sorts.foldIndexed(tbl) { ix, acc, spec ->
            val translate = spec.getTranslate(info)
            val comparer = spec.getComparer(info)
            val lim = if (ix + 1 != sorts.size || limit == null) JcInt(-1, info.cp.int) else limit!!.genInst(info)
            val off = if (ix + 1 != sorts.size || offset == null) JcInt(0, info.cp.int) else offset!!.genInst(info)
            val dir = JcBool(spec.dir, info.cp.boolean)
            val nulls = JcBool(spec.nulls, info.cp.boolean)
            val args = listOf(acc, lim, off, dir, nulls, translate, comparer)
            info.genCtx.generateNewWithInit("sortWrap$ix", info.orderType, args)
        }
    }

}

class QueryCtx(
    val from : FromCtx?,
    val where : WhereCtx?,
    val select : SelectFunCtx?
) {

    fun collectTblAliases() : Map<String, TableCtx> {
        return from?.collectAliases() ?: mapOf()
    }

    fun collectSelAliases() : Map<String, SelectFunCtx.SelectionCtx> {
        return select?.collectAliases() ?: mapOf()
    }

    fun collectPositions(info : CommonInfo) : List<TableInfo.ColumnInfo> {
        return from?.collectPositions(info) ?: listOf()
    }

    fun getLambdas(info : CommonInfo) : List<JcMethod> {
        return listOf(
            from?.getLambdas() ?: listOf(),
            where?.getLambdas(info) ?: listOf(),
            select?.getLambdas(info) ?: listOf()
        )
            .flatten()
    }

    fun genInst(info : CommonInfo) : JcLocalVar {

        val fromRes = from?.genInst(info)!! // TODO: no FROM part
        val whereFun = where?.getLambdaVar(info)
        val selFun = select?.getLambdaVar(info)!! // TODO: no SELECT part

        val filtred = whereFun?.let {
            info.genCtx.generateNewWithInit("resFiltred", info.filterType, listOf(fromRes, it))
        }
            ?: fromRes

        val retType = info.cp.findType(info.origRetGeneric)
        val classType = info.cp.findType("java.lang.Class")
        val typeVar = info.genCtx.nextLocalVar("type", classType)
        val type = JcClassConstant(retType, classType)
        info.genCtx.addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapped = info.genCtx.generateNewWithInit("mapped", info.mapperType, listOf(filtred, selFun, typeVar))
        if (select.isDistinct) return info.genCtx.generateNewWithInit("dis", info.distinctType, listOf(mapped))

        return mapped
    }
}

abstract class TableCtx(
    val alias : String?
) {

    abstract fun genLambas() : List<JcMethod>
    abstract fun positions(info : CommonInfo) : List<TableInfo.ColumnInfo>
    abstract fun genInst(info : CommonInfo) : JcLocalVar
    abstract fun getTbl(info : CommonInfo) : TableInfo.TableWithIdInfo

    // ... FROM SomeTable AS st
    class TableRootCtx(
        val entityName : EntityNameCtx,
        alias : String?
    ) : TableCtx(alias) {

        // Name of class (may contain points: java.lang.Boolean)
        // TODO: polymorphism like FROM java.lang.Object
        class EntityNameCtx(val names : List<String>) {
            val name = names.joinToString(separator = ".")

            override fun toString() : String { return name }
        }

        override fun genLambas() : List<JcMethod> { return listOf() }

        private var cachedTbl : TableInfo.TableWithIdInfo? = null
        override fun getTbl(info : CommonInfo) : TableInfo.TableWithIdInfo {
            if (cachedTbl != null) return cachedTbl as TableInfo.TableWithIdInfo
            cachedTbl = info.collector.getTableByPartName(entityName.name).single() // it can be resolved
            return cachedTbl as TableInfo.TableWithIdInfo
        }

        override fun positions(info : CommonInfo) : List<TableInfo.ColumnInfo> {
            return getTbl(info).columnsInOrder()
        }

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val classTable = getTbl(info)
            val field = info.databases.declaredFields.single { it.name == classTable.name }

            val tbl = info.genCtx.nextLocalVar(classTable.name, field.type)
            val ref = JcFieldRef(null, field)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, tbl, ref) }

            return tbl
        }
    }

    // ... FROM (SELECT ...) AS sub
    class TableSubqueryCtx(
        val subqury : SelectCtx,
        alias : String?
    ) : TableCtx(alias) {

        override fun genLambas(): List<JcMethod> {
            TODO("Not yet implemented")
        }

        override fun positions(info : CommonInfo) : List<TableInfo.ColumnInfo> {
            TODO("Not yet implemented")
        }

        override fun genInst(info : CommonInfo) : JcLocalVar {
            TODO("Not yet implemented")
        }

        override fun getTbl(info : CommonInfo) : TableInfo.TableWithIdInfo {
            TODO("Not yet implemented")
        }
    }
}

open class JoinCtx() {

    class JpaCollectionJoin() : JoinCtx() {} // deprecated syntax (never documented)

    class Join() : JoinCtx()
    class CrossJoin() : JoinCtx()
}

class TableWithJoinsCtx(
    val root : TableCtx,
    val joins : List<JoinCtx>
) {

    fun getLambdas() : List<JcMethod> {
        // TODO: joins
        return root.genLambas()
    }

    fun collectPositions(info : CommonInfo) : List<TableInfo.ColumnInfo> {
        val rootPos = root.positions(info)
        // TODO: joinPos
        return rootPos
    }

    fun genInst(info : CommonInfo) : JcLocalVar {
        // TODO: joins
        return root.genInst(info)
    }
}

class FromCtx(
    val tables : List<TableWithJoinsCtx>
) {

    fun getLambdas() : List<JcMethod> {
        return tables.flatMap { it.getLambdas() }
    }

    fun collectAliases() : Map<String, TableCtx> {
        return tables.mapNotNull { tbl -> tbl.root.alias?.let { it to tbl.root } }
            .associate { p -> p }
    }

    fun collectPositions(info : CommonInfo) : List<TableInfo.ColumnInfo> {
        return tables.map { it.collectPositions(info) }.flatten()
    }

    fun genInst(info : CommonInfo) : JcLocalVar {
        val tbl = tables.single() // TODO: make "FROM Foo, Bar, Baz" as join
        return tbl.genInst(info)
    }
}

class InstCtx() {

}

class PathCtx() {

}

class SimplePathCtx(
    val root : String, // not case-sensitive
    val cont : List<String> // case-sensitive
) {

    fun flat() : String { return root + cont.joinToString(prefix = ".") }
}

class GeneralPathCtx(
    val path : SimplePathCtx,
    val index : Index?
) {
    class Index(val ix : ExpressionCtx, val cont : GeneralPathCtx?)

    // TODO: indexing
    fun fullPath() : SimplePathCtx { return path }
}

open class FunctionCtx {

}

abstract class ExprOrPredCtx {
    abstract val type : TypeCtx
    abstract fun genInst(info : CommonInfo) : JcLocalVar

}

// Argument from original function
abstract class Parameter {

    abstract fun genInst(info : CommonInfo) : JcLocalVar

    // Simple name
    class Colon(val name : String) : Parameter() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    // ?3
    class Positional(val pos : Int) : Parameter() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}

abstract class ExpressionCtx : ExprOrPredCtx() {

    class Tuple(val elems : List<ExprOrPredCtx>) : ExpressionCtx() {
        override val type : TypeCtx = TypeCtx.Tuple(elems.map { it.type })

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Subquery(val query : SelectCtx) : ExpressionCtx() {
        override val type: TypeCtx
            get() = TODO("Not yet implemented")
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    // CASE a WHEN 'a' THEN 1 WHEN 'b' THEN 2 ELSE 3 END
    class SimpleCaseList(
        val caseValue : ExprOrPredCtx,
        val branches : List<BranchCtx>,
        val elseBranch : ExprOrPredCtx?
    ) : ExpressionCtx() {
        class BranchCtx(val expr : ExpressionCtx, val value : ExprOrPredCtx)

        override val type : TypeCtx = branches.first().value.type

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    // CASE WHEN a == 'a' THEN 1 WHEN a == 'b' THEN 2 ELSE 3 END
    class CaseList(val branches : List<BranchCtx>, val elseBranch : ExprOrPredCtx?) : ExpressionCtx() {
        class BranchCtx(val pred : PredicateCtx, val value : ExprOrPredCtx)

        override val type : TypeCtx = branches.first().value.type

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class LString(val value : String) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.String()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.strType)
            val str = JcStringConstant(value, info.strType)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, str) }
            return v
        }
    }

    class LNull : ExpressionCtx() {

        override val type = TypeCtx.Null()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.objectType)
            val n = JcNullConstant(info.cp.objectType)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, n) }
            return v
        }
    }

    class LBool(val value : Boolean) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Bool()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.boolean)
            val b = if (value) info.jcTrue else info.jcFalse
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, b) }
            return v
        }
    }

    class LInt(val value : Int) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Int()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.int)
            val i = JcInt(value, info.cp.int)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, i) }
            return v
        }
    }

    class LLong(val value : Long) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Long()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.long)
            val l = JcLong(value, info.cp.long)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, l) }
            return v
        }
    }

    class LBigInt(val value : String) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.BigInt()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.bigIntType)
            val init = JcNewExpr(info.bigIntType)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

            val bigInit = info.bigIntType.declaredMethods
                .single { it.name == "<init>" && it.parameters.first().type == info.strType }
            val str = JcStringConstant(value, info.strType)
            val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
            info.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
            return v
        }
    }

    class LFloat(val value : Float) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Float()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.float)
            val f = JcFloat(value, info.cp.float)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, f) }
            return v
        }
    }

    class LDouble(val value : Double) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Double()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val v = info.newVar(info.cp.double)
            val d = JcDouble(value, info.cp.double)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
            return v
        }
    }

    class LBigDecimal(val value : String) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.BigDecimal()

        override fun genInst(info: CommonInfo): JcLocalVar {
            val v = info.newVar(info.bigDecimalType)
            val init = JcNewExpr(info.bigDecimalType)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

            val bigInit = info.bigDecimalType.declaredMethods
                .single { it.name == "<init>" && it.parameters.first().type == info.strType }
            val str = JcStringConstant(value, info.strType)
            val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
            info.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
            return v
        }
    }

    class LBinary(val bins : ByteArray) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Binary()

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val size = bins.size
            val v = info.newVar(info.byteArrType)
            val arr = JcNewArrayExpr(info.byteArrType, listOf(JcInt(size, info.cp.int)))
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, arr) }

            bins.forEachIndexed { ix, b ->
                val elem = JcArrayAccess(v, JcInt(ix, info.cp.int), info.cp.byte)
                val value = JcByte(b, info.cp.byte)
                info.genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
            }

            return v
        }
    }

    class LTime(val time : LocalDateTime) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Param(val param : Parameter) : ExpressionCtx() {

        override val type = TypeCtx.Param(param)

        override fun genInst(info : CommonInfo) : JcLocalVar {
            return param.genInst(info)
        }
    }

    class TypeOfParam(val param : Parameter) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class TypeOfPath(val path : PathCtx) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Id(val path : PathCtx, val cont : SimplePathCtx?) : ExpressionCtx() {

        override val type = TypeCtx.Primitive.Bool()

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Version(val path : PathCtx) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class NaturalId(val path : PathCtx, val cont : SimplePathCtx?) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class SyntacticPath() : ExpressionCtx() { // TODO:

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Function(val function : FunctionCtx) : ExpressionCtx() {

        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class GeneralPath(val path : GeneralPathCtx) : ExpressionCtx() {

        val simplePath = path.fullPath() // TODO: indexing

        override val type = TypeCtx.Path(path.fullPath())

        override fun genInst(info : CommonInfo) : JcLocalVar {
            val tbl = info.tblAliases[simplePath.root]
            return if (simplePath.cont.isEmpty() && tbl != null) genObj(info, tbl)
            else genField(info)
        }

        private fun genObj(info : CommonInfo, tbl : TableCtx) : JcLocalVar {
            // tbl != null invariant

            val pos = tbl.positions(info)
            val indexes = pos.map { info.positions.indexOf(it) }
            val size = indexes.size
            val newRow = info.newVar(info.objectArrType)
            val arr = JcNewArrayExpr(info.objectArrType, listOf(JcInt(size, info.cp.int)))
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, newRow, arr) }

            val row = info.method.parameters.first().toArgument
            indexes.forEachIndexed { ix, oldIx ->
                val elem = JcArrayAccess(newRow, JcInt(ix, info.cp.int), info.cp.objectType)
                val value = JcArrayAccess(row, JcInt(oldIx, info.cp.int), info.cp.objectType)
                info.genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
            }

            val tblInfo = tbl.getTbl(info)
            val origClass = tblInfo.origClass
            val res = info.newVar(origClass.toType())
            val obj = JcNewExpr(origClass.toType())
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, res, obj) }

            val init = origClass.declaredMethods.single { it.generatedInit }
            if (info.fetched.isEmpty()) { info.addFetched(genFetch(info, tblInfo)) }
            val arg = info.fetched.toPersistentList().add(0, newRow)
            val initCall = JcSpecialCallExpr(init.toTypedMethod.methodRef, res, arg)
            info.genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }

            return res
        }

        private fun genFetch(info : CommonInfo, tblInfo : TableInfo.TableWithIdInfo) : List<JcLocalVar> {
            return tblInfo.orderedRelations().map { rel ->
                val relTblName = rel.toTableName(info.cp)
                val tblField = info.databases.fields.single { it.name == relTblName }
                val v = info.newVar(tblField.type)
                val field = JcFieldRef(null, tblField)
                info.genCtx.addInstruction { loc -> JcAssignInst(loc, v, field) }
                v
            }
        }

        private fun genField(info : CommonInfo) : JcLocalVar {

            val fieldName = simplePath.cont.single() // TODO:
            val pos = info.positions.single { it.origField.name == fieldName }
            val ix = info.positions.indexOf(pos)
            val row = info.method.parameters.first().toArgument

            val f = info.newVar(info.cp.objectType)
            val elem = JcArrayAccess(row, JcInt(ix, info.cp.int), info.cp.objectType)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, f, elem) }

            val fType = pos.type.toJcType(info.cp)!!
            val casted = info.newVar(fType)
            val cast = JcCastExpr(fType, f)
            info.genCtx.addInstruction { loc -> JcAssignInst(loc, casted, cast) }

            return casted
        }
    }

    class Minus(val expr : ExpressionCtx) : ExpressionCtx() {

        override val type = expr.type

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class ToDuration(val expr : ExpressionCtx, val datetime : Datetime) : ExpressionCtx() {
        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class FromDuration(val expr : ExpressionCtx, val datetime : Datetime) : ExpressionCtx() {
        override val type: TypeCtx
            get() = TODO("Not yet implemented")

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class BinOperator(val left : ExpressionCtx, val right : ExpressionCtx, val operator : Operator) : ExpressionCtx() {

        override val type = left.type

        enum class Operator {
            Slash, Percent, Asterisk, Plus, Minus, Concat
        }

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}

abstract class PredicateCtx : ExprOrPredCtx() {

    override val type = TypeCtx.Primitive.Bool()

    fun makeNot(not : Any?) : PredicateCtx {
        return if (not == null) this else Not(this)
    }

    class Not(val predicate : PredicateCtx) : PredicateCtx() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            val pr = predicate.genInst(info)
            val cond = JcNeqExpr(info.cp.boolean, pr, info.jcTrue)
            val ifRes = info.genCtx.compare(info.cp, cond, info.getPredicateName())
            return ifRes
        }
    }

    class And(val left : PredicateCtx, val right : PredicateCtx) : PredicateCtx() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            val genCtx = info.genCtx
            val l = left.genInst(info)
            val r = right.genInst(info)

            // 0. if l == false (jmp 4) (next)
            // 1. if r == false (jmp 4) (next)
            // 2. %0 = true
            // 3. goto 6
            // 4. %0 = false
            // 5. goto 6
            // 6. return %0
            val falseRes : JcInstRef
            val endOfIf : JcInstRef
            genCtx.addInstruction { loc ->
                val cond = JcEqExpr(info.cp.boolean, l, info.jcFalse)
                falseRes = JcInstRef(loc.index + 4)
                val nextInst = JcInstRef(loc.index + 1)
                endOfIf = JcInstRef(loc.index + 6)
                JcIfInst(loc, cond, falseRes, nextInst)
            }
            genCtx.addInstruction { loc ->
                val cond = JcEqExpr(info.cp.boolean, r, info.jcFalse)
                val nextInst = JcInstRef(loc.index + 1)
                JcIfInst(loc, cond, falseRes, nextInst)
            }

            val resVal = genCtx.nextLocalVar(info.getPredicateName(), info.cp.boolean)
            genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, info.jcTrue) }
            genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }
            genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, info.jcFalse) }
            genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }

            return resVal
        }
    }

    class Or(val left : PredicateCtx, val right : PredicateCtx) : PredicateCtx() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            val genCtx = info.genCtx
            val l = left.genInst(info)
            val r = right.genInst(info)

            // 0. if l == true (jmp 2) (next)
            // 1. if r == false (jmp 4) (next)
            // 2. %0 = true
            // 3. goto 6
            // 4. %0 = false
            // 5. goto 6
            // 6. return %0
            val endOfIf : JcInstRef
            genCtx.addInstruction { loc ->
                val cond = JcEqExpr(info.cp.boolean, l, info.jcTrue)
                val trueBranch = JcInstRef(loc.index + 2)
                val nextInst = JcInstRef(loc.index + 1)
                endOfIf = JcInstRef(loc.index + 6)
                JcIfInst(loc, cond, trueBranch, nextInst)
            }
            genCtx.addInstruction { loc ->
                val cond = JcEqExpr(info.cp.boolean, r, info.jcFalse)
                val trueBranch = JcInstRef(loc.index + 3)
                val nextInst = JcInstRef(loc.index + 1)
                JcIfInst(loc, cond, trueBranch, nextInst)
            }

            val resVal = genCtx.nextLocalVar(info.getPredicateName(), info.cp.boolean)
            genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, info.jcTrue) }
            genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }
            genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, info.jcFalse) }
            genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }

            return resVal
        }
    }

    class BoolExpr(val expression : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            return expression.genInst(info)
        }
    }

    class IsNull(val expression : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsEmpty(val expression : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsTrue(val expression : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            return expression.genInst(info)
        }
    }

    class IsDistinct(val expression : ExpressionCtx, val from : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Member(val expression : ExpressionCtx, val of : PathCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class In(val expression : ExpressionCtx, val list : ListCtx) : PredicateCtx() {
        class ListCtx()

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Between(val expression : ExpressionCtx, val left : ExpressionCtx, val right : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Like(
        val expression : ExpressionCtx,
        val pattern : ExpressionCtx,
        val likeEscape : LikeCtx?,
        val caseSenc : Boolean
    ) : PredicateCtx() {
        class LikeCtx()

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Compare(val left : ExpressionCtx, val right : ExpressionCtx, val operator : Operator) : PredicateCtx() {
        enum class Operator {
            Equal, NotEqual, Greater, GreaterEqual, Less, LessEqual
        }

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class ExistCollection(val quantifier : ColQuantifierCtx, val path : SimplePathCtx) : PredicateCtx() {
        class ColQuantifierCtx()

        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Exist(val expression : ExpressionCtx) : PredicateCtx() {
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}

class WhereCtx(
    val predicate : PredicateCtx
) {

    fun getLambdas(info : CommonInfo) : List<JcMethod> {
        return listOf(getMethod(info))
    }

    fun getMethod(info : CommonInfo) : JcMethod {

        val methodName = info.getLambdaName()
        val newFeatures = JcFeaturesChain(
            info.cp.features!!.toPersistentList().add(0, LambdaFeature(info, predicate, methodName))
        )

        val methodInfo = MethodInfo(
            methodName,
            "([Ljava/lang/Object;)Ljava/lang/Boolean;",
            null,
            Opcodes.ACC_STATIC,
            listOf(blancAnnotation(REPOSITORY_LAMBDA)),
            listOf(),
            listOf(ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf()))
        )
        return JcMethodImpl(methodInfo, newFeatures, info.repo)
    }

    fun getLambdaVar(info : CommonInfo) : JcLocalVar {
        val method = getMethod(info)
        val lambda = info.genCtx.generateLambda("${info.getLambdaName()}Var", method)
        return lambda
    }

    class LambdaFeature(val info : CommonInfo, val predicate : PredicateCtx, val name : String) : JcMethodExtFeature {

        override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {
            if (method.name != name) return null

            val filler = JcMethodBodyFiller(method)
            filler.generateReplacementBlock { generateBody(method) }
            return filler.buildBody()
        }

        private fun BlockGenerationContext.generateBody(method : JcMethod) {
            val newInfo = CommonInfo(info.cp, info.query, info.repo, method, this)
            val pr = predicate.genInst(newInfo)
            addInstruction { loc -> JcReturnInst(loc, pr) }
        }
    }
}

class SelectFunCtx(
    val isDistinct : Boolean,
    val selections : List<SelectionCtx>
) {

    abstract class SelectionCtx(var alias : String?) {
        abstract fun genInst(info : CommonInfo) : JcLocalVar
    }

    class Inst(val inst : InstCtx, alias : String?) : SelectionCtx(alias) { // TODO
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Entry(val path : PathCtx, alias : String?) : SelectionCtx(alias) { // TODO
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class JpaSelect(alias : String?) : SelectionCtx(alias) { // TODO: deprecated
        override fun genInst(info: CommonInfo): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Expr(val value : ExprOrPredCtx, alias : String?) : SelectionCtx(alias) {
        override fun genInst(info : CommonInfo) : JcLocalVar {
            return value.genInst(info)
        }
    }

    fun collectAliases() : Map<String, SelectionCtx> {
        return selections.mapNotNull { s -> s.alias?.let { it to s } }
            .associate { p -> p }
    }

    fun getLambdas(info : CommonInfo) : List<JcMethod> {
        return listOf(getMethod(info))
    }

    fun getMethod(info : CommonInfo) : JcMethod {
        val methodName = info.getLambdaName()
        val newFeatures = JcFeaturesChain(
            info.cp.features!!.toPersistentList().add(0, SelectFuture(info, this, methodName))
        )
        val retType = info.origRetGeneric.jvmName()
        val desc = "([Ljava/lang/Object;)$retType"
        val methodInfo = MethodInfo(
            methodName,
            desc,
            null,
            Opcodes.ACC_STATIC,
            listOf(blancAnnotation(REPOSITORY_LAMBDA)),
            listOf(),
            listOf(ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf()))
        )
        return JcMethodImpl(methodInfo, newFeatures, info.repo)
    }

    fun getLambdaVar(info : CommonInfo) : JcLocalVar {
        val method = getMethod(info)
        val lambda = info.genCtx.generateLambda("${info.getLambdaName()}Var", method)
        return lambda
    }

    class SelectFuture(val info : CommonInfo, val select : SelectFunCtx, val name : String) : JcMethodExtFeature {

        override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {
            if (method.name != name) return null

            val filler = JcMethodBodyFiller(method)
            filler.generateReplacementBlock { generateBody(select, method) }
            return filler.buildBody()
        }

        private fun BlockGenerationContext.generateBody(select : SelectFunCtx, method : JcMethod) {
            val newInfo = CommonInfo(info.cp, info.query, info.repo, method, this)

            val selVars = select.selections.map { it.genInst(newInfo) }
            if (newInfo.origRetGeneric != "java.lang.Object[]") {
                newInfo.genCtx.addInstruction { loc -> JcReturnInst(loc, selVars.single()) }
            }
            else {
                val res = newInfo.newVar(newInfo.objectArrType)
                val arr = JcNewArrayExpr(newInfo.objectArrType, listOf(JcInt(selVars.size, newInfo.cp.int)))
                newInfo.genCtx.addInstruction { loc -> JcAssignInst(loc, res, arr) }

                selVars.forEachIndexed { ix, s ->
                    val ass = JcArrayAccess(res, JcInt(ix, newInfo.cp.int), newInfo.cp.objectType)
                    newInfo.genCtx.addInstruction { loc -> JcAssignInst(loc, ass, s) }
                }

                newInfo.genCtx.addInstruction { loc -> JcReturnInst(loc, res) }
            }
        }
    }
}

class SelectCtx(
    val orders : List<OrderCtx>,
    val query : QueryCtx
) {
    fun addOrder(order : List<OrderCtx>) {
        orders.toMutableList().addAll(order)
    }

    fun genInst(
        cp : JcClasspath,
        repo : JcClassOrInterface,
        method : JcMethod,
        genCtx : JcSingleInstructionTransformer.BlockGenerationContext
    ) : JcLocalVar {
        val info = CommonInfo(cp, query, repo, method, genCtx)

        val queryVar = query.genInst(info)
        val ordered = orders.fold(queryVar) { p, order -> order.applyOrder(p, info) }

        val wrapperType = getWrapperType(info, method.returnType)!!
        val wrapper = info.genCtx.generateNewWithInit("wrappedRes", wrapperType, listOf(ordered))

        return wrapper
    }

    private fun getWrapperType(info : CommonInfo, type : TypeName) : JcClassType? {
        return when (type.typeName) {
            "java.util.Set" -> info.setType
            "java.util.List" -> info.listType
            // TODO: more collections
            else -> null
        }
    }

    fun getLambdas(
        cp : JcClasspath,
        repo : JcClassOrInterface,
        method : JcMethod,
        ) : List<JcMethod> {

        val info = CommonInfo(cp, query, repo, method, null)
        val queryLambdas = query.getLambdas(info)
        val orderLambas = orders.flatMap { it.getLambdas(info) }

        return queryLambdas.toPersistentList().addAll(orderLambas)
    }
}

class CommonInfo(
    val cp : JcClasspath,
    val query : QueryCtx,
    val repo : JcClassOrInterface,
    val method : JcMethod,
    genCtx : JcSingleInstructionTransformer.BlockGenerationContext?
) {

    val genCtx by lazy { genCtx!! }

    val collector : JcTableInfoCollector get() {
        return JcTableInfoCollector(cp).also {
            val dataClass = cp.findClass(repo.signature!!.genericTypes[0])
            it.collectTable(dataClass)
        }
    }

    val origRetGeneric : String = method.signature?.let { it.genericTypes[0] } ?: method.returnType.typeName

    var namesCounter = 0
    fun getLambdaName() : String { return "\$lambda#${namesCounter++}" }
    fun getPredicateName() : String { return "\$predicate#${namesCounter++}" }
    fun getVarName() : String { return "#${namesCounter++}" }

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

    fun addPositions(names : List<TableInfo.ColumnInfo>) {
        positions.toMutableList().addAll(names)
    }

    fun addFetched(fetched : List<JcLocalVar>) {
        fetched.toMutableList().addAll(fetched)
    }

    fun boolCons(v : Boolean) : JcBool { return JcBool(v, cp.boolean) }
    val jcTrue = boolCons(true)
    val jcFalse = boolCons(false)

    fun newVar(type : JcType) : JcLocalVar {
        return genCtx.nextLocalVar(getVarName(), type)
    }
}
