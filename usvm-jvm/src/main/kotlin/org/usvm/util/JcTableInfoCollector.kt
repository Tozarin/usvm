package org.usvm.util

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass
import org.usvm.instrumentation.util.toJcClassOrInterface

class JcTableInfoCollector(
    private val cp : JcClasspath
) {

    private val tablesInfo = HashMap<String, TableInfo.TableWithIdInfo>()
    private val btwTablesInfo = HashMap<String, TableInfo>()

    fun allTables() : List<TableInfo> {
        return tablesInfo.values + btwTablesInfo.values
    }

    fun tables() : List<TableInfo.TableWithIdInfo> {
        return tablesInfo.values.toList()
    }

    fun getBtwTable(name : String) : TableInfo? {
        return btwTablesInfo[name]
    }

    private fun collectFields(clazz : JcClassOrInterface): List<JcField> {

        val supClass = clazz.superClass
        val columns = clazz.fields +
                if (supClass != null
                    && supClass.annotations.any { it.jcClass?.simpleName.equals("MappedSuperclass") })
                    collectFields(supClass)
                else
                    listOf()

        return columns.sortedBy { it.name }
    }

    fun collectTable(clazz : JcClassOrInterface) : TableInfo.TableWithIdInfo {

        val name = getTableName(clazz)
        val fields = collectFields(clazz)
        val idField = fields
            .find { contains(it.annotations, "Id") }!!
        val idColumn = idField.let { TableInfo.ColumnInfo(getColumnName(it), it.type, idField, true) }
        val idType = idField.type

        val tableColumns = mutableListOf<TableInfo.ColumnInfo>()
        val relations = mutableListOf<Relation>()

        tablesInfo.getOrPut(name) { TableInfo.TableWithIdInfo(name, tableColumns, relations, idColumn, clazz) }
        val classTable = tablesInfo[name]!!

        fields.forEach { field ->

            val simpleColName = getColumnName(field)

            if (
                !contains(
                    field.annotations,
                    listOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany"))
            ) {

                val colInfo = TableInfo.ColumnInfo(simpleColName, field.type, field, true)
                tableColumns.add(colInfo)
                return@forEach
            }

            val subClass = field.signature?.genericTypes?.let { cp.findClass(it[0])}
                ?: cp.findClass(field.type.typeName)

            val subTable = collectTable(subClass)
            val subIdType = subTable.idColumn.type

            val rel = Relation.fromField(classTable, subTable, field)!!
            relations.add(rel)

            when(rel) {
                is Relation.OneToOne -> {
                    if (rel.mappedBy != null) return@forEach
                    tableColumns.add(
                        TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false)
                    )
                }
                is Relation.OneToManyByColumn -> {
                    if (rel.mappedBy != null) return@forEach
                    subTable.insertColumn(
                        TableInfo.ColumnInfo(rel.join!!.colName, idType, field, false)
                    )
                }
                is Relation.ManyToOne -> {
                    tableColumns.add(
                        TableInfo.ColumnInfo(rel.join.colName, subIdType, field, false)
                    )
                }
                is Relation.RelationByTable -> {
                    val newTable = rel.joinTable.toTable()
                    btwTablesInfo[newTable.name] = newTable
                }
            }
        }

        return classTable
    }

    fun findSubTable(field : JcField) : TableInfo.TableWithIdInfo? {
        val subClass = field.signature?.genericTypes?.get(0)?.let { cp.findClass(it) }
            ?: field.type.toJcClassOrInterface(cp)!!
        return tablesInfo.get(getTableName(subClass))
    }
}

open class TableInfo(
    val name : String,
    val columns : List<ColumnInfo>,
    val relations : List<Relation>
) {

    data class ColumnInfo(
        val name : String,
        val type : TypeName,
        val origField : JcField,
        val isOrig : Boolean
    )

    class TableWithIdInfo(
        name : String,
        columns : List<ColumnInfo>,
        relations : List<Relation>,
        val idColumn : ColumnInfo,
        val origClass : JcClassOrInterface
    ) : TableInfo(name, columns, relations) {

        fun jkName() : String {
            return "${name}_${idColumn.name}"
        }

        fun jkColumnInfo() : ColumnInfo {
            val name = jkName()
            val type = idColumn.type
            val field = idColumn.origField
            return ColumnInfo(name, type, field, false)
        }

        fun idColIndex() : Int {
            return indexOfCol(idColumn)
        }

        fun columnsInOrder() : List<ColumnInfo> {
            return columns.sortedBy { it.name }
        }
    }

    fun insertColumn(col: ColumnInfo) {
        columns.toMutableList().add(col)
    }

    fun indexOfCol(col : ColumnInfo) : Int {
        return columns.sortedBy { it.name }.indexOf(col)
    }

    fun indexOfField(field: JcField) : Int {
        return columns.sortedBy { it.name }.indexOfFirst { it.origField == field }
    }

    fun orderedRelations() : List<Relation> {
        return relations.sortedBy { it.toString() }
    }
}

