package bench

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcMutableInstList
import org.jacodb.api.jvm.cfg.JcRawArrayAccess
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawNewArrayExpr
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.cfg.JcRawInt
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldNode
import java.nio.file.Path

private val JcClassOrInterface.jvmDescriptor : String get() = "L${name.replace('.','/')};"
private val String.fromJvmDescriptor : String get() = this.drop(1).replace("/", ".")
private val String.genericTypes : List<String> get() = this
    .substringAfter("<")
    .substringBefore(">")
    .split(";")
    .map { it.fromJvmDescriptor }

object AnnotationUtils {

    fun nameEquals(annotation: JcAnnotation, name : String) : Boolean {
        return annotation.jcClass?.simpleName.equals(name)
    }

    fun contains(annotations : List<JcAnnotation>, name : String) : Boolean {
        return annotations.any { nameEquals(it, name) }
    }

    fun contains(annotation: List<JcAnnotation>, names : List<String>) : Boolean {
        return names.any { contains(annotation, it) }
    }

    fun find(annotations: List<JcAnnotation>, name : String) : JcAnnotation? {
        return annotations.find { nameEquals(it, name) }
    }
}

object DatabaseNamingUtils {

    fun databaseName(name: String): String {

        val newName = name
            .replace(".", "_")
            .replace(Regex("[a-z][A-Z]"))
            { mr -> mr.value[0] + "_" + mr.value[1] }
            .lowercase()

        return newName
    }

    fun getTableName(clazz: JcClassOrInterface): String {
        val name = clazz.annotations
            .find { AnnotationUtils.nameEquals(it, "Table") }
            ?.values?.get("name")
            ?: databaseName(clazz.simpleName)

        return name as String
    }

    fun getColumnName(field: JcField): String {
        val name = field.annotations
            .find { AnnotationUtils.nameEquals(it, "Column") }
            ?.values?.get("name")
            ?: databaseName(field.name)

        return name as String
    }
}

class LocalVarsManager {

    private var lastIndex : Int

    constructor(startIndex: Int) {
        lastIndex = if (startIndex < 0) 0 else startIndex
    }

    private fun newName() : String {
        return "%${lastIndex}"
    }

    fun newLocalVar(type : TypeName) : JcRawLocalVar {
        val v = JcRawLocalVar(lastIndex, newName(), type)
        lastIndex++

        return v
    }
}

