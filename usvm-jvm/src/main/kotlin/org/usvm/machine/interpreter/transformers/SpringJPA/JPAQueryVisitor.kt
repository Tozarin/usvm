package org.usvm.machine.interpreter.transformers.SpringJPA

import org.antlr.v4.runtime.tree.AbstractParseTreeVisitor
import org.antlr.v4.runtime.tree.TerminalNode
import org.hibernate.grammars.hql.HqlLexer
import org.hibernate.grammars.hql.HqlParser
import org.hibernate.grammars.hql.HqlParserVisitor
import org.hibernate.internal.util.QuotingHelper
import org.hibernate.internal.util.QuotingHelper.unquoteJavaStringLiteral
import org.hibernate.internal.util.QuotingHelper.unquoteStringLiteral
import org.hibernate.type.descriptor.java.PrimitiveByteArrayJavaType

class JPAQueryVisitor : AbstractParseTreeVisitor<Any>(), HqlParserVisitor<Any> {

    override fun visitStatement(ctx: HqlParser.StatementContext): Any {
        return visit(ctx.getChild(0)) // TODO: switch by type of statement
    }

    override fun visitSelectStatement(ctx: HqlParser.SelectStatementContext): SelectCtx {
        return visitChildren(ctx) as SelectCtx
    }

    override fun visitSubquery(ctx: HqlParser.SubqueryContext): SelectCtx {
        return visitChildren(ctx) as SelectCtx
    }

