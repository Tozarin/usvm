package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.BsmHandleTag
import org.jacodb.api.jvm.cfg.BsmMethodTypeArg
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcConditionExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.TypedMethodRefImpl
import org.jacodb.impl.cfg.TypedStaticMethodRefImpl
import org.usvm.instrumentation.util.getTypename
import org.usvm.instrumentation.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.util.contains
import org.usvm.util.nameEquals

const val JAVA_BOOL = "java.lang.Boolean"
const val JAVA_SET = "java.util.Set"
const val JAVA_STRING = "java.lang.String"
const val JAVA_BIG_INT = "java.math.BigInteger"
const val JAVA_BIG_DECIMAL = "java.math.BigDecimal"

const val INIT_ANNOT = "\$generatedInit"
const val INIT_FETCH_ANNOT = "\$generatedFetchedAnnot"
const val GET_ID_ANNOT = "\$generatedGetId"
const val DATACLASS_GETTER = "\$generatedDataclassGetter"
const val FILTER_ANNOT = "\$generatedFilter"
const val FILTER_BTW_ANNOT = "\$generatedBtwFilter"
const val SELECT_BTW_ANNOT = "\$generatedBtwSelector"
const val FILTER_SET_ANNOT = "\$generatedSetFilter"
const val SERIALIZER_ANNOT = "\$generatedSerializer"
const val IDENTITY_ANNOT = "\$generatedIdent"
const val REPOSITORY_LAMBDA = "\$queryLambda"

const val DATABASES = "SpringDatabases"
const val CRUD_MANAGER = "generated.org.springframework.boot.databases.CrudManager"
const val ITABLE = "generated.org.springframework.boot.databases.ITable"
const val BASE_TABLE = "generated.org.springframework.boot.databases.BaseTable"
const val IWRAPPER = "generated.org.springframework.boot.databases.IWrapper"
const val PAGE_WRAPPER = "org.springframework.data.domain.Page"
const val PAGE_IMPL_WRAPPER = "org.springframework.data.domain.PageImpl"
const val SET_WRAPPER = "generated.org.springframework.boot.databases.SetWrapper"
const val LIST_WRAPPER = "generated.org.springframework.boot.databases.ListWrapper"
const val MAP_TABLE = "generated.org.springframework.boot.databases.MappedTable"
const val FILTER_TABLE = "generated.org.springframework.boot.databases.FiltredTable"
const val SORTED_TABLE = "generated.org.springframework.boot.databases.SortedTable"
const val JOIN_TABLE = "generated.org.springframework.boot.databases.JoinedTable"
const val DISTINCT_TABLE = "generated.org.springframework.boot.databases.DistinctTable"
const val FLAT_TABLE = "generated.org.springframework.boot.databases.FlatTable"
const val SINGLETON_TABLE = "generated.org.springframework.boot.databases.SingletonTable"
const val DATABASE_UTILS = "generated.org.springframework.boot.databases.Utils"

const val PREDICATE = "java.util.function.Predicate"
const val FUNCTION = "java.util.function.Function"
const val FUNCTION2 = "java.util.function.Function2"

val JcMethod.generatedSubFilter: Boolean get() = contains(this.annotations, FILTER_ANNOT)
val JcMethod.generatedBtwFilter: Boolean get() = contains(this.annotations, FILTER_BTW_ANNOT)
val JcMethod.generatedBtwSelect: Boolean get() = contains(this.annotations, SELECT_BTW_ANNOT)
val JcMethod.generatedSetFilter: Boolean get() = contains(this.annotations, FILTER_SET_ANNOT)
val JcMethod.generatedGetId: Boolean get() = contains(annotations, GET_ID_ANNOT)
val JcMethod.generatedGetter: Boolean get() = contains(annotations, DATACLASS_GETTER)
val JcMethod.generatedIdentity: Boolean get() = contains(annotations, IDENTITY_ANNOT)
val JcMethod.generatedSerializer: Boolean get() = contains(annotations, SERIALIZER_ANNOT)
val JcMethod.generatedInit: Boolean get() = contains(this.annotations, INIT_ANNOT)
val JcMethod.generatedFetchInit: Boolean get() = contains(this.annotations, INIT_FETCH_ANNOT)
val JcMethod.repositoryLambda: Boolean get() = contains(annotations, REPOSITORY_LAMBDA)

fun JcMethod.isGeneratedGetter(fieldName: String): Boolean {
    return contains(annotations, DATACLASS_GETTER) && contains(annotations, fieldName)
}

val JcClassOrInterface.isDataClass: Boolean get() = contains(annotations, "Entity")
val JcClassOrInterface.isJpaRepository: Boolean
    get() =
        interfaces.any { it.name == "org.springframework.data.repository.Repository" }

val JcTypedMethod.methodRef: TypedMethodRefImpl
    get() = TypedMethodRefImpl(
        enclosingType as JcClassType,
        name,
        method.parameters.map { it.type },
        method.returnType
    )

val JcTypedMethod.staticMethodRef: TypedStaticMethodRefImpl
    get() = TypedStaticMethodRefImpl(
        enclosingType as JcClassType,
        name,
        method.parameters.map { it.type },
        method.returnType
    )

val JcMethod.query: String?
    get() =
        annotations.find { nameEquals(it, "Query") }?.values?.get("value") as String?

val JcParameter.parameterName: String
    get() =
        annotations.find { nameEquals(it, "Param") }?.values?.get("value") as String?
            ?: name!!

val JcParameter.toArgument: JcArgument
    get() = JcArgument(index, name!!, type.toJcType(method.enclosingClass.classpath)!!)


