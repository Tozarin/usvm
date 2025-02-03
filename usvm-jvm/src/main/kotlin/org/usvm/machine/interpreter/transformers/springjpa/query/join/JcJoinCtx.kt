package org.usvm.machine.interpreter.transformers.springjpa.query.join

open class JoinCtx() {

    class JpaCollectionJoin() : JoinCtx() {} // deprecated syntax (never documented)

    class Join() : JoinCtx()
    class CrossJoin() : JoinCtx()
}
