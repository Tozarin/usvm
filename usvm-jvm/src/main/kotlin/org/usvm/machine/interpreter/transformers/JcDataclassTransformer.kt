package org.usvm.machine.interpreter.transformers

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.methods
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.cfg.util.internalDesc
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.*
import org.usvm.machine.interpreter.JcBtwFilterTransformer
import org.usvm.machine.interpreter.JcBtwSelectorTransformer
import org.usvm.machine.interpreter.JcFilterSubclassTransformer
import org.usvm.machine.interpreter.JcSetFilterTransformer
import org.usvm.util.JcTableInfoCollector
import org.usvm.util.RelationType
import org.usvm.util.TableInfo
import org.usvm.util.blancAnnotation
import org.usvm.util.contains
import org.usvm.util.jvmDescriptor

val JcClassOrInterface.isDataClass: Boolean get() = contains(annotations, "Entity")

object JcDataclassTransformer : JcClassExtFeature {

    // prevents cycles
    private val askedClasses : MutableSet<String> = mutableSetOf()

    override fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? {

        if (!clazz.isDataClass || askedClasses.contains(clazz.name)) return null

        askedClasses.add(clazz.name)

        val fields = originalFields.toPersistentList()
        val cp = clazz.classpath
        val collector = JcTableInfoCollector(cp)
        val classTable = collector.collectTable(clazz)

        classTable.relations.forEach { rel ->

            val subTable = collector.findSubTable(rel)!!
            val subIdType = subTable.idColumn!!.type.typeName

            if (rel.withTable) { fields.add(getSetField(clazz, rel, subIdType)) }
            else { fields.add(getIdField(clazz, rel, subIdType)) }
        }

        return fields
    }

    private fun getSetField(clazz : JcClassOrInterface, rel : RelationType, subIdType : String) : JcField {

        val fieldInfo = FieldInfo(
            "\$${rel.origField.name}IdSet",
            "Ljava/util/Set<${subIdType.jvmName()}>;",
            2,
            "Ljava.util.Set;",
            listOf()
        )
        val field = JcFieldImpl(clazz, fieldInfo)

        return field
    }

    private fun getIdField(clazz : JcClassOrInterface, rel : RelationType, subIdType: String) : JcField {

        val fieldInfo = FieldInfo(
            "\$${rel.origField.name}IdCheck",
            null,
            2,
            "L${subIdType};",
            listOf()
        )
        val field = JcFieldImpl(clazz, fieldInfo)

        return field
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isDataClass) return null

        val cp = clazz.classpath
        val collector = JcTableInfoCollector(cp)
        val classTable = collector.collectTable(clazz)

        val blancInit = getConstructor(cp, classTable, collector)
        val getId = getId(cp, classTable)
        val lambdas = getLambdas(cp, classTable, collector)