sealed class Relation(
    val origField : JcField
) {

    abstract val mappedBy: String?

    override fun toString() : String {
        return "\$r${origField.enclosingClass.name}.${origField.name}"
    }

    data class Join(
        val colName: String
    )

    class JoinTable(
        val name: String,
        val joinCol: TableInfo.ColumnInfo,
        val inverseJoinCol: TableInfo.ColumnInfo
    ) {
        fun toTable() : TableInfo {
            return TableInfo(name, listOf(joinCol, inverseJoinCol), listOf())
        }
    }

    sealed class RelationByTable(
        val joinTable: JoinTable,
        origField: JcField
    ) : Relation(origField)

    sealed class RelationByColumn(
        val join: Join,
        origField: JcField
    ) : Relation(origField)

    class OneToOne(
        override val mappedBy: String?,
        join: Join,
        origField: JcField
    ) : RelationByColumn(join, origField) {
        override fun toString(): String {
            return "\$OneToOne" + super.toString()
        }
    }

    class OneToManyByColumn(
        override val mappedBy: String?,
        val join: Join?,
        origField: JcField
    ) : Relation(origField) {
        override fun toString(): String {
            return "\$OneToManyCol" + super.toString()
        }
    }

    class OneToManyByTable(
        joinTable: JoinTable,
        origField: JcField
    ) : RelationByTable(joinTable, origField) {
        override val mappedBy: String? = null

        override fun toString(): String {
            return "\$OneToManyTable" + super.toString()
        }
    }

    class ManyToOne(
        join: Join,
        origField: JcField
    ) : RelationByColumn(join, origField) {
        override val mappedBy: String? = null

        override fun toString(): String {
            return "\$ManyToOne" + super.toString()
        }
    }

    class ManyToMany(
        override val mappedBy: String?,
        joinTable: JoinTable,
        origField: JcField
    ) : RelationByTable(joinTable, origField) {
        override fun toString(): String {
            return "\$ManyToMany" + super.toString()
        }
    }

    companion object {

        private fun join(annotation: JcAnnotation): Join? {
            return annotation.values["name"].let { it as String? }?.let { Join(it) }
        }

        fun fromField(
            classTable : TableInfo.TableWithIdInfo,
            subTable : TableInfo.TableWithIdInfo,
            field: JcField
        ) : Relation? {

            val simpleName = databaseName(field.name)
            val commonName = "${simpleName}_${subTable.idColumn.name}"
            val annotations = field.annotations
            val join = find(annotations, "JoinColumn")
                ?.let { join(it) }

            find(annotations, "OneToOne")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    val j = join ?: Join(commonName)
                    return OneToOne(mappedBy, j, field)
                }

            find(annotations, "OneToMany")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    if (join == null && mappedBy == null) {
                        val name = getBtwTableName(classTable.origClass, field)
                        val jkClass = classTable.jkColumnInfo()
                        val jkSub = subTable.jkColumnInfo()
                        val table = JoinTable(name, jkClass, jkSub)
                        return OneToManyByTable(table, field)
                    }

                    return OneToManyByColumn(mappedBy, join, field)
                }

            find(annotations, "ManyToOne")
                ?.let {
                    val j = join ?: Join(commonName)
                    return ManyToOne(j, field)
                }

            // TODO: several join columns
            find(annotations, "ManyToMany")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    val joinTableAnnot = find(annotations, "JoinTable")
                    val joinTableName = (joinTableAnnot?.values?.get("name") as String?)
                        ?: getBtwTableName(field.enclosingClass, field)
                    val jkClassName = (joinTableAnnot?.values?.get("JoinColumns") as List<*>?)
                        ?.first()?.let { a -> join(a as JcAnnotation) }
                        ?.colName
                        ?: classTable.jkName()
                    val jkClass = TableInfo.ColumnInfo(
                        jkClassName, classTable.idColumn.type, classTable.idColumn.origField, false
                    )
                    val jkSubName = (joinTableAnnot?.values?.get("inverseJoinColumns") as List<*>?)
                        ?.first()?.let { a -> join(a as JcAnnotation) }
                        ?.colName
                        ?: simpleName
                    val jkSub = TableInfo.ColumnInfo(
                        jkSubName, subTable.idColumn.type, subTable.idColumn.origField, false
                    )
                    val joinTable = JoinTable(joinTableName, jkClass, jkSub)
                    return ManyToMany(mappedBy, joinTable, field)
                }

            return null;
        }
    }
}
