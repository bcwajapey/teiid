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
package org.teiid.translator.odata;

import java.io.InputStreamReader;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.*;

import javax.resource.cci.ConnectionFactory;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.odata4j.edm.EdmDataServices;
import org.odata4j.format.FormatType;
import org.odata4j.format.xml.EdmxFormatParser;
import org.odata4j.stax2.util.StaxUtil;
import org.teiid.core.util.PropertiesUtils;
import org.teiid.language.Call;
import org.teiid.language.Command;
import org.teiid.language.QueryExpression;
import org.teiid.language.visitor.SQLStringVisitor;
import org.teiid.metadata.MetadataFactory;
import org.teiid.metadata.RuntimeMetadata;
import org.teiid.translator.*;
import org.teiid.translator.jdbc.AliasModifier;
import org.teiid.translator.jdbc.FunctionModifier;
import org.teiid.translator.ws.BinaryWSProcedureExecution;

/**
 * TODO:
 * Type comparison	isof(T), isof(x, T)	Whether targeted instance can be converted to the specified type.
 * media streams are generally not supported yet. (blobs, clobs)
 */
@Translator(name="odata", description="A translator for making OData data service calls")
public class ODataExecutionFactory extends ExecutionFactory<ConnectionFactory, WSConnection> {
    
    public final static TimeZone DEFAULT_TIME_ZONE = TimeZone.getDefault();

	static final String INVOKE_HTTP = "invokeHttp"; //$NON-NLS-1$
	protected Map<String, FunctionModifier> functionModifiers = new TreeMap<String, FunctionModifier>(String.CASE_INSENSITIVE_ORDER);
	private EdmDataServices eds;
	private String databaseTimeZone;
	private TimeZone timeZone = DEFAULT_TIME_ZONE;
	
