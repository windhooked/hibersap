package org.hibersap.execution.jca;

/*
 * Copyright (C) 2008 akquinet tech@spree GmbH
 * 
 * This file is part of Hibersap.
 * 
 * Hibersap is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 * 
 * Hibersap is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License along with Hibersap. If
 * not, see <http://www.gnu.org/licenses/>.
 */

import java.util.Map;

import javax.resource.ResourceException;
import javax.resource.cci.ConnectionFactory;
import javax.resource.cci.MappedRecord;
import javax.resource.cci.Record;
import javax.resource.cci.RecordFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibersap.HibersapException;
import org.hibersap.execution.Connection;
import org.hibersap.execution.UnsafeCastHelper;
import org.hibersap.session.SessionImplementor;
import org.hibersap.session.Transaction;

/**
 * Implementation for JCA, i.e. it uses a deployed resource adapter to connect to SAP.
 * 
 * @author dahm
 */
public class JCAConnection
    implements Connection
{
    private static Log LOG = LogFactory.getLog( JCAConnection.class );

    private final javax.resource.cci.Connection connection;

    private RecordFactory recordFactory;

    private Transaction transaction;

    private final JCAMapper mapper = new JCAMapper();

    public JCAConnection( final ConnectionFactory connectionFactory )
    {
        try
        {
            connection = connectionFactory.getConnection();
            recordFactory = connectionFactory.getRecordFactory();
        }
        catch ( ResourceException e )
        {
            throw new HibersapException( "Problem creating connection.", e );
        }
    }

    public Transaction beginTransaction( final SessionImplementor session )
    {
        if ( transaction == null )
        {
            LOG.debug( "Begin JCA transaction: " + session );

            try
            {
                transaction = new JCATransaction( connection.getLocalTransaction() );
            }
            catch ( final ResourceException e )
            {
                throw new HibersapException( "new JCATransaction", e );
            }

            transaction.begin();
        }

        return transaction;
    }

    public void close()
    {
        try
        {
            connection.close();
        }
        catch ( final ResourceException e )
        {
            throw new HibersapException( "While closing JCA connection", e );
        }
    }

    public void execute( final String bapiName, final Map<String, Object> functionMap )
    {
        Record result;

        try
        {
            MappedRecord mappedInputRecord = mapper.mapFunctionMapValuesToMappedRecord( bapiName, recordFactory,
                                                                                        functionMap );

            LOG.debug( "JCA Execute: " + bapiName + ", arguments= " + functionMap + "\ninputRecord = "
                + mappedInputRecord );

            result = connection.createInteraction().execute( null, mappedInputRecord );

            LOG.debug( "JCA Execute: " + bapiName + ", result = " + result );

            final Map<String, Object> resultMap = UnsafeCastHelper.castToMap( result );
            mapper.mapRecordToFunctionMap( functionMap, resultMap );
        }
        catch ( final ResourceException e )
        {
            throw new HibersapException( "Error executing function module " + bapiName, e );
        }
    }

    public Transaction getTransaction()
    {
        return transaction;
    }
}
