package org.usvm.machine.interpreter.transformers.SpringJPA

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.toJcType
import org.usvm.instrumentation.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.TableInfo
import org.usvm.util.contains
import org.usvm.util.toArgument

val JcMethod.generatedGetId : Boolean get() = contains(annotations, GET_ID_ANNOT)
val JcMethod.generatedIdentity : Boolean get() = contains(annotations, IDENTITY_ANNOT)
val JcMethod.generatedSerializer : Boolean get() = contains(annotations, SERIALIZER_ANNOT)

// Integer $getId() { return id; }
class JcGeneratedGetIdTransformer(
    val classTable : TableInfo.TableWithIdInfo
) : JcMethodExtFeature {

    override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {

        if (!method.generatedGetId) return null

        val filler = JcMethodBodyFiller(method)
        filler.generateReplacementBlock { generateBody() }
        return filler.buildBody()
    }

    private fun BlockGenerationContext.generateBody() {

        val clazz = classTable.origClass
        val cp = clazz.classpath
        val classType = clazz.typename.toJcType(cp)!!
        val idType = classTable.idColumn.type.toJcType(cp)!!

        val lhv = nextLocalVar("%0", idType)
        val rhv = JcFieldRef(JcThis(classType), JcTypedFieldImpl(
            clazz.toType(),
            classTable.idColumn.origField,
            JcSubstitutorImpl()
        ))
        addInstruction { loc -> JcAssignInst(loc, lhv, rhv) }

        addInstruction { loc -> JcReturnInst(loc, lhv) }
    }
}

// T $identity(T v) { return v; }
class JcGeneratedIdentityTransformer(
    val type : JcType
) : JcMethodExtFeature {

    override fun instList(method : JcMethod) : JcMethodExtFeature.JcInstListResult? {

        if (!method.generatedIdentity) return null

        val filler = JcMethodBodyFiller(method)
        filler.generateReplacementBlock { generateBody(method) }
        return filler.buildBody()
    }

    private fun BlockGenerationContext.generateBody(method : JcMethod) {
        val argVal = nextLocalVar("v", type)
        val arg = method.parameters.first().toArgument
        addInstruction { loc -> JcAssignInst(loc, argVal, arg) }
        addInstruction { loc -> JcReturnInst(loc, argVal) }
    }
}

// Object[] $serialize() {
//      val row = new Object[4];
//      row[0] = id;
//      ...
//      return row
// }
class JcGeneratedSerializerTransformer(
    val classTable : TableInfo.TableWithIdInfo
) : JcMethodExtFeature {

    override fun instList(method: JcMethod) : JcMethodExtFeature.JcInstListResult? {

        if (!method.generatedSerializer) return null

        val filler = JcMethodBodyFiller(method)
        filler.generateReplacementBlock { generateBody(method) }
        return filler.buildBody()
    }

    private fun BlockGenerationContext.generateBody(method : JcMethod) {
        val clazz = method.enclosingClass
        val classType = clazz.toType()
        val cp = clazz.classpath
        val columns = classTable.columnsInOrder()

        val arrType = cp.arrayTypeOf(cp.objectType, true, listOf())
        val arr = nextLocalVar("row", arrType)
        val newArr = JcNewArrayExpr(arrType, listOf(JcInt(columns.size, cp.int)))
        addInstruction { loc -> JcAssignInst(loc, arr, newArr) }

        columns.forEachIndexed { ix, col ->
            val fieldVar = nextLocalVar("${col.name}Field", col.type.toJcType(cp)!!)
            val field = if (col.isOrig) col.origField else JcDataclassTransformer.relatedField(clazz, col.origField)!!
            val fieldRef = JcFieldRef(JcThis(classType), JcTypedFieldImpl(
                classType,
                field,
                JcSubstitutorImpl()
            ))
            addInstruction { loc -> JcAssignInst(loc, fieldVar, fieldRef) }

            val arrAcess = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
            addInstruction { loc -> JcAssignInst(loc, arrAcess, fieldVar) }
        }

        addInstruction { loc -> JcReturnInst(loc, arr) }
    }
}
