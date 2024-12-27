package org.usvm.machine.interpreter

import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.fields
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.*

val JcClassOrInterface.isDataClass: Boolean get() = annotations.any { it.jcClass?.simpleName == "Entity" }
val JcClassOrInterface.columns : List<JcField> get() = declaredFields.filter { it.annotations.any { it.jcClass?.simpleName.equals("Column") } } // TODO: other links

val JcClassOrInterface.initDesc : String get() = columns.joinToString { "L${it.type.typeName.replace(".", "/")};" }.let { "(${it})V" }

object Foo : JcClassExtFeature {

    override fun fieldsOf(clazz: JcClassOrInterface, originalFields: List<JcField>): List<JcField>? {
        return super.fieldsOf(clazz, originalFields)
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {
        return super.methodsOf(clazz, originalMethods)
    }
}

object JcJPADataClassConstructorTransformer : JcClassExtFeature {

    private fun hasInitFromColumns(clazz: JcClassOrInterface, methods : List<JcMethod>) : Boolean {
        return methods.any { it.description.equals(clazz.initDesc) }
    }

//    override fun methodsOf(clazz: JcClassOrInterface): List<JcMethod>? {
//
//        if (!clazz.isDataClass) return null
//
//        // cp.findClass("generated.org.springframework.boot.Test")
//
//        //assert(!hasInitFromColumns(clazz, cl))
//
//        return null
//    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isDataClass) return null

        assert(!hasInitFromColumns(clazz, originalMethods))

        val blanckInit = getBlanckInit(clazz, originalMethods)
        val lambdas = getBlanckLambdas(clazz)

        return originalMethods + blanckInit + lambdas
    }

    private fun getBlanckInit(clazz : JcClassOrInterface, originalMethods: List<JcMethod>) : JcMethod {

        val initMethod = originalMethods.find{ it.name.equals("<init>") }!!

        val parametersInfo = clazz.columns.mapIndexed { i, col ->
            ParameterInfo(col.type.typeName, i, 1, col.name, listOf())
        }

        val features = clazz.classpath.features!!.toPersistentList().add(JcJPAGeneratedInitTransformer)

        val blanckInit = JcMethodImpl(
            MethodInfo(
                "<init>",
                clazz.initDesc,
                null,
                1,
                listOf(AnnotationInfo("GeneratedInit", true, listOf(), null, null)),
                listOf(),
                parametersInfo
            ),

            JcFeaturesChain(features),
            initMethod.enclosingClass
        )

        return blanckInit
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
