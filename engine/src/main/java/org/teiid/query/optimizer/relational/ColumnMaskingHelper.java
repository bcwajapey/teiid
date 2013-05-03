/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.optimizer.relational;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.teiid.adminapi.DataPolicy;
import org.teiid.adminapi.impl.DataPolicyMetadata;
import org.teiid.adminapi.impl.DataPolicyMetadata.PermissionMetaData;
import org.teiid.api.exception.query.QueryMetadataException;
import org.teiid.core.TeiidComponentException;
import org.teiid.core.TeiidException;
import org.teiid.core.TeiidProcessingException;
import org.teiid.metadata.FunctionMethod.Determinism;
import org.teiid.query.QueryPlugin;
import org.teiid.query.metadata.QueryMetadataInterface;
import org.teiid.query.parser.QueryParser;
import org.teiid.query.resolver.util.ResolverVisitor;
import org.teiid.query.rewriter.QueryRewriter;
import org.teiid.query.sql.navigator.PreOrPostOrderNavigator;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.Expression;
import org.teiid.query.sql.symbol.GroupSymbol;
import org.teiid.query.sql.visitor.ExpressionMappingVisitor;
import org.teiid.query.util.CommandContext;
import org.teiid.query.validator.ValidationVisitor;
import org.teiid.query.validator.Validator;
import org.teiid.query.validator.ValidatorFailure;
import org.teiid.query.validator.ValidatorReport;

public class ColumnMaskingHelper {

	private static Expression maskColumn(ElementSymbol col, GroupSymbol unaliased, QueryMetadataInterface metadata, ExpressionMappingVisitor emv, Map<String, DataPolicy> policies, CommandContext cc) throws TeiidComponentException, TeiidProcessingException {
		Object metadataID = col.getMetadataID();
		String fullName = metadata.getFullName(metadataID);
		PermissionMetaData effective = null;
		DataPolicyMetadata effectivePolicy = null;
		for (Map.Entry<String, DataPolicy> entry : policies.entrySet()) {
			DataPolicyMetadata dpm = (DataPolicyMetadata)entry.getValue();
			PermissionMetaData pmd = dpm.getPermissionMap().get(fullName);
			if (pmd == null) {
				continue;
			}
			String maskString = pmd.getMask();
			if (maskString == null) {
				continue;
			}
			if (effective == null || pmd.getOrder() < effective.getOrder()) {
				effective = pmd;
				effectivePolicy = dpm;
			}
		}
		if (effective == null) {
			return col;
		}
		final GroupSymbol group = col.getGroupSymbol();
		Expression mask = (Expression)effective.getResolvedMask();
		if (mask == null) {
			try {
				mask = QueryParser.getQueryParser().parseExpression(effective.getMask());
				ResolverVisitor.resolveLanguageObject(mask, Arrays.asList(unaliased), metadata);
                ValidatorReport report = Validator.validate(mask, metadata, new ValidationVisitor());
		        if (report.hasItems()) {
		        	ValidatorFailure firstFailure = report.getItems().iterator().next();
		        	throw new QueryMetadataException(QueryPlugin.Event.TEIID31139, QueryPlugin.Util.gs(QueryPlugin.Event.TEIID31139, effectivePolicy.getName(), fullName) + " " + firstFailure); //$NON-NLS-1$
			    }
				effective.setResolvedMask(mask.clone());
			} catch (QueryMetadataException e) {
				throw e;
			} catch (TeiidException e) {
				throw new QueryMetadataException(QueryPlugin.Event.TEIID31129, e, QueryPlugin.Util.gs(QueryPlugin.Event.TEIID31129, effectivePolicy.getName(), fullName));
			}
		} else {
			mask = (Expression) mask.clone();
		}
		if (group.getDefinition() != null) {
	        PreOrPostOrderNavigator.doVisit(mask, emv, PreOrPostOrderNavigator.PRE_ORDER, true);
		}
		if (!effectivePolicy.isAnyAuthenticated()) {
			//we treat this as user deterministic since the data roles won't change.  this may change if the logic becomes dynamic 
			cc.setDeterminismLevel(Determinism.USER_DETERMINISTIC);
		}
		mask = QueryRewriter.rewriteExpression(mask, cc, metadata);
		return mask;
	}

	public static List<? extends Expression> maskColumns(List<ElementSymbol> cols,
			final GroupSymbol group, QueryMetadataInterface metadata,
			CommandContext cc) throws QueryMetadataException, TeiidComponentException, TeiidProcessingException {
		Map<String, DataPolicy> policies = cc.getAllowedDataPolicies();
		if (policies == null || policies.isEmpty()) {
			return cols;
		}

		ArrayList<Expression> result = new ArrayList<Expression>(cols.size());
		ExpressionMappingVisitor emv = new ExpressionMappingVisitor(null) {
			@Override
			public Expression replaceExpression(
					Expression element) {
				if (element instanceof ElementSymbol) {
					ElementSymbol es = (ElementSymbol)element;
					if (es.getGroupSymbol().getDefinition() == null && es.getGroupSymbol().getName().equalsIgnoreCase(group.getDefinition())) {
						es.getGroupSymbol().setDefinition(group.getDefinition());
						es.getGroupSymbol().setName(group.getName());            						}
				}
				return element;
			}
		};
		GroupSymbol gs = group;
		if (gs.getDefinition() != null) {
			gs = new GroupSymbol(metadata.getFullName(group.getMetadataID()));
			gs.setMetadataID(group.getMetadataID());
		}
        for (int i = 0; i < cols.size(); i++) {
        	result.add(maskColumn(cols.get(i), gs, metadata, emv, policies, cc));
        }
        return result;
	}
	
}