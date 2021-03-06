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

package org.teiid.translator.simpledb;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import javax.resource.cci.ConnectionFactory;

import org.teiid.language.Command;
import org.teiid.language.Delete;
import org.teiid.language.DerivedColumn;
import org.teiid.language.Insert;
import org.teiid.language.QueryExpression;
import org.teiid.language.Select;
import org.teiid.language.Update;
import org.teiid.metadata.BaseColumn.NullType;
import org.teiid.metadata.Column;
import org.teiid.metadata.Datatype;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.metadata.Table;
import org.teiid.resource.adpter.simpledb.SimpleDBConnection;
import org.teiid.translator.ExecutionContext;
import org.teiid.translator.ExecutionFactory;
import org.teiid.translator.ResultSetExecution;
import org.teiid.translator.Translator;
import org.teiid.translator.TranslatorException;
import org.teiid.translator.UpdateExecution;
import org.teiid.translator.simpledb.executors.SimpleDBDeleteExecute;
import org.teiid.translator.simpledb.executors.SimpleDBInsertExecute;
import org.teiid.translator.simpledb.executors.SimpleDBUpdateExecute;
import org.teiid.translator.simpledb.visitors.SimpleDBSQLVisitor;

@Translator(name = "simpledb", description = "Translator for SimpleDB")
public class SimpleDBExecutionFactory extends ExecutionFactory<ConnectionFactory, SimpleDBConnection> {

	@Override
	public UpdateExecution createUpdateExecution(final Command command, ExecutionContext executionContext,
			RuntimeMetadata metadata, final SimpleDBConnection connection) throws TranslatorException {
		if (command instanceof Insert) {
			return new SimpleDBInsertExecute(command, connection);
		} else if (command instanceof Delete) {
			return new SimpleDBDeleteExecute(command, connection);
		} else if (command instanceof Update) {
			return new SimpleDBUpdateExecute(command, connection);
		} else {
			throw new TranslatorException("Just INSERT, DELETE and UPDATE are supported"); //$NON-NLS-1$
		}
	}

	@Override
	public ResultSetExecution createResultSetExecution(final QueryExpression command,
			ExecutionContext executionContext, RuntimeMetadata metadata, final SimpleDBConnection connection)
			throws TranslatorException {
		return new ResultSetExecution() {
			Iterator<List<String>> listIterator;

			@Override
			public void execute() throws TranslatorException {
				List<String> columns = new ArrayList<String>();
				for (DerivedColumn column : ((Select) command).getDerivedColumns()) {
					columns.add(SimpleDBSQLVisitor.getSQLString(column));
				}
				listIterator = connection.getAPIClass().performSelect(SimpleDBSQLVisitor.getSQLString(command),
						columns);

			}

			@Override
			public void close() {

			}

			@Override
			public void cancel() throws TranslatorException {

			}

			@Override
			public List<?> next() {
				try {
					return listIterator.next();
				} catch (NoSuchElementException ex) {
					return null;
				}
			}
		};
	}

	/**
	 * As SimpleDB does not provide any way to obtain all attribute names for
	 * given domain (one can query only attribute names for single item) and
	 * querrying all items in domain to get complete set of attribute names
	 * would be very slow and resource consuming, this approach has been
	 * selected: For each domain only firt item is queried for attribute names
	 * and metadata are created using this information. Thus first item in
	 * domain should have as much attributes as possible.
	 */

	@Override
	public void getMetadata(MetadataFactory metadataFactory, SimpleDBConnection conn) throws TranslatorException {
		List<String> domains = conn.getAPIClass().getDomains();
		for (String domain : domains) {
			Table table = metadataFactory.addTable(domain);
			table.setSupportsUpdate(true);
			Column itemName = new Column();
			itemName.setName("itemName()"); //$NON-NLS-1$
			itemName.setUpdatable(true);
			itemName.setNullType(NullType.No_Nulls);
			Map<String, Datatype> datatypes = metadataFactory.getDataTypes();
			itemName.setDatatype(datatypes.get("String")); //$NON-NLS-1$
			table.addColumn(itemName);
			for (String attributeName : conn.getAPIClass().getAttributeNames(domain)) {
				Column column = new Column();
				column.setUpdatable(true);
				column.setName(attributeName);
				column.setNullType(NullType.Nullable);
				column.setDatatype(datatypes.get("String")); //$NON-NLS-1$
				table.addColumn(column);
			}
		}
	}

	@Override
	public boolean supportsCompareCriteriaEquals() {
		return true;
	}

	@Override
	public boolean supportsCompareCriteriaOrdered() {
		return true;
	}

	@Override
	public boolean supportsInCriteria() {
		return true;
	}

	@Override
	public boolean supportsIsNullCriteria() {
		return true;
	}

	@Override
	public boolean supportsRowLimit() {
		return true;
	}

	@Override
	public boolean supportsNotCriteria() {
		return true;
	}

	@Override
	public boolean supportsOrCriteria() {
		return true;
	}

	@Override
	public boolean supportsLikeCriteria() {
		return true;
	}
}
