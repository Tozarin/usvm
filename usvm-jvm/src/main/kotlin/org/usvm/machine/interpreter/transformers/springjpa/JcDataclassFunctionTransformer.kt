package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.instrumentation.util.toJcType
import org.usvm.instrumentation.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.Relation
import org.usvm.util.TableInfo
import org.usvm.util.typedField


abstract class JcDataclassFunctionTransformer(
    val cp: JcClasspath
) : JcBodyFillerFeature() {
    val itableType = cp.findType(ITABLE)
    val setType = cp.findType(SET_WRAPPER) as JcClassType
    val listType = cp.findType(LIST_WRAPPER) as JcClassType
    val mapType = cp.findType(MAP_TABLE) as JcClassType
    val filterType = cp.findType(FILTER_TABLE) as JcClassType
    val cTyp = cp.findType("java.lang.Class")
}

class JcInitTransformer(
    cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    origInit: JcMethod
) : JcDataclassFunctionTransformer(cp) {

    val clazz = classTable.origClass
    val classType = clazz.toType()
    val thisVal = JcThis(classType)
    val parlessInitRef = VirtualMethodRefImpl.of(classType, JcTypedMethodImpl(classType, origInit, JcSubstitutorImpl()))

    override fun condition(method: JcMethod): Boolean {
        return method.generatedInit
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val callInit = JcSpecialCallExpr(parlessInitRef, thisVal, listOf())
        addInstruction { loc -> JcCallInst(loc, callInit) }

        val rowArg = method.parameters.first().toArgument
        classTable.columnsInOrder().forEachIndexed { ix, col -> generateColAssign(rowArg, ix, col) }

        classTable.relations.filterIsInstance<Relation.RelationByTable>().forEach { generateSetAssign(it) }

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

    private fun BlockGenerationContext.generateColAssign(rowArg: JcArgument, ix: Int, col: TableInfo.ColumnInfo) {

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

    private fun BlockGenerationContext.generateSingleObj(arg: JcArgument, rel: Relation.RelationByColumn) {

        val fieldName = rel.origField.name

        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambdaVar = generateLambda("${fieldName}Lambda", method)

        val filterVar = generateNewWithInit("${fieldName}Single", filterType, listOf(arg, lambdaVar))

        val fstVal = nextLocalVar("${fieldName}Fst", cp.objectType)
        val fstMethod = VirtualMethodRefImpl.of(
            filterType, filterType.declaredMethods.single { it.name == "firstEnsure" }
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

    private fun BlockGenerationContext.generateMultyObj(arg: JcArgument, rel: Relation.OneToManyByColumn) {

        val fieldName = rel.origField.name

        val method = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.first()
        val lambdaVar = generateLambda("${fieldName}Lambda", method)

        val filterVar = generateNewWithInit("${fieldName}Filter", filterType, listOf(arg, lambdaVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateTableObj(arg: JcArgument, rel: Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val pred = JcDataclassTransformer.relatedLambda(clazz, rel.origField)!!.single { it.generatedSetFilter }
        val predVar = generateLambda("${fieldName}Pred", pred)

        val filterVar = generateNewWithInit("${fieldName}FilWrapper", filterType, listOf(arg, predVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    private fun BlockGenerationContext.generateSetAssign(rel: Relation.RelationByTable) {

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

        val typeVar = nextLocalVar("${fieldName}MapType", cTyp)
        val type = JcClassConstant(sel.returnType.toJcType(cp)!!, cTyp)
        addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapVar = generateNewWithInit("${fieldName}Map", mapType, listOf(filterVar, selVar, typeVar))

        val setVar = generateNewWithInit("${fieldName}Set", setType, listOf(mapVar))

        val relField = JcDataclassTransformer.relatedField(clazz, rel.origField)!!
        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, setVar) }
    }

    private fun BlockGenerationContext.generateWrapper(rel: Relation, tblVar: JcLocalVar): JcLocalVar {
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

        return generateNewWithInit("${fieldName}Wrapper", type, listOf(tblVar))
    }
}

// new(Object[] row) {
// ITable<SomeClass> tbl1 = new MappedTable(Databases.some_class, SomeClass::new);
// ...
// return new(row, tbl1, tbl2, ...  )
// }
class JcFetchedInitTransformer(
    cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    genInit: JcMethod
) : JcDataclassFunctionTransformer(cp) {

    val clazz = classTable.origClass
    val classType = clazz.toType()
    val thisVal = JcThis(classType)
    val databases = cp.findType(DATABASES) as JcClassType
    val initRef = VirtualMethodRefImpl.of(classType, JcTypedMethodImpl(classType, genInit, JcSubstitutorImpl()))

    override fun condition(method: JcMethod): Boolean {
        return method.generatedFetchInit
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val fetches = classTable.orderedRelations().mapIndexed { ix, rel ->
            val relTblName = rel.toTableName(cp)
            val tblField = databases.fields.single { it.name == relTblName }
            val tblV = nextLocalVar("fetchTbl$ix", cp.findType(ITABLE))
            val tblRef = JcFieldRef(null, tblField)
            addInstruction { loc -> JcAssignInst(loc, tblV, tblRef) }

            val fetchInit = rel.relatedDataclass(cp).declaredMethods.single {
                it.name == "<init>" && it.generatedFetchInit
            }

            val typeVar = nextLocalVar("fetchType$ix", cTyp)
            val type = JcClassConstant(rel.relatedDataclassType(cp), cTyp)
            addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

            val const = generateLambda("initMapper$ix", fetchInit)
            generateNewWithInit("mappedFecth$ix", mapType, listOf(tblV, const, typeVar))
        }

        val args = listOf(method.parameters.single().toArgument) + fetches
        val call = JcSpecialCallExpr(initRef, thisVal, args)
        addInstruction { loc -> JcCallInst(loc, call) }
    }

}

// Integer $getId() { return id; }
class JcGetIdTransformer(
    val classTable: TableInfo.TableWithIdInfo
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedGetId
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val clazz = classTable.origClass
        val cp = clazz.classpath
        val classType = clazz.typename.toJcType(cp)!!
        val idType = classTable.idColumn.type.toJcType(cp)!!

        val lhv = nextLocalVar("%0", idType)
        val rhv = JcFieldRef(
            JcThis(classType), JcTypedFieldImpl(
                clazz.toType(),
                classTable.idColumn.origField,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, lhv, rhv) }

        addInstruction { loc -> JcReturnInst(loc, lhv) }
    }
}

// fieldType $getField() { return field; }
class JcGetterTransformer(
    val clazz: JcClassOrInterface,
    val field: JcField,
    val name: String
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return name == method.name && method.generatedGetter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val cp = clazz.classpath
        val classType = clazz.typename.toJcType(cp)!!
        val type = field.type.toJcType(cp)!!

        val lhv = nextLocalVar("%0", type)
        val rhv = JcFieldRef(
            JcThis(classType), JcTypedFieldImpl(
                clazz.toType(),
                field,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, lhv, rhv) }

        addInstruction { loc -> JcReturnInst(loc, lhv) }
    }

}

// someType $identity(someType v) { return v; }
class JcIdentityTransformer(val type: JcType) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedIdentity
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
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
class JcSerializerTransformer(val classTable: TableInfo.TableWithIdInfo) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedSerializer
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
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
            val fieldRef = JcFieldRef(
                JcThis(classType), JcTypedFieldImpl(
                    classType,
                    field,
                    JcSubstitutorImpl()
                )
            )
            addInstruction { loc -> JcAssignInst(loc, fieldVar, fieldRef) }

            val arrAcess = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
            addInstruction { loc -> JcAssignInst(loc, arrAcess, fieldVar) }
        }

        addInstruction { loc -> JcReturnInst(loc, arr) }
    }
}
