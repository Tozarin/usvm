package org.usvm.machine.interpreter.transformers.SpringJPA

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcConditionExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.TypedStaticMethodRefImpl
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.toJcType
import org.usvm.instrumentation.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.Relation
import org.usvm.util.TableInfo
import org.usvm.util.contains
import org.usvm.util.toArgument

val JcMethod.generatedSubFilter : Boolean get() = contains(this.annotations, FILTER_ANNOT)
val JcMethod.generatedBtwFilter : Boolean get() = contains(this.annotations, FILTER_BTW_ANNOT)
val JcMethod.generatedBtwSelect : Boolean get() = contains(this.annotations, SELECT_BTW_ANNOT)
val JcMethod.generatedSetFilter : Boolean get() = contains(this.annotations, FILTER_SET_ANNOT)

val JcTypedMethod.staticMethodRef : TypedStaticMethodRefImpl get() = TypedStaticMethodRefImpl(
    enclosingType as JcClassType,
    name,
    method.parameters.map { it.type },
    method.returnType
)

const val JAVA_BOOL = "java.lang.Boolean"
const val JAVA_SET = "java.util.Set"
const val JAVA_STRING = "java.lang.String"
const val JAVA_BIG_INT = "java.math.BigInteger"
const val JAVA_BIG_DECIMAL = "java.math.BigDecimal"

