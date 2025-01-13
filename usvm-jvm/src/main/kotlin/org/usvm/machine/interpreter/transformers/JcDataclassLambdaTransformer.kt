package org.usvm.machine.interpreter

import org.jacodb.api.jvm.JcInstExtFeature

object JcDataclassLambdaTransformer : JcInstExtFeature {
}

// Boolean filter(Subcl s) { return s.$getId() == oneToMany_id; }
class JcFilterSubclassTransformer : JcInstExtFeature {

}

// Boolean betweenFilter(Object[] row) { return row[0] == id; }
class JcBtwFilterTransformer : JcInstExtFeature {

}

// Integer betweenSelector(Object[] row) { return (Integer) row[1]; }
class JcBtwSelectorTransformer : JcInstExtFeature {

}

// Boolean setFilter(Subcl s) { return mtmSet.contains(s.$getId()); }
class JcSetFilterTransformer : JcInstExtFeature {

}
