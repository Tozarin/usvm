package org.usvm.machine.interpreter.transformers.SpringJPA

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.BsmHandleTag
import org.jacodb.api.jvm.cfg.BsmMethodTypeArg
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.TypedMethodRefImpl
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.Relation
import org.usvm.util.TableInfo
import org.usvm.util.contains
import org.usvm.util.toArgument
import org.usvm.util.typedField

val JcMethod.generatedInit : Boolean get() = contains(this.annotations, INIT_ANNOT)
val JcTypedMethod.methodRef : TypedMethodRefImpl
    get() = TypedMethodRefImpl(
    enclosingType as JcClassType,
    name,
    method.parameters.map { it.type },
    method.returnType
)

const val DATABASES = "SpringDatabases"
const val ITABLE = "generated.org.springframework.boot.databases.ITable"
const val SET_WRAPPER = "generated.org.springframework.boot.databases.SetWrapper"
const val LIST_WRAPPER = "generated.org.springframework.boot.databases.ListWrapper"
const val MAP_TABLE = "generated.org.springframework.boot.databases.MappedTable"
const val FILTER_TABLE = "generated.org.springframework.boot.databases.FiltredTable"
const val SORTED_TABLE = "generated.org.springframework.boot.databases.SortedTable"
const val JOIN_TABLE = "generated.org.springframework.boot.databases.JoinedTable"
const val DISTINCT_TABLE = "generated.org.springframework.boot.databases.DistinctTable"

const val PREDICATE = "java.util.function.Predicate"
const val FUNCTION = "java.util.function.Function"

fun BlockGenerationContext.generateNew(name : String, type : JcType) : JcLocalVar {
    val vari = nextLocalVar(name, type)
    val newExpr = JcNewExpr(type)
    addInstruction { loc -> JcAssignInst(loc, vari, newExpr) }
    return vari
}

fun BlockGenerationContext.generateNewWithInit(name : String, type : JcClassType, args : List<JcValue>) : JcLocalVar {
    val vari = generateNew(name, type)
    val init = type.declaredMethods.single { it.name == "<init>" && it.parameters.size == args.size }
    val call = JcSpecialCallExpr(init.methodRef, vari, args)
    addInstruction { loc -> JcCallInst(loc, call) }
    return vari
}

fun BlockGenerationContext.generateLambda(cp : JcClasspath, name : String, method : JcMethod) : JcLocalVar {
    val (callSiteName, callSiteRetType) = if (method.returnType.typeName == "java.lang.Boolean") {
        Pair("test", cp.findType(PREDICATE))
    }
    else { Pair("apply", cp.findType(FUNCTION))}

    val lambdaVar = nextLocalVar(name, callSiteRetType)
    val lambda = getLambda(cp, method, callSiteName, callSiteRetType)
    addInstruction { loc -> JcAssignInst(loc, lambdaVar, lambda) }

    return lambdaVar
}

fun BlockGenerationContext.generateLambda(name : String, method : JcMethod) : JcLocalVar {
    val cp = method.enclosingClass.classpath
    return generateLambda(cp, name, method)
}

fun getLambda(cp : JcClasspath, method : JcMethod, callSiteName : String, callSiteRetType : JcType) : JcLambdaExpr {

    val bsm = cp.findType("java.lang.invoke.LambdaMetafactory").let { it as JcClassType }
        .declaredMethods.single { it.name == "metafactory" }
        .methodRef

    val classType = method.enclosingClass.toType()
    val argTypes = method.parameters.map { it.type }
    val actualMethod = TypedMethodRefImpl(classType, method.name, argTypes, method.returnType)

    val interfaceMethodType = BsmMethodTypeArg(argTypes, method.returnType)
    val dynamicMethodType = BsmMethodTypeArg(argTypes.map { cp.objectType.getTypename() }, method.returnType)

    val callSiteArgTypes = listOf(classType as JcType)
    val callSiteArgs = listOf(JcThis(classType))

    return JcLambdaExpr(
        bsm,
        actualMethod,
        interfaceMethodType,
        dynamicMethodType,
        callSiteName,
        callSiteArgTypes,
        callSiteRetType,
        callSiteArgs,
        BsmHandleTag.MethodHandle.INVOKE_VIRTUAL
    )
}