class JcDataclassLambdaTransformer (
    val cp : JcClasspath,
    val classTable : TableInfo.TableWithIdInfo,
    val subTable : TableInfo.TableWithIdInfo,
    val btwTable : TableInfo?,
    val rel : Relation
) : JcMethodExtFeature {

    val clazz = classTable.origClass
    val classType = clazz.typename.toJcType(cp)!!
    val thisVal = JcThis(classType)
    val idType = classTable.idColumn.type.toJcType(cp)!!
    val idField = classTable.idColumn.origField

    val subClass = subTable.origClass
    val subIdType = subTable.idColumn.type.toJcType(cp)!!
    val subType = subClass.toType()
    val subGetIdMethod = subType.declaredMethods.single { it.name == "\$getId" && it.parameters.isEmpty() }
    val subGetIdRef = VirtualMethodRefImpl.of(subType, subGetIdMethod)

    val castToBool = (cp.findType(JAVA_BOOL) as JcClassType).declaredMethods.single {
        it.isStatic && it.name == "valueOf" && it.parameters.first().type.typeName == "boolean"
    }

    override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {

        val filler = JcMethodBodyFiller(method)

        var performed = true
        filler.generateReplacementBlock {
            if (method.generatedSubFilter) generateSubFilter(method)
            else if (method.generatedBtwFilter) generateBtwFilter(method)
            else if (method.generatedBtwSelect) generateBtwSelect(method)
            else if (method.generatedSetFilter) generateSetFilter(method)
            else performed = false
        }
        if (!performed) return null

        return  filler.buildBody()
    }

    // Boolean filter(Subcl s) { return s.$getId() == oneToMany_id; }
    private fun BlockGenerationContext.generateSubFilter(method : JcMethod) {

        val idVar = nextLocalVar("subId", subIdType)
        val idCall = JcVirtualCallExpr(subGetIdRef, method.parameters.first().toArgument, listOf())
        addInstruction { loc -> JcAssignInst(loc, idVar, idCall) }

        val relVar = nextLocalVar("relVal", subIdType)
        val relField = JcDataclassTransformer.relatedField(clazz, rel.origField)!!
        val relVal = JcFieldRef(thisVal, JcTypedFieldImpl(clazz.toType(), relField, JcSubstitutorImpl()))
        addInstruction { loc -> JcAssignInst(loc, relVar, relVal) }

        val cond = JcEqExpr(subIdType, idVar, relVar)
        val ifRes = compare(cp, cond, "res")
        val res = toBoolean(cp, ifRes)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }

    // Boolean betweenFilter(Object[] row) { return row[0] == id; }
    private fun BlockGenerationContext.generateBtwFilter(method : JcMethod) {

        val rowId = nextLocalVar("idRow", cp.objectType)
        val ix = btwTable!!.indexOfField(idField)
        val access = JcArrayAccess(method.parameters.first().toArgument, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowId, access) }

        val idVar = nextLocalVar("id", idType)
        val idVal = JcFieldRef(thisVal, JcTypedFieldImpl(clazz.toType(), idField, JcSubstitutorImpl()))
        addInstruction { loc -> JcAssignInst(loc, idVar, idVal) }

        val cond = JcEqExpr(idType, rowId, idVar)
        val ifRes = compare(cp, cond, "res")
        val res = toBoolean(cp, ifRes)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }

    // Integer betweenSelector(Object[] row) { return (Integer) row[1]; }
    private fun BlockGenerationContext.generateBtwSelect(method : JcMethod) {

        val rowSel = nextLocalVar("rowSel", cp.objectType)
        val ix = btwTable!!.indexOfField(subTable.idColumn.origField)
        val access = JcArrayAccess(method.parameters.first().toArgument, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowSel, access) }

        val castVar = nextLocalVar("casted", subIdType)
        val cast = JcCastExpr(subIdType, rowSel)
        addInstruction { loc -> JcAssignInst(loc, castVar, cast) }

        addInstruction { loc -> JcReturnInst(loc, castVar) }
    }

    // Boolean setFilter(Subcl s) { return mtmSet.contains(s.$getId()); }
    private fun BlockGenerationContext.generateSetFilter(method : JcMethod) {

        val setField = JcDataclassTransformer.relatedField(clazz, rel.origField)!!
        val setVal = nextLocalVar("setVal", setField.type.toJcType(cp)!!)
        val set = JcFieldRef(thisVal, JcTypedFieldImpl(clazz.toType(), setField, JcSubstitutorImpl()))
        addInstruction { loc -> JcAssignInst(loc, setVal, set) }

        val argIdVal = nextLocalVar("argId", subIdType)
        val idCall = JcVirtualCallExpr(subGetIdRef, method.parameters.first().toArgument, listOf())
        addInstruction { loc -> JcAssignInst(loc, argIdVal, idCall) }

        val conVal = nextLocalVar("contains", cp.boolean)
        val setType = cp.findType(JAVA_SET) as JcClassType
        val contains = setType.declaredMethods.single {
            !it.isStatic && it.name == "contains" && it.parameters.size == 1
        }.let { VirtualMethodRefImpl.of(setType, it) }
        val conCall = JcVirtualCallExpr(contains , setVal, listOf(argIdVal))
        addInstruction { loc -> JcAssignInst(loc, conVal, conCall) }

        val res = nextLocalVar("res", cp.boolean)
        val cast = JcStaticCallExpr(castToBool.staticMethodRef, listOf(conVal))

        addInstruction { loc -> JcAssignInst(loc, res, cast) }
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}

fun BlockGenerationContext.compare(cp : JcClasspath, cond : JcConditionExpr, name : String) : JcLocalVar {

    val endOfIf : JcInstRef
    addInstruction { loc ->
        val nextInst = JcInstRef(loc.index + 1)
        val elseBranch = JcInstRef(loc.index + 3)
        endOfIf = JcInstRef(loc.index + 5)
        JcIfInst(loc, cond, nextInst, elseBranch)
    }

    val ifResVal = nextLocalVar("if$name", cp.boolean)
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(true, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(false, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }

    return ifResVal
}

fun BlockGenerationContext.toBoolean(cp : JcClasspath, value : JcLocalVar) : JcLocalVar {
    val boolType = cp.findType(JAVA_BOOL) as JcClassType
    val castToBool = boolType.declaredMethods.single {
        it.isStatic && it.name == "valueOf" && it.parameters.first().type.typeName == "boolean"
    }
    val res = nextLocalVar("toBool${value.name}", cp.findType(JAVA_BOOL))
    val cast = JcStaticCallExpr(castToBool.staticMethodRef, listOf(value))
    addInstruction { loc -> JcAssignInst(loc, res, cast) }
    return res
}
