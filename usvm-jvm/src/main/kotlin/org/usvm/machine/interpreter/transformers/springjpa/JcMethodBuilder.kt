package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.impl.bytecode.JcMethodImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.MethodInfo
import org.jacodb.impl.types.ParameterInfo
import org.objectweb.asm.Opcodes
import org.usvm.util.blancAnnotation

class JcMethodBuilder(
    val clazz: JcClassOrInterface
) {
    private val paramsBuilder = JcParamBuilder()

    private val features = clazz.classpath.features!!.toMutableList()

    private var name: String? = null
    private var desc: String? = null
    private var signature: String? = null
    private var access = Opcodes.ACC_PUBLIC
    private val annots = mutableListOf<AnnotationInfo>()
    private val exeps = mutableListOf<String>()
    private val params = mutableListOf<ParameterInfo>()

    fun addFillerFuture(feature: JcMethodExtFeature): JcMethodBuilder {
        return this.also { it.features.add(0, feature) }
    }

    fun setName(name: String): JcMethodBuilder {
        return this.also { it.name = name }
    }

    fun setDesc(desc: String): JcMethodBuilder {
        return this.also { it.desc = desc }
    }

    fun setSignature(sig: String?): JcMethodBuilder {
        return this.also { signature = sig }
    }

    fun setAccess(access: Int): JcMethodBuilder {
        return this.also { it.access = access }
    }

    fun addAnnot(annot: AnnotationInfo): JcMethodBuilder {
        return this.also { annots.add(annot) }
    }

    fun addExep(exep: String): JcMethodBuilder {
        return this.also { exeps.add(exep) }
    }

    fun addParam(param: ParameterInfo): JcMethodBuilder {
        return this.also { it.params.add(param) }
    }

    fun addBlanckAnnot(name: String): JcMethodBuilder {
        return this.addAnnot(blancAnnotation(name))
    }

    fun addFreshParam(type: String): JcMethodBuilder {
        return addParam(paramsBuilder.setType(type).buildParam())
    }

    fun buildMethod(): JcMethod {
        val info = MethodInfo(name!!, desc!!, signature, access, annots, exeps, params)
        return JcMethodImpl(info, JcFeaturesChain(features), clazz)
    }
}

class JcParamBuilder {

    private var type: String? = null
    private var ix = 0
    private var access = Opcodes.ACC_PUBLIC
    private var name: String? = null
    private val annots = mutableListOf<AnnotationInfo>()

    fun setType(type: String): JcParamBuilder {
        return this.also { it.type = type }
    }

    fun setIndex(ix: Int): JcParamBuilder {
        return this.also { it.ix = ix }
    }

    fun setAccess(access: Int): JcParamBuilder {
        return this.also { it.access = access }
    }

    fun setName(name: String): JcParamBuilder {
        return this.also { it.name = name }
    }

    fun addAnnot(annot: AnnotationInfo): JcParamBuilder {
        return this.also { it.annots.add(annot) }
    }

    fun buildParam(): ParameterInfo {
        val n = name ?: "\$p$ix"
        return ParameterInfo(type!!, ix++, access, n, annots)
    }
}
