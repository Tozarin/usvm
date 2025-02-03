package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.impl.bytecode.JcFieldImpl
import org.jacodb.impl.types.AnnotationInfo
import org.jacodb.impl.types.FieldInfo
import org.objectweb.asm.Opcodes

class JcFieldBuilder(val clazz: JcClassOrInterface) {

    private var name: String? = null
    private var signature: String? = null
    private var access = Opcodes.ACC_PUBLIC
    private var type: String? = null
    private val annots = mutableListOf<AnnotationInfo>()

    fun setName(name: String): JcFieldBuilder {
        return this.also { it.name = name }
    }

    fun setSignature(sig: String): JcFieldBuilder {
        return this.also { signature = sig }
    }

    fun setAccess(access: Int): JcFieldBuilder {
        return this.also { it.access = access }
    }

    fun setType(type: String): JcFieldBuilder {
        return this.also { it.type = type }
    }

    fun addAnnot(annot: AnnotationInfo): JcFieldBuilder {
        return this.also { annots.add(annot) }
    }

    fun buildField(): JcField {
        val info = FieldInfo(name!!, signature, access, type!!, annots)
        return JcFieldImpl(clazz, info)
    }
}