        return originalMethods + blancInit + getId + lambdas
    }

    private fun getConstructor(cp : JcClasspath, classTable : TableInfo, collector: JcTableInfoCollector) : JcMethod {

        val newFeatures = cp.features!!.toPersistentList()
            .add(JcGeneratedInitTransformer(classTable, collector))
        val relTables = collector.tablesInfo.values.filter { !it.name.equals(classTable.name) && it.hasId }

        val itableDesc = cp.findClass("generated.org.springframework.boot.databases.ITable").jvmDescriptor
        val desc = "([Ljava/lang/Object;${relTables.joinToString(separator = "") { itableDesc }})V"

        val signature = "([Ljava/lang/Object;${relTables.joinToString(separator = "") { info -> 
            "${itableDesc}<${info.origClass!!.jvmDescriptor}>"
        }};)V"

        val parametrs = mutableListOf(
            ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf())
        )

        relTables.forEachIndexed { ix, info ->
            val newParam = ParameterInfo(
                "generated.org.springframework.boot.databases.ITable",
                ix + 1,
                1,
                "${info.name}Condition",
                listOf()
            )

            parametrs.add(newParam)
        }

        val newMethodInfo = MethodInfo(
            "<init>",
            desc,
            signature,
            1,
            listOf(blancAnnotation("\$generatedInit")),
            listOf(),
            parametrs
        )
        val newInit = JcMethodImpl(newMethodInfo, JcFeaturesChain(newFeatures), classTable.origClass!!)

        return newInit
    }

    private fun getId(cp : JcClasspath, classTable: TableInfo) : JcMethod {

        val newFeatures = cp.features!!.toPersistentList().add(JcGeneratedGetIdTransformer(classTable))

        val desc = "()L${classTable.idColumn!!.type.internalDesc};"

        val methodInfo = MethodInfo(
            "\$getId",
            desc,
            null,
            1,
            listOf(blancAnnotation("\$generatedGetId")),
            listOf(),
            listOf()
        )
        val getId = JcMethodImpl(methodInfo, JcFeaturesChain(newFeatures), classTable.origClass!!)

        return getId
    }

    private fun getFilter(cp : JcClasspath, rel : RelationType, clazz : JcClassOrInterface) : JcMethod {

        val features = cp.features!!.toPersistentList()
        val newFeatures = JcFeaturesChain(features.add(JcFilterSubclassTransformer()))

        val desc = "(${rel.origField.type.typeName.jvmName()})Ljava/lang/Boolean;"
        val methodInfo = MethodInfo(
            "\$${rel.origField.name}filter",
            desc,
            null,
            1,
            listOf(blancAnnotation("\$generatedFilter")),
            listOf(),
            listOf(ParameterInfo(rel.origField.type.typeName, 0, 1, "subc", listOf()))
        )
        val filter = JcMethodImpl(methodInfo, newFeatures, clazz)

        return filter
    }

    private fun getBtwFilter(cp : JcClasspath, rel : RelationType, clazz: JcClassOrInterface) : JcMethod {

        val features = cp.features!!.toPersistentList()
        val newFeatures = JcFeaturesChain(features.add(JcBtwFilterTransformer()))

        val desc = "([Ljava/lang/Object;)Ljava/lang/Boolean;"
        val methodInfo = MethodInfo(
            "\$${rel.origField.name}BetweenFilter",
            desc,
            null,
            1,
            listOf(blancAnnotation("\$generatedBtwFilter")),
            listOf(),
            listOf(ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf()))
        )
        val btwFilter = JcMethodImpl(methodInfo, newFeatures, clazz)

        return btwFilter
    }

    private fun getBtwSelector(
        cp : JcClasspath,
        rel : RelationType,
        clazz: JcClassOrInterface,
        subTable: TableInfo
    ) : JcMethod {

        val features = cp.features!!.toPersistentList()
        val newFeatures = JcFeaturesChain(features.add(JcBtwSelectorTransformer()))

        val desc = "([Ljava/lang/Object;)L${subTable.idColumn!!.type.internalDesc};"
        val methodInfo = MethodInfo(
            "\$${rel.origField.name}BetweenSelector",
            desc,
            null,
            1,
            listOf(blancAnnotation("\$generatedBtwSelector")),
            listOf(),
            listOf(ParameterInfo("java.lang.Object[]", 0, 1, "row", listOf()))
        )
        val selector = JcMethodImpl(methodInfo, newFeatures, clazz)

        return selector
    }

    private fun getSetFilter(
        cp : JcClasspath,
        rel : RelationType,
        clazz: JcClassOrInterface,
        subTable : TableInfo
    ) : JcMethod {

        val feature = cp.features!!.toPersistentList()
        val newFeatures = JcFeaturesChain(feature.add(JcSetFilterTransformer()))

        val desc = "(L${subTable.idColumn!!.type.internalDesc};)Ljava/lang/Boolean;"
        val methodInfo = MethodInfo(
            "\$${rel.origField.name}SetFilter",
            desc,
            null,
            1,
            listOf(blancAnnotation("\$generatedSetFilter")),
            listOf(),
            listOf(ParameterInfo(rel.origField.type.typeName, 0, 1, "subc", listOf()))
        )
        val filter = JcMethodImpl(methodInfo, newFeatures, clazz)

        return filter
    }

    private fun getLambdas(cp : JcClasspath, classTable: TableInfo, collector: JcTableInfoCollector) : List<JcMethod> {

        println(collector)

        val clazz = classTable.origClass!!
        val methods = mutableListOf<JcMethod>()

        classTable.relations.forEach { rel ->

            if (rel.withTable) {

                val subTable = collector.findSubTable(rel)!!

                methods.add(getBtwFilter(cp, rel, clazz))
                methods.add(getBtwSelector(cp, rel, clazz, subTable))
                methods.add(getSetFilter(cp, rel, clazz, subTable))
            }
            else {
                methods.add(getFilter(cp, rel, clazz))
            }
        }

        return methods
    }
}
