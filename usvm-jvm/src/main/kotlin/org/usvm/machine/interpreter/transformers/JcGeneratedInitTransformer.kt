package org.usvm.machine.interpreter.transformers

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcInstLocation
import org.jacodb.impl.cfg.JcInstLocationImpl

val JcMethod.isGeneratedInit : Boolean get() = annotations.any { it.jcClass?.simpleName.equals("GeneratedInit") }
val JcClassOrInterface.freeLineNumber : Int get() = declaredMethods.maxOf {
    it.instList.lastOrNull()?.lineNumber ?: -1
} + 1

object JcGeneratedInitTransformer : JcInstExtFeature {

    // TODO: check in debug
    override fun transformInstList(method: JcMethod, list: JcInstList<JcInst>): JcInstList<JcInst> {

        if (!method.isGeneratedInit) return list

        return list

//        val enClass = method.enclosingClass
//        val cp = enClass.classpath
//        val className = enClass.name
//        val classType = cp.findType(className)
//        val thisInst = JcThis(classType)
//
//        val locationManager = LocationManager(method, enClass.freeLineNumber)
//
//        val methodRef = TypedSpecialMethodRefImpl(cp.objectType, "<init>", listOf(), TypeNameImpl("java.lang.Void"))
//        val callExpr = JcSpecialCallExpr(methodRef, thisInst, listOf())
//        val initInst = JcCallInst(locationManager.newLocation(), callExpr) as JcInst
//
//        val assignInst = enClass.columns.mapIndexed { index, col ->
//
//            val fieldInfo = JcTypedFieldImpl(classType as JcRefType, col, JcSubstitutorImpl())
//            val lhv = JcFieldRef(thisInst, fieldInfo)
//            val rhv = JcArgument(index, col.name, col.type as JcType)
//
//            JcAssignInst(locationManager.newLocation(), lhv, rhv)
//        }
//
//        val retInst = JcReturnInst(locationManager.newLocation(), null) as JcInst
//
//        return JcInstListImpl(listOf(initInst) + assignInst + listOf(retInst))
    }


    private class LocationManager {

        private val initIndex : Int
        private var lastIndex : Int
        private val owner : JcMethod

        constructor(owner : JcMethod, startIndex : Int) {
            lastIndex = if (startIndex < 0) 0 else startIndex
            initIndex = lastIndex
            this.owner = owner
        }

        fun newLocation() : JcInstLocation {
            val v = JcInstLocationImpl(owner, lastIndex - initIndex, lastIndex)
            lastIndex++

            return  v
        }
    }
}
