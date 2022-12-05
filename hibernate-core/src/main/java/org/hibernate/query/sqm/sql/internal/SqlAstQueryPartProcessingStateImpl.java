/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.query.sqm.sql.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.hibernate.sql.ast.Clause;
import org.hibernate.sql.ast.spi.SqlAstCreationState;
import org.hibernate.sql.ast.spi.SqlAstProcessingState;
import org.hibernate.sql.ast.spi.SqlAstQueryPartProcessingState;
import org.hibernate.sql.ast.spi.SqlExpressionResolver;
import org.hibernate.sql.ast.spi.SqlSelection;
import org.hibernate.sql.ast.tree.expression.Expression;
import org.hibernate.sql.ast.tree.select.QueryPart;
import org.hibernate.sql.ast.tree.select.QuerySpec;
import org.hibernate.sql.ast.tree.select.SelectClause;
import org.hibernate.sql.results.graph.FetchParent;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * @author Steve Ebersole
 */
public class SqlAstQueryPartProcessingStateImpl
		extends SqlAstProcessingStateImpl
		implements SqlAstQueryPartProcessingState {

	private final QueryPart queryPart;
	private final boolean deduplicateSelectionItems;

	public SqlAstQueryPartProcessingStateImpl(
			QueryPart queryPart,
			SqlAstProcessingState parent,
			SqlAstCreationState creationState,
			Supplier<Clause> currentClauseAccess,
			boolean deduplicateSelectionItems) {
		super( parent, creationState, currentClauseAccess );
		this.queryPart = queryPart;
		this.deduplicateSelectionItems = deduplicateSelectionItems;
	}

	public SqlAstQueryPartProcessingStateImpl(
			QueryPart queryPart,
			SqlAstProcessingState parent,
			SqlAstCreationState creationState,
			Function<SqlExpressionResolver, SqlExpressionResolver> expressionResolverDecorator,
			Supplier<Clause> currentClauseAccess,
			boolean deduplicateSelectionItems) {
		super( parent, creationState, expressionResolverDecorator, currentClauseAccess );
		this.queryPart = queryPart;
		this.deduplicateSelectionItems = deduplicateSelectionItems;
	}

	@Override
	public QueryPart getInflightQueryPart() {
		return queryPart;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SqlExpressionResolver

	private Map<?, ?> sqlSelectionMap;

	@Override
	public SqlSelection resolveSqlSelection(
			Expression expression,
			JavaType<?> javaType,
			FetchParent fetchParent,
			TypeConfiguration typeConfiguration) {
		final Map<Expression, SqlSelection> selectionMap;
		if ( deduplicateSelectionItems ) {
			final SqlSelection existing;
			if ( sqlSelectionMap == null ) {
				sqlSelectionMap = new HashMap<>();
				existing = null;
			}
			else {
				existing = (SqlSelection) sqlSelectionMap.get( expression );
			}

			if ( existing != null ) {
				return existing;
			}
			//noinspection unchecked
			selectionMap = (Map<Expression, SqlSelection>) sqlSelectionMap;
		}
		else if ( fetchParent != null ) {
			// De-duplicate selection items within the root of a fetch parent
			final Map<FetchParent, Map<Expression, SqlSelection>> fetchParentSqlSelectionMap;
			final FetchParent root = fetchParent.getRoot();
			if ( sqlSelectionMap == null ) {
				sqlSelectionMap = fetchParentSqlSelectionMap = new HashMap<>();
				fetchParentSqlSelectionMap.put( root, selectionMap = new HashMap<>() );
			}
			else {
				//noinspection unchecked
				fetchParentSqlSelectionMap = (Map<FetchParent, Map<Expression, SqlSelection>>) sqlSelectionMap;
				final Map<Expression, SqlSelection> map = fetchParentSqlSelectionMap.get( root );
				if ( map == null ) {
					fetchParentSqlSelectionMap.put( root, selectionMap = new HashMap<>() );
				}
				else {
					selectionMap = map;
				}
			}
			final SqlSelection sqlSelection = selectionMap.get( expression );
			if ( sqlSelection != null ) {
				return sqlSelection;
			}
		}
		else {
			selectionMap = null;
		}

		final SelectClause selectClause = ( (QuerySpec) queryPart ).getSelectClause();
		final int valuesArrayPosition = selectClause.getSqlSelections().size();
		final SqlSelection sqlSelection = expression.createSqlSelection(
				valuesArrayPosition + 1,
				valuesArrayPosition,
				typeConfiguration
		);

		selectClause.addSqlSelection( sqlSelection );

		if ( selectionMap != null ) {
			selectionMap.put( expression, sqlSelection );
		}

		return sqlSelection;
	}
}