class JcGeneratedInitTransformer(
    val cp : JcClasspath,
    val classTable : TableInfo.TableWithIdInfo,
    val origInit : JcMethod
) : JcMethodExtFeature {

    val clazz = classTable.origClass
    val classType = clazz.toType()
    val thisVal = JcThis(classType)
    val parlessInitRef = VirtualMethodRefImpl.of(classType, JcTypedMethodImpl(classType, origInit, JcSubstitutorImpl()))

    val itableType = cp.findType(ITABLE)
    val setType = cp.findType(SET_WRAPPER) as JcClassType
    val listType = cp.findType(LIST_WRAPPER) as JcClassType
    val mapType = cp.findType(MAP_TABLE) as JcClassType
    val filterType = cp.findType(FILTER_TABLE) as JcClassType

    val functionType = cp.findType(FUNCTION)
    val predicateType = cp.findType(PREDICATE)

    override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {

        if (!method.generatedInit) return null

        val filler = JcMethodBodyFiller(method)
        filler.generateReplacementBlock { generateInit(method) }

        return filler.buildBody()
    }

    private fun BlockGenerationContext.generateInit(method : JcMethod) {

        val callInit = JcSpecialCallExpr(parlessInitRef, thisVal, listOf())
        addInstruction { loc -> JcCallInst(loc, callInit) }

        val rowArg = method.parameters.first().toArgument
        classTable.columnsInOrder().forEachIndexed { ix, col -> generateColAssign(rowArg, ix, col) }

        // TODO: cant see SpringDatabases
        //classTable.relations.filterIsInstance<Relation.RelationByTable>().forEach { generateSetAssign(it) }

        classTable.orderedRelations().forEachIndexed { ix, rel ->
            val arg = method.parameters[ix + 1].toArgument
            when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne -> generateSingleObj(arg, rel as Relation.RelationByColumn)
                is Relation.OneToManyByColumn -> generateMultyObj(arg, rel)
                is Relation.RelationByTable -> generateTableObj(arg, rel)
            }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }

    private fun BlockGenerationContext.generateColAssign(rowArg : JcArgument, ix : Int, col : TableInfo.ColumnInfo) {

        val rowVal = nextLocalVar("${col.name}Row", cp.objectType)
        val access = JcArrayAccess(rowArg, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowVal, access) }

        val colType = col.type.toJcType(cp)!!
        val castVar = nextLocalVar("${col.name}Casted", colType)
        val cast = JcCastExpr(colType, rowVal)
        addInstruction { loc -> JcAssignInst(loc, castVar, cast) }

        val relField = if (col.isOrig) col.origField else JcDataclassTransformer.relatedField(clazz, col.origField)!!
        val fieldRef = JcFieldRef(thisVal, JcTypedFieldImpl(classType, relField, JcSubstitutorImpl()))
        addInstruction { loc -> JcAssignInst(loc, fieldRef, castVar) }
    }

    private fun BlockGenerationContext.generateSingleObj(arg : JcArgument, rel : Relation.RelationByColumn) {

        val fieldName = rel.origField.name

        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambdaVar = generateLambda("${fieldName}Lambda", method)

        val filterVar = generateNewWithInit("${fieldName}Single", filterType, listOf(arg, lambdaVar))

        val fstVal = nextLocalVar("${fieldName}Fst", cp.objectType)
        val fstMethod = VirtualMethodRefImpl.of(
            filterType, filterType.declaredMethods.single { it.name == "firstEnsure"}
        )
        val fstCall = JcVirtualCallExpr(fstMethod, filterVar, listOf())
        addInstruction { loc -> JcAssignInst(loc, fstVal, fstCall) }

        val relField = rel.origField
        val relType = relField.type.toJcType(cp)!!

        val castedFstVal = nextLocalVar("${fstVal.name}Casted", relType)
        val cast = JcCastExpr(relType, fstVal)
        addInstruction { loc -> JcAssignInst(loc, castedFstVal, cast) }

        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, castedFstVal) }
    }

    private fun BlockGenerationContext.generateMultyObj(arg : JcArgument, rel : Relation.OneToManyByColumn) {

        val fieldName = rel.origField.name

        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambdaVar = generateLambda("${fieldName}Lambda", method)

        val filterVar = generateNewWithInit("${fieldName}Filter", filterType, listOf(arg, lambdaVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateTableObj(arg : JcArgument, rel : Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val pred = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedSetFilter }
        val predVar = generateLambda("${fieldName}Pred", pred)

        val filterVar = generateNewWithInit("${fieldName}FilWrapper", filterType, listOf(arg, predVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateSetAssign(rel : Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val btwVar = nextLocalVar("${fieldName}Btw", itableType)
        val btwTable = cp.findType(DATABASES).let { it as JcClassType }.declaredFields
            .single { it.name == rel.joinTable.name }
        val btw = JcFieldRef(null, btwTable)
        addInstruction { loc -> JcAssignInst(loc, btwVar, btw) }

        val pred = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedBtwFilter }
        val predVar = generateLambda("${fieldName}Lambda", pred)

        val filterVar = generateNewWithInit("${fieldName}Filter", filterType, listOf(btwVar, predVar))

        val sel = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedBtwSelect }
        val selVar = generateLambda("${fieldName}Sel", sel)

        val cTyp = cp.findType("java.lang.Class")
        val typeVar = nextLocalVar("${fieldName}MapType", cTyp)
        val type = JcClassConstant(sel.returnType.toJcType(cp)!!, cTyp)
        addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapVar = generateNewWithInit("${fieldName}Map", mapType, listOf(filterVar, selVar, typeVar))

        val setVar = generateNewWithInit("${fieldName}Set", setType, listOf(mapVar))

        val relField = JcDataclassTransformer.relatedField(clazz, rel.origField)!!
        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, setVar) }
    }

    private fun BlockGenerationContext.generateWrapper(rel : Relation, tblVar : JcLocalVar) : JcLocalVar {

        val relType = rel.origField.type
        val fieldName = rel.origField.name

        val type = when (relType.typeName) {
            "java.util.Set" -> setType
            "java.util.List" -> listType
            // TODO: more collections
            else -> {
                assert(false)
                setType
            }
        }

        val vari = generateNewWithInit("${fieldName}Wrapper", type, listOf(tblVar))
        return vari
    }
}
