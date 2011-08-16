/**
 * Copyright (c) 2002-2011 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.server.rest;

import java.io.IOException;

import org.neo4j.kernel.AbstractGraphDatabase;
import org.neo4j.server.NeoServerWithEmbeddedWebServer;
import org.neo4j.server.rest.domain.GraphDbHelper;
import org.neo4j.server.rest.domain.JsonHelper;
import org.neo4j.server.rest.domain.JsonParseException;

import com.sun.jersey.api.client.Client;

public final class FunctionalTestHelper
{
    private final NeoServerWithEmbeddedWebServer server;
    private final GraphDbHelper helper;

    public static final Client CLIENT = Client.create();
    private RestRequest request;

    public FunctionalTestHelper( NeoServerWithEmbeddedWebServer server )
    {
        if ( server.getDatabase() == null )
        {
            throw new RuntimeException( "Server must be started before using " + getClass().getName() );
        }
        this.helper = new GraphDbHelper( server.getDatabase() );
        this.server = server;
        this.request = new RestRequest(server.baseUri().resolve("db/data/"));
    }

    public GraphDbHelper getGraphDbHelper()
    {
        return helper;
    }

    void assertLegalJson( String entity ) throws IOException, JsonParseException
    {
        JsonHelper.jsonToMap( entity );
    }

    public String dataUri()
    {
        return server.baseUri().toString() + "db/data/";
    }

    String nodeUri()
    {
        return dataUri() + "node";
    }

    public String nodeUri( long id )
    {
        return nodeUri() + "/" + id;
    }

    String nodePropertiesUri( long id )
    {
        return nodeUri( id ) + "/properties";
    }

    String nodePropertyUri( long id, String key )
    {
        return nodePropertiesUri( id ) + "/" + key;
    }

    String relationshipUri()
    {
        return dataUri() + "relationship";
    }

    public String relationshipUri( long id )
    {
        return relationshipUri() + "/" + id;
    }

    String relationshipPropertiesUri( long id )
    {
        return relationshipUri( id ) + "/properties";
    }

    String relationshipPropertyUri( long id, String key )
    {
        return relationshipPropertiesUri( id ) + "/" + key;
    }

    String relationshipsUri( long nodeId, String dir, String... types )
    {
        StringBuilder typesString = new StringBuilder();
        for ( String type : types )
        {
            typesString.append( typesString.length() > 0 ? "&" : "" );
            typesString.append( type );
        }
        return nodeUri( nodeId ) + "/relationships/" + dir + "/" + typesString;
    }

    String indexUri()
    {
        return dataUri() + "index/";
    }
    
    String nodeAutoIndexUri()
    {
        return indexUri() + "auto/node/";
    }
    
    String relationshipAutoIndexUri()
    {
        return indexUri() + "auto/relationship/";
    }

    String nodeIndexUri()
    {
        return indexUri() + "node/";
    }

    String relationshipIndexUri()
    {
        return indexUri() + "relationship/";
    }

    String mangementUri()
    {
        return server.baseUri()
                .toString() + "db/manage";
    }

    String indexNodeUri( String indexName )
    {
        return nodeIndexUri() + indexName;
    }

    String indexRelationshipUri( String indexName )
    {
        return relationshipIndexUri() + indexName;
    }

    String indexNodeUri( String indexName, String key, Object value )
    {
        return indexNodeUri( indexName ) + "/" + key + "/" + value;
    }

    String indexRelationshipUri( String indexName, String key, Object value )
    {
        return indexRelationshipUri( indexName ) + "/" + key + "/" + value;
    }

    String extensionUri()
    {
        return dataUri() + "ext";
    }

    String extensionUri( String name )
    {
        return extensionUri() + "/" + name;
    }

    String graphdbExtensionUri( String name, String method )
    {
        return extensionUri( name ) + "/graphdb/" + method;
    }

    String nodeExtensionUri( String name, String method, long id )
    {
        return extensionUri( name ) + "/node/" + id + "/" + method;
    }

    String relationshipExtensionUri( String name, String method, long id )
    {
        return extensionUri( name ) + "/relationship/" + id + "/" + method;
    }

    public AbstractGraphDatabase getDatabase()
    {
        return server.getDatabase().graph;
    }

    public String getWebadminUri()
    {
        return server.baseUri()
                .toString() + "webadmin";
    }

    public JaxRsResponse get(String path) {
        return request.get(path);
    }

    public JaxRsResponse get(String path, String data) {
        return request.get(path, data);
    }

    public JaxRsResponse delete(String path) {
        return request.delete(path);
    }

    public JaxRsResponse post(String path, String data) {
        return request.post(path, data);
    }

    public void put(String path, String data) {
        request.put(path, data);
    }
}
