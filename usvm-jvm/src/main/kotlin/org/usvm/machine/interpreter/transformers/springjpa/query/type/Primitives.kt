package org.usvm.machine.interpreter.transformers.springjpa.query.type

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.double
import org.jacodb.api.jvm.ext.float
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.long
import org.usvm.instrumentation.util.stringType
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo

abstract class Primitive : TypeCtx() {
    class String : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.stringType()
        }
    }

    class Bool : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.boolean
        }
    }

    class Int : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.int
        }
    }

    class Long : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.long
        }
    }

    class Float : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.float
        }
    }

    class Double : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.cp.double
        }
    }

    class BigInt : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.bigIntType
        }
    }

    class BigDecimal : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.bigDecimalType
        }
    }

    class Binary : Primitive() {
        override fun getType(info: CommonInfo): JcType {
            return info.byteArrType
        }
    }
}
