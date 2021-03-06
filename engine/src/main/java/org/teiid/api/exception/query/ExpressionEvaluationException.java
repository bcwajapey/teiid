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

package org.teiid.api.exception.query;

import org.teiid.core.BundleUtil;
import org.teiid.core.TeiidProcessingException;

/**
 * This exception is thrown when an error occurs while evaluating a SQL expression.
 */
public class ExpressionEvaluationException extends TeiidProcessingException {

	private static final long serialVersionUID = 4955469005442543688L;

	/**
     * No-arg constructor required by Externalizable semantics.
     */
    public ExpressionEvaluationException() {
        super();
    }
    
    /**
     * Construct an instance with the message specified.
     *
     * @param message A message describing the exception
     */
    public ExpressionEvaluationException( String message ) {
        super( message );
    }
  
    public ExpressionEvaluationException(Throwable e) {
        super(e);
    }
    
    /**
     * Construct an instance from a message and an exception to chain to this one.
     *
     * @param message A message describing the exception
     * @param e An exception to nest within this one
     */
    public ExpressionEvaluationException( Throwable e, String message ) {
        super( e, message );
    }

    public ExpressionEvaluationException(BundleUtil.Event event, Throwable e) {
        super( event, e);
    }    
    
    public ExpressionEvaluationException(BundleUtil.Event event, Throwable e, String msg) {
        super(event, e, msg);
    }
    
    public ExpressionEvaluationException(BundleUtil.Event event, String msg) {
        super(event, msg);
    }      
}
