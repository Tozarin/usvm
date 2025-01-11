package org.usvm.machine.interpreter.transformers

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.fields
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.*
import org.usvm.util.JcTableInfoCollector
import org.usvm.util.TableInfo
import org.usvm.util.contains
import org.usvm.util.jvmDescriptor

val JcClassOrInterface.isDataClass: Boolean get() = contains(annotations, "Entity")

// cp.findClass("generated.org.springframework.boot.databases.FirstDataClass")

object JcDataclassTransformer : JcClassExtFeature {

    override fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? {

        if (!clazz.isDataClass) return null

        return super.fieldsOf(clazz, originalFields)
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isDataClass) return null

        val cp = clazz.classpath
        val collector = JcTableInfoCollector(cp)
        val classTable = collector.collectTable(clazz)

        val blanckInit = getBlanckInit(cp, classTable, collector)
        val getId = getId(cp, classTable)
        val lambdas = getBlanckLambdas(clazz)

        return originalMethods + blanckInit + getId + lambdas
    }

    private fun getBlanckInit(cp : JcClasspath, classTable : TableInfo, collector: JcTableInfoCollector) : JcMethod {

        val newFeatures = cp.features!!.toPersistentList().add(JcGeneratedInitTransformer)
        val relTables = collector.tablesInfo.values.filter { !it.name.equals(classTable.name) && it.hasId }

        val itableDesc = cp.findClass("generated.org.springframework.boot.databases.ITable").jvmDescriptor
        val desc = "([Ljava/lang/Object;${relTables.joinToString(separator = "") { itableDesc }})V"

        val signature = "([Ljava/lang/Object;${relTables.joinToString(separator = "") { info -> 
            "${itableDesc}<${info.origClass!!.jvmDescriptor}>"
        }};)V"

        // generated.org.springframework.boot.databases.ITable
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
            listOf(AnnotationInfo("\$generatedInit", false, listOf(), null, null)),
            listOf(),
            parametrs
        )
        val newInit = JcMethodImpl(
            newMethodInfo,
            JcFeaturesChain(newFeatures),
            classTable.origClass!!
        ) as JcMethod

        return newInit
    }

    private fun getId(cp : JcClasspath, classTable: TableInfo) : JcMethod {

        val newFeatures = cp.features!!.toPersistentList().add(JcGeneratedInitTransformer)


    }

    private fun getBlanckLambdas(clazz : JcClassOrInterface) : List<JcMethod> {

        //val features = clazz.classpath.features!!.toPersistentList().add(JcJPADataClassLambdaTransformer)

        fun collectColumns(dataClass: JcClassOrInterface): List<JcField> {

            val supClass = dataClass.superClass
            val columns = dataClass.fields +

                    if (supClass != null && supClass.annotations.any { it.jcClass?.simpleName.equals("MappedSuperclass") })
                        collectColumns(supClass)
                    else
                        listOf()

            return columns.sortedBy { it.name }
        }

        val columns = collectColumns(clazz).filter { field ->
            field.annotations.any {
                listOf("OneToOne", "OneToMany", "ManyToOne", "ManyToMany")
                    .contains(it.jcClass?.simpleName)
            }
        }


        val methods = columns.map { col ->
            println(col)
        }

        println(methods)

        return listOf()
    }
}
