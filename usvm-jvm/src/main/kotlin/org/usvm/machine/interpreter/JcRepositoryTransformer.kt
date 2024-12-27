package org.usvm.machine.interpreter

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor
import org.antlr.v4.runtime.tree.ParseTree
import org.hibernate.grammars.hql.HqlParser
import org.hibernate.grammars.hql.HqlParserVisitor
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.impl.cfg.JcInstListImpl

private val JcClassOrInterface.isJpaRepository: Boolean get() = interfaces.any { it.name ==  "org.springframework.data.repository.Repository"}
private val JcClassOrInterface.getDataClass: JcClassOrInterface get() {


    // TODO: signatures is string. Mb new field in JcClassOrInterface?
    val signatures = signature!!.substringAfter("<").substringBefore(">").split(";")
    val dataClassName = signatures[0].replace("/", ".").substring(1)
    val dataClass = classpath.findClass(dataClassName)

    //val countOfFields = dataClass.fields.filter { it.annotations.any { it.name == "Column" } }.count()

    return dataClass
}
private val JcAnnotation.isQuery : Boolean get() = jcClass?.simpleName.equals("Query")
private val JcMethod.query : String? get () = annotations.find { it.isQuery }?.values?.get("value") as String?


object JcRepositoryTransformer : JcClassExtFeature {

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isJpaRepository) return null

        // TODO: change transformQuery to method that adds feature with parser
        val newMethods = originalMethods.map { transformQuery(it) }

        return newMethods
    }

    fun transformQuery(method: JcMethod): JcMethod {

        val query = method.query

        if (query.isNullOrBlank()) return method

        /*val lexer = HqlLexer(CharStreams.fromString(query))
        val tokens = CommonTokenStream(lexer)
        val parser = HqlParser(tokens)
        val queryCtx = parser.statement()

        val visitor = JPAQueryVisitor()
        visitor.visit(queryCtx)*/

        return method
    }


    private class JPAQueryVisitor() : AbstractParseTreeVisitor<JcInstList<JcInst>>(), HqlParserVisitor<JcInstList<JcInst>> {

        fun visitNullable(ctx: ParseTree?) : JcInstList<JcInst> {

            return ctx?.let { super.visit(it) } ?: JcInstListImpl(listOf())
        }

        override fun visitStatement(ctx: HqlParser.StatementContext?): JcInstList<JcInst> {
            return visitChildren(ctx)
        }

        override fun visitSelectStatement(ctx: HqlParser.SelectStatementContext?): JcInstList<JcInst> {

            return visitChildren(ctx)
        }

        override fun visitSubquery(ctx: HqlParser.SubqueryContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTargetEntity(ctx: HqlParser.TargetEntityContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDeleteStatement(ctx: HqlParser.DeleteStatementContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitUpdateStatement(ctx: HqlParser.UpdateStatementContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSetClause(ctx: HqlParser.SetClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAssignment(ctx: HqlParser.AssignmentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInsertStatement(ctx: HqlParser.InsertStatementContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTargetFields(ctx: HqlParser.TargetFieldsContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitValuesList(ctx: HqlParser.ValuesListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitValues(ctx: HqlParser.ValuesContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitWithClause(ctx: HqlParser.WithClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCte(ctx: HqlParser.CteContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCteAttributes(ctx: HqlParser.CteAttributesContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSearchClause(ctx: HqlParser.SearchClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSearchSpecifications(ctx: HqlParser.SearchSpecificationsContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSearchSpecification(ctx: HqlParser.SearchSpecificationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCycleClause(ctx: HqlParser.CycleClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSimpleQueryGroup(ctx: HqlParser.SimpleQueryGroupContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            val where = visitNullable(nnctx.withClause())
            val ordered = visitNullable(nnctx.orderedQuery())

            println(where)
            println(ordered)

            return JcInstListImpl(listOf())
        }

        override fun visitSetQueryGroup(ctx: HqlParser.SetQueryGroupContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitQuerySpecExpression(ctx: HqlParser.QuerySpecExpressionContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            val query = visitNullable(nnctx.query())
            val ordered = visitNullable(nnctx.queryOrder())

            println(query)
            println(ordered)

            return JcInstListImpl(listOf())
        }

        override fun visitNestedQueryExpression(ctx: HqlParser.NestedQueryExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitQueryOrderExpression(ctx: HqlParser.QueryOrderExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSetOperator(ctx: HqlParser.SetOperatorContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitQueryOrder(ctx: HqlParser.QueryOrderContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitQuery(ctx: HqlParser.QueryContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            // TODO: other parts
            val select = visitNullable(nnctx.selectClause())
            val from = visitNullable(nnctx.fromClause())

            println(select)
            println(from)

            return JcInstListImpl(listOf())
        }

        override fun visitFromClause(ctx: HqlParser.FromClauseContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            // TODO: visit others
            val entities = visitNullable(nnctx.entityWithJoins().first())

            return entities
        }

        override fun visitEntityWithJoins(ctx: HqlParser.EntityWithJoinsContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitRootEntity(ctx: HqlParser.RootEntityContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitRootSubquery(ctx: HqlParser.RootSubqueryContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityName(ctx: HqlParser.EntityNameContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitVariable(ctx: HqlParser.VariableContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCrossJoin(ctx: HqlParser.CrossJoinContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJpaCollectionJoin(ctx: HqlParser.JpaCollectionJoinContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJoin(ctx: HqlParser.JoinContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJoinType(ctx: HqlParser.JoinTypeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJoinPath(ctx: HqlParser.JoinPathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJoinSubquery(ctx: HqlParser.JoinSubqueryContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJoinRestriction(ctx: HqlParser.JoinRestrictionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSelectClause(ctx: HqlParser.SelectClauseContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            val selectionList = visitNullable(nnctx.selectionList())

            return selectionList
        }

        override fun visitSelectionList(ctx: HqlParser.SelectionListContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            // TODO: visit others
            val entities = visitNullable(nnctx.selection()[0])

            return entities
        }

        override fun visitSelection(ctx: HqlParser.SelectionContext?): JcInstList<JcInst> {

            val nnctx = ctx!!

            val variable = visitNullable(nnctx.variable())
            val expr = visitNullable(nnctx.selectExpression())

            println(variable)
            println(expr)

            return JcInstListImpl(listOf())
        }

        override fun visitSelectExpression(ctx: HqlParser.SelectExpressionContext?): JcInstList<JcInst> {



            return JcInstListImpl(listOf())
        }

        override fun visitMapEntrySelection(ctx: HqlParser.MapEntrySelectionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantiation(ctx: HqlParser.InstantiationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantiationTarget(ctx: HqlParser.InstantiationTargetContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantiationArguments(ctx: HqlParser.InstantiationArgumentsContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantiationArgument(ctx: HqlParser.InstantiationArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantiationArgumentExpression(ctx: HqlParser.InstantiationArgumentExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJpaSelectObjectSyntax(ctx: HqlParser.JpaSelectObjectSyntaxContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSimplePath(ctx: HqlParser.SimplePathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSimplePathElement(ctx: HqlParser.SimplePathElementContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPath(ctx: HqlParser.PathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPathContinuation(ctx: HqlParser.PathContinuationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSyntacticDomainPath(ctx: HqlParser.SyntacticDomainPathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGeneralPathFragment(ctx: HqlParser.GeneralPathFragmentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIndexedPathAccessFragment(ctx: HqlParser.IndexedPathAccessFragmentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTreatedNavigablePath(ctx: HqlParser.TreatedNavigablePathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollectionValueNavigablePath(ctx: HqlParser.CollectionValueNavigablePathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMapKeyNavigablePath(ctx: HqlParser.MapKeyNavigablePathContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGroupByClause(ctx: HqlParser.GroupByClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGroupByExpression(ctx: HqlParser.GroupByExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitHavingClause(ctx: HqlParser.HavingClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOrderByClause(ctx: HqlParser.OrderByClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOrderByFragment(ctx: HqlParser.OrderByFragmentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSortSpecification(ctx: HqlParser.SortSpecificationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNullsPrecedence(ctx: HqlParser.NullsPrecedenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSortExpression(ctx: HqlParser.SortExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSortDirection(ctx: HqlParser.SortDirectionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollateFunction(ctx: HqlParser.CollateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollation(ctx: HqlParser.CollationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLimitClause(ctx: HqlParser.LimitClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetClause(ctx: HqlParser.OffsetClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFetchClause(ctx: HqlParser.FetchClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFetchCountOrPercent(ctx: HqlParser.FetchCountOrPercentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitParameterOrIntegerLiteral(ctx: HqlParser.ParameterOrIntegerLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitParameterOrNumberLiteral(ctx: HqlParser.ParameterOrNumberLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitWhereClause(ctx: HqlParser.WhereClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIsDistinctFromPredicate(ctx: HqlParser.IsDistinctFromPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitBetweenPredicate(ctx: HqlParser.BetweenPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExistsPredicate(ctx: HqlParser.ExistsPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAndPredicate(ctx: HqlParser.AndPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIsFalsePredicate(ctx: HqlParser.IsFalsePredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGroupedPredicate(ctx: HqlParser.GroupedPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLikePredicate(ctx: HqlParser.LikePredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInPredicate(ctx: HqlParser.InPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitComparisonPredicate(ctx: HqlParser.ComparisonPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExistsCollectionPartPredicate(ctx: HqlParser.ExistsCollectionPartPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNegatedPredicate(ctx: HqlParser.NegatedPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitBooleanExpressionPredicate(ctx: HqlParser.BooleanExpressionPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOrPredicate(ctx: HqlParser.OrPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMemberOfPredicate(ctx: HqlParser.MemberOfPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIsEmptyPredicate(ctx: HqlParser.IsEmptyPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIsNullPredicate(ctx: HqlParser.IsNullPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIsTruePredicate(ctx: HqlParser.IsTruePredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitComparisonOperator(ctx: HqlParser.ComparisonOperatorContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPersistentCollectionReferenceInList(ctx: HqlParser.PersistentCollectionReferenceInListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExplicitTupleInList(ctx: HqlParser.ExplicitTupleInListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSubqueryInList(ctx: HqlParser.SubqueryInListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitParamInList(ctx: HqlParser.ParamInListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLikeEscape(ctx: HqlParser.LikeEscapeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAdditionExpression(ctx: HqlParser.AdditionExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFromDurationExpression(ctx: HqlParser.FromDurationExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitBarePrimaryExpression(ctx: HqlParser.BarePrimaryExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTupleExpression(ctx: HqlParser.TupleExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitUnaryExpression(ctx: HqlParser.UnaryExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGroupedExpression(ctx: HqlParser.GroupedExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitConcatenationExpression(ctx: HqlParser.ConcatenationExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMultiplicationExpression(ctx: HqlParser.MultiplicationExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitToDurationExpression(ctx: HqlParser.ToDurationExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSubqueryExpression(ctx: HqlParser.SubqueryExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitUnaryNumericLiteralExpression(ctx: HqlParser.UnaryNumericLiteralExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCaseExpression(ctx: HqlParser.CaseExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLiteralExpression(ctx: HqlParser.LiteralExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitParameterExpression(ctx: HqlParser.ParameterExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityTypeExpression(ctx: HqlParser.EntityTypeExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityIdExpression(ctx: HqlParser.EntityIdExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityVersionExpression(ctx: HqlParser.EntityVersionExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityNaturalIdExpression(ctx: HqlParser.EntityNaturalIdExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitToOneFkExpression(ctx: HqlParser.ToOneFkExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSyntacticPathExpression(ctx: HqlParser.SyntacticPathExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFunctionExpression(ctx: HqlParser.FunctionExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGeneralPathExpression(ctx: HqlParser.GeneralPathExpressionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExpressionOrPredicate(ctx: HqlParser.ExpressionOrPredicateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollectionQuantifier(ctx: HqlParser.CollectionQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitElementValueQuantifier(ctx: HqlParser.ElementValueQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIndexKeyQuantifier(ctx: HqlParser.IndexKeyQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitElementsValuesQuantifier(ctx: HqlParser.ElementsValuesQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIndicesKeysQuantifier(ctx: HqlParser.IndicesKeysQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMultiplicativeOperator(ctx: HqlParser.MultiplicativeOperatorContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAdditiveOperator(ctx: HqlParser.AdditiveOperatorContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSignOperator(ctx: HqlParser.SignOperatorContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityTypeReference(ctx: HqlParser.EntityTypeReferenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityIdReference(ctx: HqlParser.EntityIdReferenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityVersionReference(ctx: HqlParser.EntityVersionReferenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEntityNaturalIdReference(ctx: HqlParser.EntityNaturalIdReferenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitToOneFkReference(ctx: HqlParser.ToOneFkReferenceContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCaseList(ctx: HqlParser.CaseListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSimpleCaseList(ctx: HqlParser.SimpleCaseListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSimpleCaseWhen(ctx: HqlParser.SimpleCaseWhenContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCaseOtherwise(ctx: HqlParser.CaseOtherwiseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSearchedCaseList(ctx: HqlParser.SearchedCaseListContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSearchedCaseWhen(ctx: HqlParser.SearchedCaseWhenContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLiteral(ctx: HqlParser.LiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitBooleanLiteral(ctx: HqlParser.BooleanLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNumericLiteral(ctx: HqlParser.NumericLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitBinaryLiteral(ctx: HqlParser.BinaryLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTemporalLiteral(ctx: HqlParser.TemporalLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDateTimeLiteral(ctx: HqlParser.DateTimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLocalDateTimeLiteral(ctx: HqlParser.LocalDateTimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitZonedDateTimeLiteral(ctx: HqlParser.ZonedDateTimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetDateTimeLiteral(ctx: HqlParser.OffsetDateTimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDateLiteral(ctx: HqlParser.DateLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTimeLiteral(ctx: HqlParser.TimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDateTime(ctx: HqlParser.DateTimeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLocalDateTime(ctx: HqlParser.LocalDateTimeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitZonedDateTime(ctx: HqlParser.ZonedDateTimeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetDateTime(ctx: HqlParser.OffsetDateTimeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetDateTimeWithMinutes(ctx: HqlParser.OffsetDateTimeWithMinutesContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDate(ctx: HqlParser.DateContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTime(ctx: HqlParser.TimeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffset(ctx: HqlParser.OffsetContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetWithMinutes(ctx: HqlParser.OffsetWithMinutesContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitYear(ctx: HqlParser.YearContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMonth(ctx: HqlParser.MonthContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDay(ctx: HqlParser.DayContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitHour(ctx: HqlParser.HourContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitMinute(ctx: HqlParser.MinuteContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSecond(ctx: HqlParser.SecondContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitZoneId(ctx: HqlParser.ZoneIdContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJdbcTimestampLiteral(ctx: HqlParser.JdbcTimestampLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJdbcDateLiteral(ctx: HqlParser.JdbcDateLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJdbcTimeLiteral(ctx: HqlParser.JdbcTimeLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGenericTemporalLiteralText(ctx: HqlParser.GenericTemporalLiteralTextContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGeneralizedLiteral(ctx: HqlParser.GeneralizedLiteralContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGeneralizedLiteralType(ctx: HqlParser.GeneralizedLiteralTypeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGeneralizedLiteralText(ctx: HqlParser.GeneralizedLiteralTextContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNamedParameter(ctx: HqlParser.NamedParameterContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPositionalParameter(ctx: HqlParser.PositionalParameterContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFunction(ctx: HqlParser.FunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJpaNonstandardFunction(ctx: HqlParser.JpaNonstandardFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitJpaNonstandardFunctionName(ctx: HqlParser.JpaNonstandardFunctionNameContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGenericFunction(ctx: HqlParser.GenericFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGenericFunctionName(ctx: HqlParser.GenericFunctionNameContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitGenericFunctionArguments(ctx: HqlParser.GenericFunctionArgumentsContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollectionSizeFunction(ctx: HqlParser.CollectionSizeFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitElementAggregateFunction(ctx: HqlParser.ElementAggregateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIndexAggregateFunction(ctx: HqlParser.IndexAggregateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCollectionFunctionMisuse(ctx: HqlParser.CollectionFunctionMisuseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAggregateFunction(ctx: HqlParser.AggregateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEveryFunction(ctx: HqlParser.EveryFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAnyFunction(ctx: HqlParser.AnyFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitEveryAllQuantifier(ctx: HqlParser.EveryAllQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitAnySomeQuantifier(ctx: HqlParser.AnySomeQuantifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitListaggFunction(ctx: HqlParser.ListaggFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOnOverflowClause(ctx: HqlParser.OnOverflowClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitWithinGroupClause(ctx: HqlParser.WithinGroupClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFilterClause(ctx: HqlParser.FilterClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNullsClause(ctx: HqlParser.NullsClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNthSideClause(ctx: HqlParser.NthSideClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverClause(ctx: HqlParser.OverClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPartitionClause(ctx: HqlParser.PartitionClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFrameClause(ctx: HqlParser.FrameClauseContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFrameStart(ctx: HqlParser.FrameStartContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFrameEnd(ctx: HqlParser.FrameEndContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFrameExclusion(ctx: HqlParser.FrameExclusionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitStandardFunction(ctx: HqlParser.StandardFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCastFunction(ctx: HqlParser.CastFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCastTarget(ctx: HqlParser.CastTargetContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCastTargetType(ctx: HqlParser.CastTargetTypeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSubstringFunction(ctx: HqlParser.SubstringFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSubstringFunctionStartArgument(ctx: HqlParser.SubstringFunctionStartArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitSubstringFunctionLengthArgument(ctx: HqlParser.SubstringFunctionLengthArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTrimFunction(ctx: HqlParser.TrimFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTrimSpecification(ctx: HqlParser.TrimSpecificationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTrimCharacter(ctx: HqlParser.TrimCharacterContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPadFunction(ctx: HqlParser.PadFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPadSpecification(ctx: HqlParser.PadSpecificationContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPadCharacter(ctx: HqlParser.PadCharacterContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPadLength(ctx: HqlParser.PadLengthContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverlayFunction(ctx: HqlParser.OverlayFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverlayFunctionStringArgument(ctx: HqlParser.OverlayFunctionStringArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverlayFunctionReplacementArgument(ctx: HqlParser.OverlayFunctionReplacementArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverlayFunctionStartArgument(ctx: HqlParser.OverlayFunctionStartArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOverlayFunctionLengthArgument(ctx: HqlParser.OverlayFunctionLengthArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCurrentDateFunction(ctx: HqlParser.CurrentDateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCurrentTimeFunction(ctx: HqlParser.CurrentTimeFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCurrentTimestampFunction(ctx: HqlParser.CurrentTimestampFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitInstantFunction(ctx: HqlParser.InstantFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLocalDateTimeFunction(ctx: HqlParser.LocalDateTimeFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitOffsetDateTimeFunction(ctx: HqlParser.OffsetDateTimeFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLocalDateFunction(ctx: HqlParser.LocalDateFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitLocalTimeFunction(ctx: HqlParser.LocalTimeFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFormatFunction(ctx: HqlParser.FormatFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitFormat(ctx: HqlParser.FormatContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExtractFunction(ctx: HqlParser.ExtractFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTruncFunction(ctx: HqlParser.TruncFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitExtractField(ctx: HqlParser.ExtractFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDatetimeField(ctx: HqlParser.DatetimeFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDayField(ctx: HqlParser.DayFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitWeekField(ctx: HqlParser.WeekFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitTimeZoneField(ctx: HqlParser.TimeZoneFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitDateOrTimeField(ctx: HqlParser.DateOrTimeFieldContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPositionFunction(ctx: HqlParser.PositionFunctionContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPositionFunctionPatternArgument(ctx: HqlParser.PositionFunctionPatternArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitPositionFunctionStringArgument(ctx: HqlParser.PositionFunctionStringArgumentContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitCube(ctx: HqlParser.CubeContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitRollup(ctx: HqlParser.RollupContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitNakedIdentifier(ctx: HqlParser.NakedIdentifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }

        override fun visitIdentifier(ctx: HqlParser.IdentifierContext?): JcInstList<JcInst> {
            TODO("Not yet implemented")
        }


    }
}