	public ODataExecutionFactory() {
		setSourceRequiredForMetadata(true);
		setSupportsOrderBy(true);
		
		registerFunctionModifier(SourceSystemFunctions.CONVERT, new AliasModifier("cast")); //$NON-NLS-1$
		registerFunctionModifier(SourceSystemFunctions.LOCATE, new AliasModifier("indexof")); //$NON-NLS-1$
		registerFunctionModifier(SourceSystemFunctions.LCASE, new AliasModifier("tolower")); //$NON-NLS-1$
		registerFunctionModifier(SourceSystemFunctions.UCASE, new AliasModifier("toupper")); //$NON-NLS-1$
		registerFunctionModifier(SourceSystemFunctions.DAYOFMONTH, new AliasModifier("day")); //$NON-NLS-1$
		addPushDownFunction("odata", "startswith", TypeFacility.RUNTIME_NAMES.BOOLEAN, TypeFacility.RUNTIME_NAMES.STRING, TypeFacility.RUNTIME_NAMES.STRING); //$NON-NLS-1$ //$NON-NLS-2$
		addPushDownFunction("odata", "substringof", TypeFacility.RUNTIME_NAMES.BOOLEAN, TypeFacility.RUNTIME_NAMES.STRING, TypeFacility.RUNTIME_NAMES.STRING); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	@Override
	public void start() throws TranslatorException {
		super.start();		
		if(this.databaseTimeZone != null && this.databaseTimeZone.trim().length() > 0) {
        	TimeZone tz = TimeZone.getTimeZone(this.databaseTimeZone);
            if(!DEFAULT_TIME_ZONE.hasSameRules(tz)) {
        		timeZone = tz;;
            }
        }  
    }	
	
	@TranslatorProperty(display="Database time zone", description="Time zone of the database, if different than Integration Server", advanced=true)
	public String getDatabaseTimeZone() {
		return this.databaseTimeZone;
	}

	public void setDatabaseTimeZone(String databaseTimeZone) {
		this.databaseTimeZone = databaseTimeZone;
	}	
	
	@Override
	public void getMetadata(MetadataFactory metadataFactory, WSConnection conn) throws TranslatorException {
		BaseQueryExecution execution = new BaseQueryExecution(this, null, null, conn, null);
		BinaryWSProcedureExecution call = execution.executeDirect("GET", "$metadata", null, FormatType.ATOM.getAcceptableMediaTypes()); //$NON-NLS-1$ //$NON-NLS-2$
		if (call.getResponseCode() != Status.OK.getStatusCode()) {
			throw execution.buildError(call);
		}	
		
		Blob out = (Blob)call.getOutputParameterValues().get(0);
		
		try {
			this.eds = new EdmxFormatParser().parseMetadata(StaxUtil.newXMLEventReader(new InputStreamReader(out.getBinaryStream())));
			ODataMetadataProcessor metadataProcessor = new ODataMetadataProcessor();
			PropertiesUtils.setBeanProperties(metadataProcessor, metadataFactory.getModelProperties(), "importer"); //$NON-NLS-1$
			metadataProcessor.getMetadata(metadataFactory, eds);
		} catch (SQLException e) {
			throw new TranslatorException(e, e.getMessage());
		}
	}
	

	@Override
	public ResultSetExecution createResultSetExecution(QueryExpression command, ExecutionContext executionContext, RuntimeMetadata metadata, WSConnection connection) throws TranslatorException {
		return new ODataQueryExecution(this, command, executionContext, metadata, connection, this.eds);
	}

	@Override
	public ProcedureExecution createProcedureExecution(Call command, ExecutionContext executionContext, RuntimeMetadata metadata, WSConnection connection) throws TranslatorException {
		String nativeQuery = command.getMetadataObject().getProperty(SQLStringVisitor.TEIID_NATIVE_QUERY, false);
		if (nativeQuery != null) {
			throw new TranslatorException(ODataPlugin.Util.gs(ODataPlugin.Event.TEIID17014)); 
		}
		return new ODataProcedureExecution(command, this, executionContext, metadata, connection, this.eds);
	}

	@Override
	public UpdateExecution createUpdateExecution(Command command, ExecutionContext executionContext, RuntimeMetadata metadata, WSConnection connection) throws TranslatorException {
		return new ODataUpdateExecution(command, this, executionContext, metadata, connection, this.eds);
	}
	
	@Override
	public List<String> getSupportedFunctions() {
        List<String> supportedFunctions = new ArrayList<String>();
        supportedFunctions.addAll(getDefaultSupportedFunctions());

        // String functions
        supportedFunctions.add(SourceSystemFunctions.ENDSWITH); 
        supportedFunctions.add(SourceSystemFunctions.REPLACE); 
        supportedFunctions.add(SourceSystemFunctions.TRIM);
        supportedFunctions.add(SourceSystemFunctions.SUBSTRING);
        supportedFunctions.add(SourceSystemFunctions.CONCAT);
        supportedFunctions.add(SourceSystemFunctions.LENGTH);
        
        // date functions
        supportedFunctions.add(SourceSystemFunctions.YEAR);
        supportedFunctions.add(SourceSystemFunctions.MONTH);
        supportedFunctions.add(SourceSystemFunctions.HOUR);
        supportedFunctions.add(SourceSystemFunctions.MINUTE);
        supportedFunctions.add(SourceSystemFunctions.SECOND);
        
        // airthamatic functions
        supportedFunctions.add(SourceSystemFunctions.ROUND);
        supportedFunctions.add(SourceSystemFunctions.FLOOR);
        supportedFunctions.add(SourceSystemFunctions.CEILING);
        
        return supportedFunctions;
	}

    /**
     * Return a map of function name to FunctionModifier.
     * @return Map of function name to FunctionModifier.
     */
    public Map<String, FunctionModifier> getFunctionModifiers() {
    	return functionModifiers;
    }
    
    /**
     * Add the {@link FunctionModifier} to the set of known modifiers.
     * @param name
     * @param modifier
     */
    public void registerFunctionModifier(String name, FunctionModifier modifier) {
    	this.functionModifiers.put(name, modifier);
    }	
	
	
	public List<String> getDefaultSupportedFunctions(){
		return Arrays.asList(new String[] { "+", "-", "*", "/" }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
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
    public boolean supportsIsNullCriteria() {
    	return true;
    }

	@Override
	public boolean supportsOrCriteria() {
    	return true;
    }

	@Override
    public boolean supportsNotCriteria() {
    	return true;
    }

	@Override
    public boolean supportsQuantifiedCompareCriteriaSome() {
    	return false; // TODO:for ANY
    }

	@Override
    public boolean supportsQuantifiedCompareCriteriaAll() {
    	return false; // TODO:FOR ALL
    }

	@Override
    public boolean supportsOrderByUnrelated() {
    	return true;
    }

	@Override
    public boolean supportsAggregatesCount() {
    	return true;
    }
    
	@Override
	public boolean supportsAggregatesCountStar() {
    	return true;
    }

	@Override
    public boolean supportsRowLimit() {
    	return true;
    }

	@Override
    public boolean supportsRowOffset() {
    	return true;
    }

	@Override
	public boolean supportsOnlyLiteralComparison() {
		return true;
	}
	
	@Override
    public boolean useAnsiJoin() {
    	return true;
    }

	/**
	 * 
	 * @param value
	 * @param expectedType
	 * @return
	 */
	public Object retrieveValue(Object value, Class<?> expectedType) {
		if (value == null) {
			return null;
		}
		if (value instanceof LocalDateTime) {
    		DateTime dateTime = ((LocalDateTime) value).toDateTime(DateTimeZone.forTimeZone(this.timeZone));
    		return new java.sql.Timestamp(dateTime.getMillis());
    	}
		if (value instanceof LocalTime) {
    		return new java.sql.Timestamp(((LocalTime)value).toDateTimeToday().getMillis());
    	}
		return value;
	}
    	
}
