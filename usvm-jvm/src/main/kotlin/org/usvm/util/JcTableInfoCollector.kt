package org.usvm.util

import bench.DatabaseGenerator
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

    val tablesInfo = HashMap<String, TableInfo>()

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

    fun collectTable(clazz : JcClassOrInterface) : TableInfo {

        val name = getTableName(clazz)
        val columns = collectColumns(clazz)
        val idField = columns
            .find { contains(it.annotations, "Id") }!!
        val idColumn = idField.let { ColumnInfo(getColumnName(it), it.type) }

        val tableColumns = mutableListOf<ColumnInfo>()
        val relations = mutableListOf<RelationType>()

        tablesInfo.getOrPut(name) { TableInfo(name, idColumn, idField, tableColumns, relations, clazz) }
        val parentTable = tablesInfo[name]!!

        columns.forEach { col ->

            val simpleColName = getColumnName(col)

            if (
                !contains(
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
            val childTable = tablesInfo[getTableName(childClass)]!!

            val relation = RelationType.fromField(col)!!
            relations.add(relation)

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

                    if (relation.withTable) {

                        val betweenTableName = relation.tableName!!
                        val parentJk = parentTable.jkColumnInfo()
                        val childJk = childTable.jkColumnInfo()
                        val betweenTable = TableInfo(betweenTableName, listOf(parentJk, childJk))
                        tablesInfo[betweenTableName] = betweenTable
                    }
                    else {
                        val colInfo = ColumnInfo(relation.join!!.name!!, parentTable.idColumn!!.type)
                        childTable.insertColumn(colInfo)
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
                    val betweenTableName = relation.tableName

                    val parentJk =
                        if (rJoinTable?.joinCol == null) parentTable.jkColumnInfo()
                        else BtwColumnInfo(parentTable.idField!!, rJoinTable.joinCol.name!!, parentTable.idColumn!!.type)

                    val childJk =
                        if (rJoinTable?.inverseJoinCol == null) childTable.jkColumnInfo()
                        else BtwColumnInfo(childTable.idField!!, rJoinTable.inverseJoinCol.name!!, childTable.idColumn!!.type)

                    val betweenTable = TableInfo(betweenTableName, listOf(parentJk, childJk))
                    tablesInfo[betweenTableName] = betweenTable
                }
            }
        }

        return tablesInfo[name]!!
    }

    fun findSubTable(rel : RelationType) : TableInfo? {
        val subClass = rel.origField.signature?.genericTypes?.get(0)?.let { cp.findClass(it) }
            ?: rel.origField.type.toJcClassOrInterface(cp)!!
        return tablesInfo.get(getTableName(subClass))
    }
}

class TableInfo(
    val name : String,
    val idColumn : ColumnInfo?,
    val idField : JcField?,
    val columns : List<ColumnInfo>,
    val relations : List<RelationType>,
    val origClass : JcClassOrInterface?
) {

    val hasId: Boolean = idColumn != null

    constructor(
        name : String,
        columns : List<ColumnInfo>
    ) : this(name, null, null, columns, listOf(), null)

    fun jkColumnInfo(): ColumnInfo {
        val name = "${name}_${idColumn!!.name}"
        val type = idColumn.type
        return BtwColumnInfo(idField!!, name, type)
    }

    fun insertColumn(col: ColumnInfo) {
        columns.toMutableList().add(col)
    }

    fun idColIndex() : Int {
        return idColumn?.let { indexOfCol(it) } ?: -1
    }

    fun indexOfCol(col : ColumnInfo) : Int {
        return columns.sortedBy { it.name }.indexOf(col)
    }

    fun indexOfField(field: JcField) : Int {
        return columns.sortedBy { it.name }.indexOfFirst { (it as? BtwColumnInfo)?.origField == field }
    }
}

open class ColumnInfo(
    val name : String,
    val type : TypeName
)

class BtwColumnInfo(
    val origField: JcField,
    name : String,
    type : TypeName
) : ColumnInfo(name, type)

sealed class RelationType(
    val origField: JcField
) {

    abstract val withTable : Boolean

    data class Join(
        val name : String?
    )

    data class JoinTable(
        val name : String?,
        val joinCol : Join?,
        val inverseJoinCol : Join?
    )

    class OneToOne(
        val join : Join?,
        val mappedBy : String?,
        origField : JcField
    ) : RelationType(origField) {
        override val withTable: Boolean = false
    }

    class OneToMany(
        val join : Join?,
        val mappedBy : String?,
        val tableName : String?,
        origField : JcField
    ) : RelationType(origField) {
        override val withTable : Boolean = tableName != null
    }

    class ManyToOne(
        val join : Join?,
        origField : JcField
    ) : RelationType(origField) {
        override val withTable: Boolean = false
    }

    class ManyToMany(
        val joinTable : JoinTable?,
        val tableName: String,
        val mappedBy: String?,
        origField : JcField
    ) : RelationType(origField) {
        override val withTable: Boolean = true
    }

    companion object {

        fun join(annotation : JcAnnotation) : Join {
            return Join(annotation.values["name"] as String?)
        }

        fun fromField(field : JcField) : RelationType? {

            val annotations = field.annotations
            val join = find(annotations, "JoinColumn")
                ?.let { join(it) }

            find(annotations, "OneToOne")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    return OneToOne(join, mappedBy, field)
                }

            find(annotations, "OneToMany")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    val tableName = if (join == null) getBtwTableName(field.enclosingClass, field) else null
                    return OneToMany(join, mappedBy, tableName, field)
                }

            find(annotations, "ManyToOne")
                ?.let {
                    return ManyToOne(join, field)
                }

            find(annotations, "ManyToMany")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    val joinTable = find(annotations, "JoinTable")
                        ?.let {
                            val name = it.values["name"] as String?
                            val joinCol = (it.values["joinColumns"] as List<*>?)
                                ?.first()?.let { join(it as JcAnnotation) } // TODO: many joins columns
                            val inverseJoinCol = (it.values["inverseJoinColumns"] as List<*>?)
                                ?.first()?.let { join(it as JcAnnotation) }
                            JoinTable(name, joinCol, inverseJoinCol)
                        }
                    val tableName = joinTable?.name ?: getBtwTableName(field.enclosingClass, field)
                    return ManyToMany(joinTable, tableName, mappedBy, field)
                }

            return null;
        }
    }
}
