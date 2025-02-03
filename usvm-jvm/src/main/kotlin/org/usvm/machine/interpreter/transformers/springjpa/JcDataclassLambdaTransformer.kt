package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.toJcType
import org.usvm.instrumentation.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.Relation
import org.usvm.util.TableInfo

abstract class JcDataclassLambdaTransformer(
    val cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    val subTable: TableInfo.TableWithIdInfo,
    val btwTable: TableInfo?,
    val rel: Relation
) : JcBodyFillerFeature() {
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
}

// Boolean filter(Subcl s) { return s.$getId() == oneToMany_id; }
class JcSubFilterTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    btwTable: TableInfo?,
    rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable, btwTable, rel) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedSubFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
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
}

// Boolean betweenFilter(Object[] row) { return row[0] == id; }
class JcBtwFilterTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    btwTable: TableInfo?,
    rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable, btwTable, rel) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedBtwFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

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
}


// Integer betweenSelector(Object[] row) { return (Integer) row[1]; }
class JcBtwSelectTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    btwTable: TableInfo?,
    rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable, btwTable, rel) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedBtwSelect
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val rowSel = nextLocalVar("rowSel", cp.objectType)
        val ix = btwTable!!.indexOfField(subTable.idColumn.origField)
        val access = JcArrayAccess(method.parameters.first().toArgument, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowSel, access) }

        val castVar = nextLocalVar("casted", subIdType)
        val cast = JcCastExpr(subIdType, rowSel)
        addInstruction { loc -> JcAssignInst(loc, castVar, cast) }

        addInstruction { loc -> JcReturnInst(loc, castVar) }
    }
}


// Boolean setFilter(Subcl s) { return mtmSet.contains(s.$getId()); }
class JcSetFilterTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    btwTable: TableInfo?,
    rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable, btwTable, rel) {

    val castToBool = (cp.findType(JAVA_BOOL) as JcClassType).declaredMethods.single {
        it.isStatic && it.name == "valueOf" && it.parameters.first().type.typeName == "boolean"
    }

    override fun condition(method: JcMethod): Boolean {
        return method.generatedSetFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

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
        val conCall = JcVirtualCallExpr(contains, setVal, listOf(argIdVal))
        addInstruction { loc -> JcAssignInst(loc, conVal, conCall) }

        val res = nextLocalVar("res", cp.boolean)
        val cast = JcStaticCallExpr(castToBool.staticMethodRef, listOf(conVal))

        addInstruction { loc -> JcAssignInst(loc, res, cast) }
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
