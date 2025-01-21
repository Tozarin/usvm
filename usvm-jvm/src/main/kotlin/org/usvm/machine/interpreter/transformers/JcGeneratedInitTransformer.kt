package org.usvm.machine.interpreter.transformers

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcParameter
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
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.jacodb.impl.cfg.TypedMethodRefImpl
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.generatedBtwFilter
import org.usvm.machine.interpreter.generatedBtwSelect
import org.usvm.machine.interpreter.generatedSetFilter
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.Relation
import org.usvm.util.TableInfo
import org.usvm.util.contains
import org.usvm.util.typedField

val JcMethod.generatedInit : Boolean get() = contains(this.annotations, INIT_ANNOT)
val JcTypedMethod.methodRef : TypedMethodRefImpl
    get() = TypedMethodRefImpl(
    enclosingType as JcClassType,
    name,
    method.parameters.map { it.type },
    method.returnType
)


private val JcParameter.toArgument : JcArgument
    get() = JcArgument(index, name!!, type.toJcType(method.enclosingClass.classpath)!!)

private const val DATABASES = "SpringDatabases"
private const val ITABLE = "generated.org.springframework.boot.databases.ITable"
private const val SET_WRAPPER = "generated.org.springframework.boot.databases.SetWrapper"
private const val LIST_WRAPPER = "generated.org.springframework.boot.databases.ListWrapper"
private const val MAP_TABLE = "generated.org.springframework.boot.databases.MappedTable"
private const val FILTER_TABLE = "generated.org.springframework.boot.databases.FiltredTable"

private const val PREDICATE = "java.util.function.Predicate"
private const val FUNCTION = "java.util.function.Function"

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
    val predicateType = cp.findType(PREDICATE)
    val functionType = cp.findType(FUNCTION)

    val filterInit = filterType.declaredMethods.single { it.name == "<init>" && it.parameters.size == 2 }
    val mapInit = mapType.declaredMethods.single { it.name == "<init>" && it.parameters.size == 3 }
    val listInit = listType.declaredMethods.single { it.name == "<init>" && it.parameters.size == 1 }
    val setInit = setType.declaredMethods.single { it.name == "<init>" && it.parameters.size == 1 }

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

        val filterVar = generateNew("${fieldName}Single", filterType)
        val lambdaVar = nextLocalVar("${fieldName}Lambda", cp.findType("java.util.function.Predicate"))
        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambda = getLambda(method)
        addInstruction { loc -> JcAssignInst(loc, lambdaVar, lambda) }

        val filterCall = JcSpecialCallExpr(filterInit.methodRef, filterVar, listOf(arg, lambdaVar))
        addInstruction { loc -> JcCallInst(loc, filterCall) }

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

        val lambdaVar = nextLocalVar("${fieldName}Lambda", predicateType)
        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambda = getLambda(method)
        addInstruction { loc -> JcAssignInst(loc, lambdaVar, lambda) }

        val filterVar = generateNew("${fieldName}Filter", filterType)
        val filterCall = JcSpecialCallExpr(filterInit.methodRef, filterVar, listOf(arg, lambdaVar))
        addInstruction { loc -> JcCallInst(loc, filterCall) }

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateTableObj(arg : JcArgument, rel : Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val filterVar = generateNew("${fieldName}FilWrapper", filterType)
        val predVar = nextLocalVar("${fieldName}Pred", predicateType)
        val pred = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedSetFilter }
        addInstruction { loc -> JcAssignInst(loc, predVar, getLambda(pred)) }

        val filterCall = JcSpecialCallExpr(filterInit.methodRef, filterVar, listOf(arg, predVar))
        addInstruction { loc -> JcCallInst(loc, filterCall) }

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateSetAssign(rel : Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val setVar = generateNew("${fieldName}Set", setType)
        val mapVar = generateNew("${fieldName}Map", mapType)
        val filterVar = generateNew("${fieldName}Filter", filterType)

        val btwVar = nextLocalVar("${fieldName}Btw", itableType)
        val btwTable = cp.findType(DATABASES).let { it as JcClassType }.declaredFields
            .single { it.name == rel.joinTable.name }
        val btw = JcFieldRef(null, btwTable)
        addInstruction { loc -> JcAssignInst(loc, btwVar, btw) }

        val predVar = nextLocalVar("${fieldName}Lambda", predicateType)
        val pred = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedBtwFilter }
        addInstruction { loc -> JcAssignInst(loc, predVar, getLambda(pred)) }

        val filterCall = JcSpecialCallExpr(filterInit.methodRef, filterVar, listOf(btwVar, predVar))
        addInstruction { loc -> JcCallInst(loc, filterCall) }

        val selVar = nextLocalVar("${fieldName}Sel", functionType)
        val sel = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedBtwSelect }
        addInstruction { loc -> JcAssignInst(loc, selVar, getLambda(sel)) }

        val cTyp = cp.findType("java.lang.Class")
        val typeVar = nextLocalVar("${fieldName}MapType", cTyp)
        val type = JcClassConstant(sel.returnType.toJcType(cp)!!, cTyp)
        addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapperCall = JcSpecialCallExpr(mapInit.methodRef, mapVar, listOf(filterVar, selVar, typeVar))
        addInstruction { loc -> JcCallInst(loc, mapperCall) }

        val setCall = JcSpecialCallExpr(setInit.methodRef, setVar, listOf(mapVar))
        addInstruction { loc -> JcCallInst(loc, setCall) }

        val relField = JcDataclassTransformer.relatedField(clazz, rel.origField)!!
        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, selVar) }
    }


    private fun BlockGenerationContext.generateNew(name : String, type : JcType) : JcLocalVar {
        val vari = nextLocalVar(name, type)
        val newExpr = JcNewExpr(type)
        addInstruction { loc -> JcAssignInst(loc, vari, newExpr) }
        return vari
    }

    private fun BlockGenerationContext.generateWrapper(rel : Relation, tblVar : JcLocalVar) : JcLocalVar {

        val relType = rel.origField.type
        val fieldName = rel.origField.name

        val (type, method) = when (relType.typeName) {
            "java.util.Set" -> Pair(setType, setInit)
            "java.util.List" -> Pair(listType, listInit)
            // TODO: more collections
            else -> {
                assert(false)
                Pair(setType, setInit)
            }
        }

        val vari = generateNew("${fieldName}Wrapper", type)
        val call = JcSpecialCallExpr(method.methodRef, vari, listOf(tblVar))
        addInstruction { loc -> JcCallInst(loc, call) }
        return vari
    }

    private fun getLambda(method : JcMethod) : JcLambdaExpr {

        val bsm = cp.findType("java.lang.invoke.LambdaMetafactory").let { it as JcClassType }
            .declaredMethods.single { it.name == "metafactory" }
            .methodRef

        val argTypes = method.parameters.map { it.type }
        val actualMethod = TypedMethodRefImpl(classType, method.name, argTypes, method.returnType)

        val interfaceMethodType = BsmMethodTypeArg(argTypes, method.returnType)
        val dynamicMethodType = BsmMethodTypeArg(argTypes.map { cp.objectType.getTypename() }, method.returnType)

        val callSiteName = "test"
        val callSiteArgTypes = listOf(classType as JcType)
        val callSiteRetType = predicateType
        val callSiteArgs = listOf(thisVal)

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
}