    override fun visitTargetEntity(ctx: HqlParser.TargetEntityContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDeleteStatement(ctx: HqlParser.DeleteStatementContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitUpdateStatement(ctx: HqlParser.UpdateStatementContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSetClause(ctx: HqlParser.SetClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitAssignment(ctx: HqlParser.AssignmentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInsertStatement(ctx: HqlParser.InsertStatementContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTargetFields(ctx: HqlParser.TargetFieldsContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitValuesList(ctx: HqlParser.ValuesListContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitValues(ctx: HqlParser.ValuesContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitWithClause(ctx: HqlParser.WithClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCte(ctx: HqlParser.CteContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCteAttributes(ctx: HqlParser.CteAttributesContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSearchClause(ctx: HqlParser.SearchClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSearchSpecifications(ctx: HqlParser.SearchSpecificationsContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSearchSpecification(ctx: HqlParser.SearchSpecificationContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCycleClause(ctx: HqlParser.CycleClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSimpleQueryGroup(ctx: HqlParser.SimpleQueryGroupContext): SelectCtx {
        //val with = visit(ctx.withClause()) // TODO: https://www.geeksforgeeks.org/sql-with-clause/
        val query = visit(ctx.orderedQuery()) as SelectCtx
        return query
    }

    override fun visitSetQueryGroup(ctx: HqlParser.SetQueryGroupContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitQuerySpecExpression(ctx: HqlParser.QuerySpecExpressionContext): SelectCtx {
        val order = visitQueryOrder(ctx.queryOrder())
        val query = visitQuery(ctx.query())
        val sCtx = SelectCtx(order, query)
        return sCtx
    }

    override fun visitNestedQueryExpression(ctx: HqlParser.NestedQueryExpressionContext): SelectCtx {
        val order = visitQueryOrder(ctx.queryOrder())
        val subQuery = visit(ctx.queryExpression()) as SelectCtx
        subQuery.addOrder(order)
        return subQuery
    }

    override fun visitQueryOrderExpression(ctx: HqlParser.QueryOrderExpressionContext?): SelectCtx {
        TODO("Not yet implemented")
    }

    override fun visitSetOperator(ctx: HqlParser.SetOperatorContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitQueryOrder(ctx: HqlParser.QueryOrderContext?): List<OrderCtx> {
        if (ctx == null) return listOf()
        val limit = visitLimitClause(ctx.limitClause())
        val offset = visitOffsetClause(ctx.offsetClause())
        // val fetch = visitFetchClause(ctx.fetchClause()) TODO:
        val order = visitOrderByClause(ctx.orderByClause())
        order.setLimit(limit)
        order.setOffset(offset)
        return listOf(order)
    }

    override fun visitQuery(ctx: HqlParser.QueryContext): QueryCtx {
        val from = visitFromClause(ctx.fromClause())
        val where = visitWhereClause(ctx.whereClause())
        val select = visitSelectClause(ctx.selectClause())
        val query = QueryCtx(from, where, select)
        return query
    }

    override fun visitFromClause(ctx: HqlParser.FromClauseContext?): FromCtx? {
        if (ctx == null) return null
        val tbls = mutableListOf<TableWithJoinsCtx>()
        ctx.entityWithJoins().forEach { tbls.add(visitEntityWithJoins(it as HqlParser.EntityWithJoinsContext)) }
        val from = FromCtx(tbls)
        return from
    }

    override fun visitEntityWithJoins(ctx: HqlParser.EntityWithJoinsContext): TableWithJoinsCtx {
        val root = visit(ctx.fromRoot()) as TableCtx
        val joins = mutableListOf<JoinCtx>()
        // TODO: visitJoin, visitCrossJoin, visitJpaCollectionJoin (deprecated)
        for (i in 1 ..< ctx.childCount) { joins.add(visit(ctx.getChild(i)) as JoinCtx) }
        val tbl = TableWithJoinsCtx(root, joins)
        return tbl
    }

    override fun visitRootEntity(ctx: HqlParser.RootEntityContext): TableCtx {
        val name = visitEntityName(ctx.entityName())
        val alias = visitVariable(ctx.variable())
        val tbl = TableCtx.TableRootCtx(name, alias)
        return tbl
    }

    override fun visitRootSubquery(ctx: HqlParser.RootSubqueryContext): TableCtx {
        val subquery = visitSubquery(ctx.subquery())
        val alias = visitVariable(ctx.variable())
        val tbl = TableCtx.TableSubqueryCtx(subquery, alias)
        return tbl
    }

    override fun visitEntityName(ctx: HqlParser.EntityNameContext): TableCtx.TableRootCtx.EntityNameCtx {
        val names = mutableListOf<String>()
        ctx.children.forEach{ names.add(visitIdentifier(it as HqlParser.IdentifierContext)) }
        val entName = TableCtx.TableRootCtx.EntityNameCtx(names)
        return entName
    }

    override fun visitVariable(ctx: HqlParser.VariableContext?): String? {
        if (ctx == null) return null
        val child = ctx.getChild(ctx.childCount - 1)
        return if (child is HqlParser.IdentifierContext) visitIdentifier(child)
        else visitNakedIdentifier(child as HqlParser.NakedIdentifierContext)
    }

    override fun visitCrossJoin(ctx: HqlParser.CrossJoinContext): JoinCtx.CrossJoin {
        TODO("Not yet implemented")
    }

    override fun visitJpaCollectionJoin(ctx: HqlParser.JpaCollectionJoinContext): JoinCtx.JpaCollectionJoin {
        TODO("Deprecate syntax")
    }

    override fun visitJoin(ctx: HqlParser.JoinContext): JoinCtx.Join {
        TODO("Not yet implemented")
    }

    override fun visitJoinType(ctx: HqlParser.JoinTypeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJoinPath(ctx: HqlParser.JoinPathContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJoinSubquery(ctx: HqlParser.JoinSubqueryContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJoinRestriction(ctx: HqlParser.JoinRestrictionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSelectClause(ctx: HqlParser.SelectClauseContext?): SelectFunCtx? {
        if (ctx == null) return null
        val selections = visitSelectionList(ctx.selectionList())
        val sCtx = SelectFunCtx(ctx.DISTINCT() != null, selections)
        return sCtx
    }

    override fun visitSelectionList(ctx: HqlParser.SelectionListContext): List<SelectFunCtx.SelectionCtx> {
        val selections = mutableListOf<SelectFunCtx.SelectionCtx>()
        ctx.selection().forEach { selections.add(visitSelection(it)) }
        return selections
    }

    override fun visitSelection(ctx: HqlParser.SelectionContext): SelectFunCtx.SelectionCtx {
        val alias = visitVariable(ctx.variable())
        val selection = visitSelectExpression(ctx.selectExpression())
        selection.alias = alias
        return selection
    }

    override fun visitSelectExpression(ctx: HqlParser.SelectExpressionContext): SelectFunCtx.SelectionCtx {
        return if (ctx.instantiation() != null) {
            val inst = visitInstantiation(ctx.instantiation())
            SelectFunCtx.Inst(inst, null)
        }
        else if (ctx.mapEntrySelection() != null) {
            val path = visitPath(ctx.mapEntrySelection().path())
            SelectFunCtx.Entry(path, null)
        }
        else if (ctx.expressionOrPredicate() != null) {
            val expr = visitExpressionOrPredicate(ctx.expressionOrPredicate())
            SelectFunCtx.Expr(expr, null)
        }
        else { SelectFunCtx.JpaSelect(null) } // TODO:
    }

    override fun visitMapEntrySelection(ctx: HqlParser.MapEntrySelectionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInstantiation(ctx: HqlParser.InstantiationContext): InstCtx {
        TODO("Not yet implemented")
    }

    override fun visitInstantiationTarget(ctx: HqlParser.InstantiationTargetContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInstantiationArguments(ctx: HqlParser.InstantiationArgumentsContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInstantiationArgument(ctx: HqlParser.InstantiationArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInstantiationArgumentExpression(ctx: HqlParser.InstantiationArgumentExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJpaSelectObjectSyntax(ctx: HqlParser.JpaSelectObjectSyntaxContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSimplePath(ctx: HqlParser.SimplePathContext): SimplePathCtx {
        val root = visitIdentifier(ctx.identifier())
        val cont = mutableListOf<String>()
        ctx.simplePathElement().forEach { cont.add(visitSimplePathElement(it)) }
        val path = SimplePathCtx(root, cont)
        return path
    }

    override fun visitSimplePathElement(ctx: HqlParser.SimplePathElementContext): String {
        return visitIdentifier(ctx.identifier())
    }

    override fun visitPath(ctx: HqlParser.PathContext): PathCtx {
        TODO("Not yet implemented")
    }

    override fun visitPathContinuation(ctx: HqlParser.PathContinuationContext?): SimplePathCtx? {
        if (ctx == null) return null
        return visitSimplePath(ctx.simplePath())
    }

    override fun visitSyntacticDomainPath(ctx: HqlParser.SyntacticDomainPathContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGeneralPathFragment(ctx: HqlParser.GeneralPathFragmentContext): GeneralPathCtx {
        val simplePath = visitSimplePath(ctx.simplePath())
        val indexCont = visitIndexedPathAccessFragment(ctx.indexedPathAccessFragment())
        val path = GeneralPathCtx(simplePath, indexCont)
        return path
    }

    override fun visitIndexedPathAccessFragment(
        ctx: HqlParser.IndexedPathAccessFragmentContext?
    ): GeneralPathCtx.Index? {
        if (ctx == null) return null
        val ix = visit(ctx.expression()) as ExpressionCtx
        val cont = ctx.generalPathFragment()?.let { visitGeneralPathFragment(it) }
        val index = GeneralPathCtx.Index(ix, cont)
        return index
    }

    override fun visitTreatedNavigablePath(ctx: HqlParser.TreatedNavigablePathContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCollectionValueNavigablePath(ctx: HqlParser.CollectionValueNavigablePathContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitMapKeyNavigablePath(ctx: HqlParser.MapKeyNavigablePathContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGroupByClause(ctx: HqlParser.GroupByClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGroupByExpression(ctx: HqlParser.GroupByExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitHavingClause(ctx: HqlParser.HavingClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOrderByClause(ctx: HqlParser.OrderByClauseContext): OrderCtx {
        val sorts = mutableListOf<OrderCtx.SortSpec>()
        ctx.sortSpecification().forEach { sorts.add(visitSortSpecification(it)) }
        val qCtx = OrderCtx(sorts)
        return qCtx
    }

    override fun visitOrderByFragment(ctx: HqlParser.OrderByFragmentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSortSpecification(ctx: HqlParser.SortSpecificationContext): OrderCtx.SortSpec {
        val dir = visitSortDirection(ctx.sortDirection()) ?: true
        val nulls = visitNullsPrecedence(ctx.nullsPrecedence()) ?: true
        val spec = visitSortExpression(ctx.sortExpression())
        spec.dir = dir
        spec.nulls = nulls
        return spec
    }

    override fun visitNullsPrecedence(ctx: HqlParser.NullsPrecedenceContext?): Boolean? {
        if (ctx == null) return null
        return ctx.LAST() != null
    }

    override fun visitSortExpression(ctx: HqlParser.SortExpressionContext): OrderCtx.SortSpec {
        return if (ctx.identifier() != null) {
            val ident = visitIdentifier(ctx.identifier())
            OrderCtx.SortSpec.ByIdent(ident)
        }
        else if (ctx.expression() != null) {
            val expr = visit(ctx.expression()) as ExpressionCtx
            OrderCtx.SortSpec.ByExpr(expr)
        }
        else {
            val pos = intLiteral(ctx.INTEGER_LITERAL().text)
            OrderCtx.SortSpec.ByPos(pos)
        }
    }

    override fun visitSortDirection(ctx: HqlParser.SortDirectionContext?): Boolean? {
        if (ctx == null) return null
        return ctx.ASC() != null
    }

    override fun visitCollateFunction(ctx: HqlParser.CollateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCollation(ctx: HqlParser.CollationContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLimitClause(ctx: HqlParser.LimitClauseContext?): OrderCtx.ParamOrInt? {
        if (ctx == null) return null
        return visitParameterOrIntegerLiteral(ctx.parameterOrIntegerLiteral())
    }

    override fun visitOffsetClause(ctx: HqlParser.OffsetClauseContext?): OrderCtx.ParamOrInt? {
        if (ctx == null) return null
        return visitParameterOrIntegerLiteral(ctx.parameterOrIntegerLiteral())
    }

    override fun visitFetchClause(ctx: HqlParser.FetchClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFetchCountOrPercent(ctx: HqlParser.FetchCountOrPercentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitParameterOrIntegerLiteral(ctx: HqlParser.ParameterOrIntegerLiteralContext): OrderCtx.ParamOrInt {
        return if (ctx.parameter() != null) {
            val param = visit(ctx.parameter()) as Parameter
            OrderCtx.ParamOrInt.Param(param)
        }
        else {
            val text = ctx.INTEGER_LITERAL().text
            val num = intLiteral(text)
            OrderCtx.ParamOrInt.Num(num)
        }
    }

    override fun visitParameterOrNumberLiteral(ctx: HqlParser.ParameterOrNumberLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitWhereClause(ctx: HqlParser.WhereClauseContext?): WhereCtx? {
        if (ctx == null) return null
        val pred = visit(ctx.predicate()) as PredicateCtx
        val where = WhereCtx(pred)
        return where
    }

    override fun visitIsDistinctFromPredicate(ctx: HqlParser.IsDistinctFromPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression(0)) as ExpressionCtx
        val from = visit(ctx.expression(1)) as ExpressionCtx
        val pred = PredicateCtx.IsDistinct(expr, from)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitBetweenPredicate(ctx: HqlParser.BetweenPredicateContext): PredicateCtx {
        val exprs = ctx.expression()
        val expr = visit(exprs[0]) as ExpressionCtx
        val left = visit(exprs[1]) as ExpressionCtx
        val right = visit(exprs[2]) as ExpressionCtx
        val pred = PredicateCtx.Between(expr, left, right)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitExistsPredicate(ctx: HqlParser.ExistsPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.Exist(expr)
        return pred
    }

    override fun visitAndPredicate(ctx: HqlParser.AndPredicateContext): PredicateCtx {
        val left = visit(ctx.predicate(0)) as PredicateCtx
        val right = visit(ctx.predicate(1)) as PredicateCtx
        val pred = PredicateCtx.And(left, right)
        return pred
    }

    override fun visitIsFalsePredicate(ctx: HqlParser.IsFalsePredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.IsTrue(expr)
        return if (ctx.NOT() == null) return PredicateCtx.Not(pred) else pred
    }

    override fun visitGroupedPredicate(ctx: HqlParser.GroupedPredicateContext?): PredicateCtx {
        return visitChildren(ctx) as PredicateCtx
    }

    override fun visitLikePredicate(ctx: HqlParser.LikePredicateContext): PredicateCtx {
        val expr = visit(ctx.expression(0)) as ExpressionCtx
        val pattern = visit(ctx.expression(1)) as ExpressionCtx
        val like = visitLikeEscape(ctx.likeEscape())
        val pred = PredicateCtx.Like(expr, pattern, like, ctx.LIKE() != null)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitInPredicate(ctx: HqlParser.InPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val inList = visit(ctx.inList()) as PredicateCtx.In.ListCtx
        val pred = PredicateCtx.In(expr, inList)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitComparisonPredicate(ctx: HqlParser.ComparisonPredicateContext): PredicateCtx {
        val left = visit(ctx.expression(0)) as ExpressionCtx
        val right = visit(ctx.expression(1)) as ExpressionCtx
        val comparator = visitComparisonOperator(ctx.comparisonOperator())
        val pred = PredicateCtx.Compare(left, right, comparator)
        return pred
    }

    override fun visitExistsCollectionPartPredicate(ctx: HqlParser.ExistsCollectionPartPredicateContext): PredicateCtx {
        val quant = visitCollectionQuantifier(ctx.collectionQuantifier())
        val path = visitSimplePath(ctx.simplePath())
        val pred = PredicateCtx.ExistCollection(quant, path)
        return pred
    }

    override fun visitNegatedPredicate(ctx: HqlParser.NegatedPredicateContext): PredicateCtx {
        val pred = visit(ctx.predicate()) as PredicateCtx
        val notPred = PredicateCtx.Not(pred)
        return notPred
    }

    override fun visitBooleanExpressionPredicate(ctx: HqlParser.BooleanExpressionPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.BoolExpr(expr)
        return pred
    }

    override fun visitOrPredicate(ctx: HqlParser.OrPredicateContext): PredicateCtx {
        val left = visit(ctx.predicate(0)) as PredicateCtx
        val right = visit(ctx.predicate(1)) as PredicateCtx
        val pred = PredicateCtx.Or(left, right)
        return pred
    }

    override fun visitMemberOfPredicate(ctx: HqlParser.MemberOfPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val path = visitPath(ctx.path())
        val pred = PredicateCtx.Member(expr, path)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitIsEmptyPredicate(ctx: HqlParser.IsEmptyPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.IsEmpty(expr)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitIsNullPredicate(ctx: HqlParser.IsNullPredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.IsNull(expr)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitIsTruePredicate(ctx: HqlParser.IsTruePredicateContext): PredicateCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        val pred = PredicateCtx.IsTrue(expr)
        return pred.makeNot(ctx.NOT())
    }

    override fun visitComparisonOperator(ctx: HqlParser.ComparisonOperatorContext): PredicateCtx.Compare.Operator {
        return when(ctx.getChild(0).let { it as TerminalNode }.symbol.type) {
            HqlLexer.EQUAL -> PredicateCtx.Compare.Operator.Equal
            HqlLexer.NOT_EQUAL -> PredicateCtx.Compare.Operator.NotEqual
            HqlLexer.GREATER -> PredicateCtx.Compare.Operator.Greater
            HqlLexer.GREATER_EQUAL -> PredicateCtx.Compare.Operator.GreaterEqual
            HqlLexer.LESS -> PredicateCtx.Compare.Operator.Less
            else -> PredicateCtx.Compare.Operator.LessEqual
        }
    }

    override fun visitPersistentCollectionReferenceInList(ctx: HqlParser.PersistentCollectionReferenceInListContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitExplicitTupleInList(ctx: HqlParser.ExplicitTupleInListContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSubqueryInList(ctx: HqlParser.SubqueryInListContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitParamInList(ctx: HqlParser.ParamInListContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLikeEscape(ctx: HqlParser.LikeEscapeContext?): PredicateCtx.Like.LikeCtx? {
        if (ctx == null) return null

        TODO("Not yet implemented")
    }

    override fun visitAdditionExpression(ctx: HqlParser.AdditionExpressionContext): ExpressionCtx {
        val left = visit(ctx.expression(0)) as ExpressionCtx
        val right = visit(ctx.expression(1)) as ExpressionCtx
        val op = visitAdditiveOperator(ctx.additiveOperator())
        val expr = ExpressionCtx.BinOperator(left, right, op)
        return expr
    }

    override fun visitFromDurationExpression(ctx: HqlParser.FromDurationExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitBarePrimaryExpression(ctx: HqlParser.BarePrimaryExpressionContext?): ExpressionCtx {
        return visitChildren(ctx) as ExpressionCtx
    }

    override fun visitTupleExpression(ctx: HqlParser.TupleExpressionContext): ExpressionCtx {
        val elems = mutableListOf<ExprOrPredCtx>()
        ctx.expressionOrPredicate().forEach { elems.add(visitExpressionOrPredicate(it)) }
        val expr = ExpressionCtx.Tuple(elems)
        return expr
    }

    override fun visitUnaryExpression(ctx: HqlParser.UnaryExpressionContext): ExpressionCtx {
        val expr = visit(ctx.expression()) as ExpressionCtx
        return if (visitSignOperator(ctx.signOperator())) ExpressionCtx.Minus(expr) else expr
    }

    override fun visitGroupedExpression(ctx: HqlParser.GroupedExpressionContext?): ExpressionCtx {
        return visitChildren(ctx) as ExpressionCtx
    }

    override fun visitConcatenationExpression(ctx: HqlParser.ConcatenationExpressionContext): ExpressionCtx {
        val left = visit(ctx.expression(0)) as ExpressionCtx
        val right = visit(ctx.expression(1)) as ExpressionCtx
        val op = ExpressionCtx.BinOperator.Operator.Concat
        val expr = ExpressionCtx.BinOperator(left, right, op)
        return expr
    }

    override fun visitMultiplicationExpression(ctx: HqlParser.MultiplicationExpressionContext): ExpressionCtx {
        val left = visit(ctx.expression(0)) as ExpressionCtx
        val right = visit(ctx.expression(1)) as ExpressionCtx
        val op = visitMultiplicativeOperator(ctx.multiplicativeOperator())
        val expr = ExpressionCtx.BinOperator(left, right, op)
        return expr
    }

    override fun visitToDurationExpression(ctx: HqlParser.ToDurationExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSubqueryExpression(ctx: HqlParser.SubqueryExpressionContext): ExpressionCtx {
        val query = visitSubquery(ctx.subquery())
        val expr = ExpressionCtx.Subquery(query)
        return expr
    }

    override fun visitUnaryNumericLiteralExpression(ctx: HqlParser.UnaryNumericLiteralExpressionContext): ExpressionCtx {
        val num = visitNumericLiteral(ctx.numericLiteral())
        return if (visitSignOperator(ctx.signOperator())) ExpressionCtx.Minus(num) else num
    }

    override fun visitCaseExpression(ctx: HqlParser.CaseExpressionContext?): ExpressionCtx {
        return visitChildren(ctx) as ExpressionCtx
    }

    override fun visitLiteralExpression(ctx: HqlParser.LiteralExpressionContext): ExpressionCtx {
        return visitChildren(ctx) as ExpressionCtx
    }

    override fun visitParameterExpression(ctx: HqlParser.ParameterExpressionContext): ExpressionCtx {
        val param = visit(ctx.parameter()) as Parameter
        val expr = ExpressionCtx.Param(param)
        return expr
    }

    override fun visitEntityTypeExpression(ctx: HqlParser.EntityTypeExpressionContext): ExpressionCtx {
        return visitEntityTypeReference(ctx.entityTypeReference())
    }

    override fun visitEntityIdExpression(ctx: HqlParser.EntityIdExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitEntityVersionExpression(ctx: HqlParser.EntityVersionExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitEntityNaturalIdExpression(ctx: HqlParser.EntityNaturalIdExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitToOneFkExpression(ctx: HqlParser.ToOneFkExpressionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSyntacticPathExpression(ctx: HqlParser.SyntacticPathExpressionContext): ExpressionCtx {
        return ExpressionCtx.SyntacticPath() // TODO
    }

    override fun visitFunctionExpression(ctx: HqlParser.FunctionExpressionContext): ExpressionCtx {
        val func = visitFunction(ctx.function())
        val expr = ExpressionCtx.Function(func)
        return expr
    }

    override fun visitGeneralPathExpression(ctx: HqlParser.GeneralPathExpressionContext): ExpressionCtx {
        val path = visitGeneralPathFragment(ctx.generalPathFragment())
        val expr = ExpressionCtx.GeneralPath(path)
        return expr
    }

    override fun visitExpressionOrPredicate(ctx: HqlParser.ExpressionOrPredicateContext): ExprOrPredCtx {
        return ctx.expression()?.let { visit(it) as ExpressionCtx } ?: visit(ctx.predicate()) as PredicateCtx
    }

    override fun visitCollectionQuantifier(
        ctx: HqlParser.CollectionQuantifierContext
    ): PredicateCtx.ExistCollection.ColQuantifierCtx {
        TODO("Not yet implemented")
    }

    override fun visitElementValueQuantifier(ctx: HqlParser.ElementValueQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitIndexKeyQuantifier(ctx: HqlParser.IndexKeyQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitElementsValuesQuantifier(ctx: HqlParser.ElementsValuesQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitIndicesKeysQuantifier(ctx: HqlParser.IndicesKeysQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitMultiplicativeOperator(
        ctx: HqlParser.MultiplicativeOperatorContext
    ): ExpressionCtx.BinOperator.Operator {
        val node = ctx.getChild(0) as TerminalNode
        return when (node.symbol.type) {
            HqlParser.SLASH -> ExpressionCtx.BinOperator.Operator.Slash
            HqlParser.PERCENT_OP -> ExpressionCtx.BinOperator.Operator.Percent
            else -> ExpressionCtx.BinOperator.Operator.Asterisk
        }
    }

    override fun visitAdditiveOperator(ctx: HqlParser.AdditiveOperatorContext): ExpressionCtx.BinOperator.Operator {
        val node = ctx.getChild(0) as TerminalNode
        return when (node.symbol.type) {
            HqlParser.PLUS -> ExpressionCtx.BinOperator.Operator.Plus
            else -> ExpressionCtx.BinOperator.Operator.Minus
        }
    }

    // Is Minus?
    override fun visitSignOperator(ctx: HqlParser.SignOperatorContext): Boolean {
        return ctx.MINUS() != null
    }

    override fun visitEntityTypeReference(ctx: HqlParser.EntityTypeReferenceContext): ExpressionCtx {
        return if (ctx.path() != null) {
            val path = visitPath(ctx.path())
            ExpressionCtx.TypeOfPath(path)
        }
        else {
            val param = visit(ctx.parameter()) as Parameter
            ExpressionCtx.TypeOfParam(param)
        }
    }

    override fun visitEntityIdReference(ctx: HqlParser.EntityIdReferenceContext): ExpressionCtx {
        val path = visitPath(ctx.path())
        val cont = visitPathContinuation(ctx.pathContinuation())
        val expr = ExpressionCtx.Id(path, cont)
        return expr
    }

    override fun visitEntityVersionReference(ctx: HqlParser.EntityVersionReferenceContext): ExpressionCtx {
        val path = visitPath(ctx.path())
        val expr = ExpressionCtx.Version(path)
        return expr
    }

    override fun visitEntityNaturalIdReference(ctx: HqlParser.EntityNaturalIdReferenceContext): ExpressionCtx {
        val path = visitPath(ctx.path())
        val cont = visitPathContinuation(ctx.pathContinuation())
        val expr = ExpressionCtx.NaturalId(path, cont)
        return expr
    }

    override fun visitToOneFkReference(ctx: HqlParser.ToOneFkReferenceContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCaseList(ctx: HqlParser.CaseListContext): ExpressionCtx {
        return ctx.simpleCaseList()?.let { visitSimpleCaseList(it) }
            ?: visitSearchedCaseList(ctx.searchedCaseList())
    }

    override fun visitSimpleCaseList(ctx: HqlParser.SimpleCaseListContext): ExpressionCtx {
        val caseValue = visitExpressionOrPredicate(ctx.expressionOrPredicate())
        val branches = mutableListOf<ExpressionCtx.SimpleCaseList.BranchCtx>()
        ctx.simpleCaseWhen().forEach { branches.add(visitSimpleCaseWhen(it)) }
        val elseValue = visitCaseOtherwise(ctx.caseOtherwise())
        val expr = ExpressionCtx.SimpleCaseList(caseValue, branches, elseValue)
        return expr
    }

    override fun visitSimpleCaseWhen(ctx: HqlParser.SimpleCaseWhenContext): ExpressionCtx.SimpleCaseList.BranchCtx {
        val pattern = visit(ctx.expression()) as ExpressionCtx
        val value = visitExpressionOrPredicate(ctx.expressionOrPredicate())
        val branch = ExpressionCtx.SimpleCaseList.BranchCtx(pattern, value)
        return branch
    }

    override fun visitCaseOtherwise(ctx: HqlParser.CaseOtherwiseContext): ExprOrPredCtx {
        return visitExpressionOrPredicate(ctx.expressionOrPredicate())
    }

    override fun visitSearchedCaseList(ctx: HqlParser.SearchedCaseListContext): ExpressionCtx.CaseList {
        val branches = mutableListOf<ExpressionCtx.CaseList.BranchCtx>()
        ctx.searchedCaseWhen().forEach { branches.add(visitSearchedCaseWhen(it)) }
        val elseValue = visitCaseOtherwise(ctx.caseOtherwise())
        val expr = ExpressionCtx.CaseList(branches, elseValue)
        return expr
    }

    override fun visitSearchedCaseWhen(ctx: HqlParser.SearchedCaseWhenContext): ExpressionCtx.CaseList.BranchCtx {
        val pattern = visit(ctx.predicate()) as PredicateCtx
        val value = visitExpressionOrPredicate(ctx.expressionOrPredicate())
        val branch = ExpressionCtx.CaseList.BranchCtx(pattern, value)
        return branch
    }

    override fun visitLiteral(ctx: HqlParser.LiteralContext): ExpressionCtx {
        val node = ctx.getChild(0)
        if (node !is TerminalNode) return visit(node) as ExpressionCtx
        return when (node.symbol.type) {
            HqlParser.STRING_LITERAL -> ExpressionCtx.LString(unquoteStringLiteral(node.text))
            HqlParser.JAVA_STRING_LITERAL -> ExpressionCtx.LString(unquoteJavaStringLiteral(node.text))
            else -> ExpressionCtx.LNull()
        }
    }

    override fun visitBooleanLiteral(ctx: HqlParser.BooleanLiteralContext): ExpressionCtx {
        return ExpressionCtx.LBool(ctx.TRUE() != null)
    }

    fun intLiteral(text : String) : Int {
        return text.replace("_", "").toInt()
    }

    fun longLiteral(text : String) : Long {
        return text.substring(0, text.length - 1).replace("_", "").toLong()
    }

    fun bigIntLiteral(text : String) : String {
        return text.substring(0, text.length - 2).replace("_", "")
    }

    fun floatLiteral(text : String) : Float {
        return text.substring(0, text.length - 1).replace("_", "").toFloat()
    }

    fun doubleLiteral(text : String) : Double {
        return text.substring(0, text.length - 1).replace("_", "").toDouble()
    }

    fun bigDecimalLiteral(text : String) : String {
        return text.substring(0, text.length - 2).replace("_", "")
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun hexLongLiteral(text : String) : Long {
        return text.substring(2, text.length - 1).replace("_", "").hexToLong()
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun hexIntLiteral(text : String) : Int {
        return text.substring(2, text.length).replace("_", "").hexToInt()
    }

    override fun visitNumericLiteral(ctx: HqlParser.NumericLiteralContext): ExpressionCtx {
        val node = ctx.getChild(0) as TerminalNode
        val text = node.text
        return when(node.symbol.type) {
            HqlParser.INTEGER_LITERAL -> ExpressionCtx.LInt(intLiteral(text))
            HqlParser.LONG_LITERAL -> ExpressionCtx.LLong(longLiteral(text))
            HqlParser.BIG_INTEGER_LITERAL -> ExpressionCtx.LBigInt(bigIntLiteral(text))
            HqlParser.FLOAT_LITERAL -> ExpressionCtx.LFloat(floatLiteral(text))
            HqlParser.DOUBLE_LITERAL -> ExpressionCtx.LDouble(doubleLiteral(text))
            HqlParser.BIG_DECIMAL_LITERAL -> ExpressionCtx.LBigDecimal(bigDecimalLiteral(text))
            else -> {
                if (text.endsWith("l")) ExpressionCtx.LLong(hexLongLiteral(text))
                else ExpressionCtx.LInt(hexIntLiteral(text))
            }
        }
    }

    override fun visitBinaryLiteral(ctx: HqlParser.BinaryLiteralContext): ExpressionCtx {
        val node = ctx.getChild(0) as TerminalNode
        val nodeText = node.text
        val text =  when(node.symbol.type) {
            HqlParser.BINARY_LITERAL -> nodeText.substring(2, nodeText.length - 1)
            else -> ctx.children.joinToString { it.text.substring(2, 4) }
        }
        val arr = PrimitiveByteArrayJavaType.INSTANCE.fromString(text)
        val expr = ExpressionCtx.LBinary(arr)
        return expr
    }

    override fun visitTemporalLiteral(ctx: HqlParser.TemporalLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDateTimeLiteral(ctx: HqlParser.DateTimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLocalDateTimeLiteral(ctx: HqlParser.LocalDateTimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitZonedDateTimeLiteral(ctx: HqlParser.ZonedDateTimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffsetDateTimeLiteral(ctx: HqlParser.OffsetDateTimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDateLiteral(ctx: HqlParser.DateLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTimeLiteral(ctx: HqlParser.TimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDateTime(ctx: HqlParser.DateTimeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLocalDateTime(ctx: HqlParser.LocalDateTimeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitZonedDateTime(ctx: HqlParser.ZonedDateTimeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffsetDateTime(ctx: HqlParser.OffsetDateTimeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffsetDateTimeWithMinutes(ctx: HqlParser.OffsetDateTimeWithMinutesContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDate(ctx: HqlParser.DateContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTime(ctx: HqlParser.TimeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffset(ctx: HqlParser.OffsetContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffsetWithMinutes(ctx: HqlParser.OffsetWithMinutesContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitYear(ctx: HqlParser.YearContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitMonth(ctx: HqlParser.MonthContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDay(ctx: HqlParser.DayContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitHour(ctx: HqlParser.HourContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitMinute(ctx: HqlParser.MinuteContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSecond(ctx: HqlParser.SecondContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitZoneId(ctx: HqlParser.ZoneIdContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJdbcTimestampLiteral(ctx: HqlParser.JdbcTimestampLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJdbcDateLiteral(ctx: HqlParser.JdbcDateLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJdbcTimeLiteral(ctx: HqlParser.JdbcTimeLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGenericTemporalLiteralText(ctx: HqlParser.GenericTemporalLiteralTextContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGeneralizedLiteral(ctx: HqlParser.GeneralizedLiteralContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGeneralizedLiteralType(ctx: HqlParser.GeneralizedLiteralTypeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGeneralizedLiteralText(ctx: HqlParser.GeneralizedLiteralTextContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitNamedParameter(ctx: HqlParser.NamedParameterContext): Parameter {
        val ident = visitIdentifier(ctx.identifier())
        val param = Parameter.Colon(ident)
        return param
    }

    override fun visitPositionalParameter(ctx: HqlParser.PositionalParameterContext): Parameter {
        val pos = intLiteral(ctx.INTEGER_LITERAL().text) // just '?' is deprecated
        val param = Parameter.Positional(pos)
        return param
    }

    override fun visitFunction(ctx: HqlParser.FunctionContext): FunctionCtx {
        TODO("Not yet implemented")
    }

    override fun visitJpaNonstandardFunction(ctx: HqlParser.JpaNonstandardFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitJpaNonstandardFunctionName(ctx: HqlParser.JpaNonstandardFunctionNameContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGenericFunction(ctx: HqlParser.GenericFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGenericFunctionName(ctx: HqlParser.GenericFunctionNameContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitGenericFunctionArguments(ctx: HqlParser.GenericFunctionArgumentsContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCollectionSizeFunction(ctx: HqlParser.CollectionSizeFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitElementAggregateFunction(ctx: HqlParser.ElementAggregateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitIndexAggregateFunction(ctx: HqlParser.IndexAggregateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCollectionFunctionMisuse(ctx: HqlParser.CollectionFunctionMisuseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitAggregateFunction(ctx: HqlParser.AggregateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitEveryFunction(ctx: HqlParser.EveryFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitAnyFunction(ctx: HqlParser.AnyFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitEveryAllQuantifier(ctx: HqlParser.EveryAllQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitAnySomeQuantifier(ctx: HqlParser.AnySomeQuantifierContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitListaggFunction(ctx: HqlParser.ListaggFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOnOverflowClause(ctx: HqlParser.OnOverflowClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitWithinGroupClause(ctx: HqlParser.WithinGroupClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFilterClause(ctx: HqlParser.FilterClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitNullsClause(ctx: HqlParser.NullsClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitNthSideClause(ctx: HqlParser.NthSideClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverClause(ctx: HqlParser.OverClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPartitionClause(ctx: HqlParser.PartitionClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFrameClause(ctx: HqlParser.FrameClauseContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFrameStart(ctx: HqlParser.FrameStartContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFrameEnd(ctx: HqlParser.FrameEndContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFrameExclusion(ctx: HqlParser.FrameExclusionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitStandardFunction(ctx: HqlParser.StandardFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCastFunction(ctx: HqlParser.CastFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCastTarget(ctx: HqlParser.CastTargetContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCastTargetType(ctx: HqlParser.CastTargetTypeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSubstringFunction(ctx: HqlParser.SubstringFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSubstringFunctionStartArgument(ctx: HqlParser.SubstringFunctionStartArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitSubstringFunctionLengthArgument(ctx: HqlParser.SubstringFunctionLengthArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTrimFunction(ctx: HqlParser.TrimFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTrimSpecification(ctx: HqlParser.TrimSpecificationContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTrimCharacter(ctx: HqlParser.TrimCharacterContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPadFunction(ctx: HqlParser.PadFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPadSpecification(ctx: HqlParser.PadSpecificationContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPadCharacter(ctx: HqlParser.PadCharacterContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPadLength(ctx: HqlParser.PadLengthContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverlayFunction(ctx: HqlParser.OverlayFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverlayFunctionStringArgument(ctx: HqlParser.OverlayFunctionStringArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverlayFunctionReplacementArgument(ctx: HqlParser.OverlayFunctionReplacementArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverlayFunctionStartArgument(ctx: HqlParser.OverlayFunctionStartArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOverlayFunctionLengthArgument(ctx: HqlParser.OverlayFunctionLengthArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCurrentDateFunction(ctx: HqlParser.CurrentDateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCurrentTimeFunction(ctx: HqlParser.CurrentTimeFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCurrentTimestampFunction(ctx: HqlParser.CurrentTimestampFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitInstantFunction(ctx: HqlParser.InstantFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLocalDateTimeFunction(ctx: HqlParser.LocalDateTimeFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitOffsetDateTimeFunction(ctx: HqlParser.OffsetDateTimeFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLocalDateFunction(ctx: HqlParser.LocalDateFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitLocalTimeFunction(ctx: HqlParser.LocalTimeFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFormatFunction(ctx: HqlParser.FormatFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitFormat(ctx: HqlParser.FormatContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitExtractFunction(ctx: HqlParser.ExtractFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTruncFunction(ctx: HqlParser.TruncFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitExtractField(ctx: HqlParser.ExtractFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDatetimeField(ctx: HqlParser.DatetimeFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDayField(ctx: HqlParser.DayFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitWeekField(ctx: HqlParser.WeekFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitTimeZoneField(ctx: HqlParser.TimeZoneFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitDateOrTimeField(ctx: HqlParser.DateOrTimeFieldContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPositionFunction(ctx: HqlParser.PositionFunctionContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPositionFunctionPatternArgument(ctx: HqlParser.PositionFunctionPatternArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitPositionFunctionStringArgument(ctx: HqlParser.PositionFunctionStringArgumentContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitCube(ctx: HqlParser.CubeContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitRollup(ctx: HqlParser.RollupContext?): Any {
        TODO("Not yet implemented")
    }

    override fun visitNakedIdentifier(ctx: HqlParser.NakedIdentifierContext?): String {
        val child = ctx!!.getChild(0) as TerminalNode
        return if (child.symbol.type == HqlParser.QUOTED_IDENTIFIER) QuotingHelper.unquoteIdentifier(child.text)
        else child.text
    }

    override fun visitIdentifier(ctx: HqlParser.IdentifierContext?): String {
        val child = ctx!!.getChild(0)
        return if (child is TerminalNode) child.text
        else visitNakedIdentifier(child as HqlParser.NakedIdentifierContext)
    }

}