fun BlockGenerationContext.compare(cp: JcClasspath, cond: JcConditionExpr, name: String): JcLocalVar {
    val endOfIf: JcInstRef
    addInstruction { loc ->
        val nextInst = JcInstRef(loc.index + 1)
        val elseBranch = JcInstRef(loc.index + 3)
        endOfIf = JcInstRef(loc.index + 5)
        JcIfInst(loc, cond, nextInst, elseBranch)
    }

    val ifResVal = nextLocalVar("if$name", cp.boolean)
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(true, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(false, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }

    return ifResVal
}

fun BlockGenerationContext.toBoolean(cp: JcClasspath, value: JcLocalVar): JcLocalVar {
    val boolType = cp.findType(JAVA_BOOL) as JcClassType
    val castToBool = boolType.declaredMethods.single {
        it.isStatic && it.name == "valueOf" && it.parameters.first().type.typeName == "boolean"
    }
    val res = nextLocalVar("toBool${value.name}", cp.findType(JAVA_BOOL))
    val cast = JcStaticCallExpr(castToBool.staticMethodRef, listOf(value))
    addInstruction { loc -> JcAssignInst(loc, res, cast) }
    return res
}

fun BlockGenerationContext.generateNew(name: String, type: JcType): JcLocalVar {
    val vari = nextLocalVar(name, type)
    val newExpr = JcNewExpr(type)
    addInstruction { loc -> JcAssignInst(loc, vari, newExpr) }
    return vari
}

fun BlockGenerationContext.generateObjectArray(cp: JcClasspath, name: String, size: Int): JcLocalVar {
    val objArrayType = cp.arrayTypeOf(cp.objectType, true, listOf())
    val vari = nextLocalVar(name, objArrayType)
    val arr = JcNewArrayExpr(objArrayType, listOf(JcInt(size, cp.int)))
    addInstruction { loc -> JcAssignInst(loc, vari, arr) }
    return vari;
}

fun BlockGenerationContext.putArgumentsToArray(cp: JcClasspath, name: String, method: JcMethod): JcLocalVar {
    val arr = generateObjectArray(cp, "args#$name", method.parameters.size)
    method.parameters.forEachIndexed { ix, p ->
        val access = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, access, p.toArgument) }
    }
    return arr
}

fun BlockGenerationContext.generateNewWithInit(name: String, type: JcClassType, args: List<JcValue>): JcLocalVar {
    val vari = generateNew(name, type)
    val init = type.declaredMethods.single {
        it.name == "<init>" && it.parameters.size == args.size
        //&& it.method.description == "(${args.joinToString(separator = "") { it.type.internalName.jvmName() }})V"
    }
    val call = JcSpecialCallExpr(init.methodRef, vari, args)
    addInstruction { loc -> JcCallInst(loc, call) }
    return vari
}

// method with methodName and sizeOf(args) count of arguments is single in clazz
fun BlockGenerationContext.generateStaticCall(
    name: String,
    methodName: String,
    clazz: JcClassType,
    args: List<JcValue>
): JcLocalVar {
    val method =
        clazz.declaredMethods.single { it.name == methodName && it.parameters.size == args.size && it.isStatic }
    val res = nextLocalVar(name, method.returnType)
    val call = JcStaticCallExpr(method.methodRef, args)
    addInstruction { loc -> JcAssignInst(loc, res, call) }
    return res
}

fun BlockGenerationContext.generateLambda(cp: JcClasspath, name: String, method: JcMethod): JcLocalVar {
    val (callSiteName, callSiteRetType) = if (method.returnType.typeName == "java.lang.Boolean") {
        Pair("test", cp.findType(PREDICATE))
    } else {
        val type = if (method.parameters.size == 1) {
            cp.findType(FUNCTION)
        } else {
            cp.findType(FUNCTION + method.parameters.size)
        }
        Pair("apply", type)
    }

    val lambdaVar = nextLocalVar(name, callSiteRetType)
    val lambda = getLambda(cp, method, callSiteName, callSiteRetType)
    addInstruction { loc -> JcAssignInst(loc, lambdaVar, lambda) }

    return lambdaVar
}

fun BlockGenerationContext.generateLambda(name: String, method: JcMethod): JcLocalVar {
    val cp = method.enclosingClass.classpath
    return generateLambda(cp, name, method)
}

fun getLambda(cp: JcClasspath, method: JcMethod, callSiteName: String, callSiteRetType: JcType): JcLambdaExpr {

    val bsm = cp.findType("java.lang.invoke.LambdaMetafactory").let { it as JcClassType }
        .declaredMethods.single { it.name == "metafactory" }
        .methodRef

    val classType = method.enclosingClass.toType()
    val argTypes = method.parameters.map { it.type }
    val actualMethod = TypedMethodRefImpl(classType, method.name, argTypes, method.returnType)

    val interfaceMethodType = BsmMethodTypeArg(argTypes, method.returnType)
    val dynamicMethodType = BsmMethodTypeArg(argTypes.map { cp.objectType.getTypename() }, method.returnType)

    val callSiteArgTypes = listOf(classType as JcType)
    val callSiteArgs = listOf(JcThis(classType))

    return JcLambdaExpr(
        bsm,
        actualMethod,
        interfaceMethodType,
        dynamicMethodType,
        callSiteName,
        callSiteArgTypes,
        callSiteRetType,
        callSiteArgs,
        BsmHandleTag.MethodHandle.INVOKE_VIRTUAL
    )
}
