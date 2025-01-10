package org.usvm.util

import bench.DatabaseGenerator
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass

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
        val idColumn = columns
            .find { contains(it.annotations, "Id") }!!
            .let { ColumnInfo(getColumnName(it), it.type) }

        val tableColumns = mutableListOf<ColumnInfo>()

        tablesInfo.getOrPut(name) { TableInfo(name, idColumn, tableColumns) }
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

        return tablesInfo[name]!!
    }

}

class TableInfo(
    val name : String,
    val idColumn : ColumnInfo?,
    val columns : List<ColumnInfo>
) {

    val hasId: Boolean = idColumn != null

    fun jkColumnInfo(): ColumnInfo {
        val name = "${name}_${idColumn!!.name}"
        val type = idColumn.type
        return ColumnInfo(name, type)
    }

    fun insetColumn(col: ColumnInfo) {
        columns.toMutableList().add(col)
    }
}

data class ColumnInfo(
    val name : String,
    val type: TypeName
)

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
            val join = find(annotations, "JoinColumn")
                ?.let { join(it) }

            find(annotations, "OneToOne")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    return OneToOne(join, mappedBy)
                }

            find(annotations, "OneToMany")
                ?.let {
                    val mappedBy = it.values["mappedBy"] as String?
                    return OneToMany(join, mappedBy)
                }

            find(annotations, "ManyToOne")
                ?.let {
                    return ManyToOne(join)
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
                            return  ManyToMany(JoinTable(name, joinCol, inverseJoinCol), mappedBy)
                        }
                    return ManyToMany(joinTable, mappedBy)
                }

            return null;
        }
    }
}