class DatabaseGenerator(
    private val cp : JcClasspath,
    private val dir: Path,
    private val repositories :  List<JcClassOrInterface>
) {

    private val databasesClass = cp.findClass("generated.org.springframework.boot.SpringDatabases")
    private val tableClass = cp.findClass("generated.org.springframework.boot.ASpringJPATable")
    private val clinitMethod = databasesClass.declaredMethods.find { it.name == "<clinit>" }!!
    private val rawInstList = clinitMethod.rawInstList.toMutableList()
    private val localVars = LocalVarsManager(0)

    val tableAssign = rawInstList[2] as JcRawAssignInst // %0 = new generated.org.springframework.boot.SpringJPATable
    val classTypesAssign = rawInstList[3] as JcRawAssignInst // %1 = new kotlin.Unit
    val argAssign = rawInstList[4] as JcRawAssignInst // %2 = java.lang.String.class
    val updateClassTypes = rawInstList[5] as JcRawAssignInst // %1[0] = %2
    val callExpr = rawInstList[8] as JcRawCallInst // %0.<init>(1, %1)
    val updateRef = rawInstList[9] as JcRawAssignInst // generated.org.springframework.boot.SpringDatabases._blanck = %0
    val ret = rawInstList[10]

    private val tablesInfo = HashMap<String, TableInfo>()

    private fun collectColumns(clazz : JcClassOrInterface): List<JcField> {

        val supClass = clazz.superClass
        val columns = clazz.fields +
                if (supClass != null
                    && supClass.annotations.any { it.jcClass?.simpleName.equals("MappedSuperclass") })
                    collectColumns(supClass)
                else
                    listOf()

        return columns.sortedBy { it.name }
    }

    private fun collectTable(clazz : JcClassOrInterface) {

        val name = DatabaseNamingUtils.getTableName(clazz)
        val columns = collectColumns(clazz)
        val idColumn = columns
            .find { AnnotationUtils.contains(it.annotations, "Id") }!!
            .let { ColumnInfo(DatabaseNamingUtils.getColumnName(it), it.type) }

        val tableColumns = mutableListOf<ColumnInfo>()

        tablesInfo.getOrPut(name) { TableInfo(name, idColumn, tableColumns) }
        val parentTable = tablesInfo[name]!!

        columns.forEach { col ->

            val simpleColName = DatabaseNamingUtils.getColumnName(col)

            if (
                !AnnotationUtils.contains(
                    col.annotations,
                    listOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany"))
            ) {

                val colInfo = ColumnInfo(simpleColName, col.type)
                tableColumns.add(colInfo)
                return@forEach
            }

            val childClass = col.signature?.genericTypes?.let { cp.findClass(it[0])}
                ?: cp.findClass(col.type.typeName)

            collectTable(childClass)
            val childTable = tablesInfo[DatabaseNamingUtils.getTableName(childClass)]!!

            val relation = RelationType.fromField(col)

            when(relation) {
                is RelationType.OneToOne -> {

                    if (relation.mappedBy != null) return@forEach

                    val jkType = childTable.idColumn!!.type
                    val jkName = relation.join?.name
                        ?: "${simpleColName}_${childTable.idColumn.name}"

                    tableColumns.add(ColumnInfo(jkName, jkType))
                }
                is RelationType.OneToMany -> {

                    if (relation.mappedBy != null) return@forEach

                    if (relation.join == null) {

                        val betweenTableName = "${parentTable.name}_${childTable.name}"
                        val parentJk = parentTable.jkColumnInfo()
                        val childJk = childTable.jkColumnInfo()
                        val betweenTable = TableInfo(betweenTableName, null, listOf(parentJk, childJk))
                        tablesInfo[betweenTableName] = betweenTable
                    }
                    else {
                        val colInfo = ColumnInfo(relation.join.name!!, parentTable.idColumn!!.type)
                        childTable.insetColumn(colInfo)
                    }

                }
                is RelationType.ManyToOne -> {

                    val colName =
                        if (relation.join == null) "${simpleColName}_${childTable.idColumn!!.name}"
                        else relation.join.name!!

                    val colInfo = ColumnInfo(colName, childTable.idColumn!!.type)
                    tableColumns.add(colInfo)
                }
                is RelationType.ManyToMany -> {

                    if (relation.mappedBy != null) return@forEach

                    val rJoinTable = relation.joinTable

                    val betweenTableName =
                        if (rJoinTable == null) "${name}_${parentTable.idColumn!!.name}"
                        else rJoinTable.name!!

                    val parentJk =
                        if (rJoinTable?.joinCol == null) parentTable.jkColumnInfo()
                        else  ColumnInfo(rJoinTable.joinCol.name!!, parentTable.idColumn!!.type)

                    val childJk =
                        if (rJoinTable?.inverseJoinCol == null) childTable.jkColumnInfo()
                        else ColumnInfo(rJoinTable.inverseJoinCol.name!!, childTable.idColumn!!.type)

                    val betweenTable = TableInfo(betweenTableName, null, listOf(parentJk, childJk))
                    tablesInfo[betweenTableName] = betweenTable
                }
                else -> assert(false)
            }
        }
    }

    fun generateJPADatabase() {

        repositories.forEach { repo ->
            val genericTypes = repo.signature!!.genericTypes
            val dataClass = cp.findClass(genericTypes[0])

            collectTable(dataClass)
        }

        databasesClass.withAsmNode { classNode ->

            classNode.fields.removeIf { it.name.equals("_blanck") }
            rawInstList.removeAll(listOf(2..9).flatten().map { rawInstList[it] })

            tablesInfo.values.forEach { it.generate(this, localVars, classNode, rawInstList) }

            clinitMethod.withAsmNode { clinitAsmNode ->
                val newNode = MethodNodeBuilder(clinitMethod, rawInstList).build()
                val asmMethods = classNode.methods
                val asmMethod = asmMethods.find { clinitAsmNode.isSameSignature(it) }!!
                check(asmMethods.replace(asmMethod, newNode))
            }

            classNode.write(cp, dir.resolve("SpringDatabases.class"), checkClass = true)
        }
    }

    sealed class RelationType {

        data class Join(
            val name : String?
        )

        data class JoinTable(
            val name : String?,
            val joinCol : Join?,
            val inverseJoinCol : Join?
        )

        data class OneToOne(
            val join : Join?,
            val mappedBy : String?
        ) : RelationType()

        data class OneToMany(
            val join : Join?,
            val mappedBy : String?
        ) : RelationType()

        data class ManyToOne(
            val join : Join?
        ) : RelationType()

        data class ManyToMany(
            val joinTable : JoinTable?,
            val mappedBy: String?
        ) : RelationType()

        companion object {

            fun join(annotation : JcAnnotation) : Join {
                return Join(annotation.values["name"] as String?)
            }

            fun fromField(field : JcField) : RelationType? {

                val annotations = field.annotations
                val join = AnnotationUtils.find(annotations, "JoinColumn")
                    ?.let { join(it) }

                AnnotationUtils.find(annotations, "OneToOne")
                    ?.let {
                        val mappedBy = it.values["mappedBy"] as String?
                        return OneToOne(join, mappedBy)
                    }

                AnnotationUtils.find(annotations, "OneToMany")
                    ?.let {
                        val mappedBy = it.values["mappedBy"] as String?
                        return OneToMany(join, mappedBy)
                    }

                AnnotationUtils.find(annotations, "ManyToOne")
                    ?.let {
                        return ManyToOne(join)
                    }

                AnnotationUtils.find(annotations, "ManyToMany")
                    ?.let {
                        val mappedBy = it.values["mappedBy"] as String?
                        val joinTable = AnnotationUtils.find(annotations, "JoinTable")
                            ?.let {
                                val name = it.values["name"] as String?
                                val joinCol = (it.values["joinColumns"] as List<*>?)
                                    ?.first()?.let { join(it as JcAnnotation) } // TODO: many joins columns
                                val inverseJoinCol = (it.values["inverseJoinColumns"] as List<*>?)
                                    ?.first()?.let { join(it as JcAnnotation) }
                                return  ManyToMany(JoinTable(name, joinCol, inverseJoinCol), mappedBy)
                            }
                        return ManyToMany(joinTable, mappedBy)
                    }

                return null;
            }
        }
    }

    class TableInfo(
        val name : String,
        val idColumn : ColumnInfo?,
        val columns : List<ColumnInfo>
    ) {

        fun jkColumnInfo() : ColumnInfo {
            val name = "${name}_${idColumn!!.name}"
            val type = idColumn.type
            return ColumnInfo(name, type)
        }

        fun insetColumn(col : ColumnInfo) {
            columns.toMutableList().add(col)
        }

        fun generate(
            generator : DatabaseGenerator,
            localVars: LocalVarsManager,
            classNode: ClassNode,
            rawInstList: JcMutableInstList<JcRawInst>
        ) {
            idColumn?.let { columns.toMutableList().add(it) }
            val allColumns = columns.sortedBy { it.name }

            val tableAssign = generator.tableAssign // %0 = new generated.org.springframework.boot.SpringJPATable
            val classTypesAssign = generator.classTypesAssign // %1 = new kotlin.Unit
            val argAssign = generator.argAssign // %2 = java.lang.String.class
            val updateClassTypes = generator.updateClassTypes // %1[0] = %2
            val callExpr = generator.callExpr // %0.<init>(1, %1)
            val updateRef = generator.updateRef // generated.org.springframework.boot.SpringDatabases._blanck = %0
            val ret = generator.ret

            val taLhv = localVars.newLocalVar(tableAssign.lhv.typeName)
            val newTableAssign = JcRawAssignInst(tableAssign.owner, taLhv, tableAssign.rhv) as JcRawInst

            val ctaLhv = localVars.newLocalVar(classTypesAssign.lhv.typeName)
            val ctaRhv = JcRawNewArrayExpr(classTypesAssign.rhv.typeName, listOf(JcRawInt(allColumns.count() + 1)))
            val newClassTypesAssign = JcRawAssignInst(classTypesAssign.owner, ctaLhv, ctaRhv) as JcRawInst

            val fieldName = name
            val tableField = FieldNode(
                Opcodes.ACC_STATIC,
                fieldName,
                generator.tableClass.jvmDescriptor,
                null,
                null
            )
            classNode.fields.add(tableField)

            fun generateArgAssign(
                colType: TypeName,
                index: Int,
                argArray:
                JcRawLocalVar
            ): List<JcRawInst> {
                val aaLhv = localVars.newLocalVar(argAssign.lhv.typeName)
                val aaRhv = JcRawClassConstant(colType, TypeNameImpl("java.lang.Class"))
                val newArgAssign = JcRawAssignInst(argAssign.owner, aaLhv, aaRhv) as JcRawInst

                val uctLhv = JcRawArrayAccess(argArray, JcRawInt(index), TypeNameImpl("java.lang.Class"))
                val newUpdateClassTypes = JcRawAssignInst(updateClassTypes.owner, uctLhv, aaLhv) as JcRawInst

                return listOf(newArgAssign, newUpdateClassTypes)
            }

            val argAssigns =
                allColumns.mapIndexed { index, col -> generateArgAssign(col.type, index + 1, ctaLhv) }.flatten()

            val oldCallExpr = callExpr.callExpr as JcRawSpecialCallExpr
            val cExpr = JcRawSpecialCallExpr(
                oldCallExpr.declaringClass,
                oldCallExpr.methodName,
                oldCallExpr.argumentTypes,
                oldCallExpr.returnType,
                taLhv,
                listOf(JcRawInt(0), ctaLhv)
            )
            val newCallExpr = JcRawCallInst(callExpr.owner, cExpr) as JcRawInst

            val oldFieldRef = updateRef.lhv as JcRawFieldRef
            val fieldRef =
                JcRawFieldRef(oldFieldRef.instance, oldFieldRef.declaringClass, fieldName, oldFieldRef.typeName)
            val newUpdateRef = JcRawAssignInst(updateRef.owner, fieldRef, taLhv) as JcRawInst

            val newInst =
                listOf(newTableAssign, newClassTypesAssign) + argAssigns + listOf(
                    newCallExpr,
                    newUpdateRef
                )

            rawInstList.insertBefore(ret, newInst)
        }
    }

    data class ColumnInfo(
        val name : String,
        val type: TypeName
    )
}
