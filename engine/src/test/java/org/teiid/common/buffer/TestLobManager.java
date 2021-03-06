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
package org.teiid.common.buffer;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.teiid.common.buffer.FileStore.FileStoreOutputStream;
import org.teiid.common.buffer.LobManager.ReferenceMode;
import org.teiid.core.types.BlobImpl;
import org.teiid.core.types.BlobType;
import org.teiid.core.types.ClobImpl;
import org.teiid.core.types.ClobType;
import org.teiid.core.types.DataTypeManager;
import org.teiid.core.types.InputStreamFactory;
import org.teiid.core.types.InputStreamFactory.StorageMode;
import org.teiid.core.types.Streamable;
import org.teiid.core.util.ObjectConverterUtil;
import org.teiid.core.util.ReaderInputStream;

@SuppressWarnings("nls")
public class TestLobManager {

	@Test
	public void testLobPeristence() throws Exception{
		
		BufferManager buffMgr = BufferManagerFactory.getStandaloneBufferManager();
		FileStore fs = buffMgr.createFileStore("temp");
		
		ClobType clob = new ClobType(new ClobImpl(new InputStreamFactory() {
			@Override
			public InputStream getInputStream() throws IOException {
				return new ReaderInputStream(new StringReader("Clob contents One"),  Charset.forName(Streamable.ENCODING)); 
			}
			
		}, -1));
		
		BlobType blob = new BlobType(new BlobImpl(new InputStreamFactory() {
			@Override
			public InputStream getInputStream() throws IOException {
				return new ReaderInputStream(new StringReader("Blob contents Two"),  Charset.forName(Streamable.ENCODING));
			}
			
		}));
		
		FileStore fs1 = buffMgr.createFileStore("blob");
		FileStoreInputStreamFactory fsisf = new FileStoreInputStreamFactory(fs1, Streamable.ENCODING);
		FileStoreOutputStream fsos = fsisf.getOuputStream();
		byte[] b = new byte[DataTypeManager.MAX_LOB_MEMORY_BYTES + 1];
		fsos.write(b);
		fsos.close();
		BlobType blob1 = new BlobType(new BlobImpl(fsisf));		
		
		assertNotNull(blob1.getReferenceStreamId());
		
		LobManager lobManager = new LobManager(new int[] {0, 1, 2}, fs);
		lobManager.setMaxMemoryBytes(4);
		List<?> tuple = Arrays.asList(clob, blob, blob1);
		lobManager.updateReferences(tuple, ReferenceMode.CREATE);
		
		assertNotNull(blob1.getReferenceStreamId());
		
		lobManager.persist();
		
		Streamable<?>lob = lobManager.getLobReference(clob.getReferenceStreamId());
		assertTrue(lob.getClass().isAssignableFrom(ClobType.class));
		ClobType clobRead = (ClobType)lob;
		assertEquals(ClobType.getString(clob), ClobType.getString(clobRead));
		assertTrue(clobRead.length() != -1);
		
		lob = lobManager.getLobReference(blob.getReferenceStreamId());
		assertTrue(lob.getClass().isAssignableFrom(BlobType.class));
		BlobType blobRead = (BlobType)lob;
		assertTrue(Arrays.equals(ObjectConverterUtil.convertToByteArray(blob.getBinaryStream()), ObjectConverterUtil.convertToByteArray(blobRead.getBinaryStream())));
		
		lobManager.updateReferences(tuple, ReferenceMode.REMOVE);
		
		assertEquals(0, lobManager.getLobCount());
		
	}
	
	@Test public void testInlining() throws Exception{
		
		BufferManager buffMgr = BufferManagerFactory.getStandaloneBufferManager();
		FileStore fs = buffMgr.createFileStore("temp");
		
		ClobType clob = new ClobType(new ClobImpl(new InputStreamFactory() {
			@Override
			public InputStream getInputStream() throws IOException {
				return new ReaderInputStream(new StringReader("small"),  Charset.forName(Streamable.ENCODING)); 
			}
			
		}, 5));
		
		assertEquals(StorageMode.OTHER, InputStreamFactory.getStorageMode(clob));
		
		LobManager lobManager = new LobManager(new int[] {0}, fs);
		
		lobManager.updateReferences(Arrays.asList(clob), ReferenceMode.CREATE);
		
		assertEquals(StorageMode.MEMORY, InputStreamFactory.getStorageMode(clob));
	}
	
}
