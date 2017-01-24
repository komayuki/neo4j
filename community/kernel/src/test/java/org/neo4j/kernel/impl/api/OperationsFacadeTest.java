/*
 * Copyright (c) 2002-2017 "Neo Technology,"
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
package org.neo4j.kernel.impl.api;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import org.neo4j.helpers.collection.Iterators;
import org.neo4j.kernel.api.schema.NodePropertyDescriptor;
import org.neo4j.kernel.api.TokenNameLookup;
import org.neo4j.kernel.api.exceptions.schema.DuplicateIndexSchemaRuleException;
import org.neo4j.kernel.api.exceptions.schema.IndexSchemaRuleNotFoundException;
import org.neo4j.kernel.api.exceptions.schema.SchemaRuleNotFoundException;
import org.neo4j.kernel.api.schema.IndexDescriptor;
import org.neo4j.kernel.api.schema.IndexDescriptorFactory;
import org.neo4j.kernel.impl.api.operations.SchemaReadOperations;
import org.neo4j.test.mockito.matcher.KernelExceptionUserMessageMatcher;

@RunWith( MockitoJUnitRunner.class )
public class OperationsFacadeTest
{
    private final String LABEL1 = "Label1";
    private final String PROP1 = "Prop1";
    private final NodePropertyDescriptor descriptor = new NodePropertyDescriptor( 1, 2 );

    @Mock
    private KernelStatement kernelStatement;
    @Mock
    private StatementOperationParts statementOperationParts;
    @Mock
    private SchemaReadOperations schemaReadOperations;
    @InjectMocks
    private OperationsFacade operationsFacade;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception
    {
        operationsFacade.initialize( statementOperationParts );
    }

    @Test
    public void testThrowExceptionWhenIndexNotFoundByLabelAndProperty() throws SchemaRuleNotFoundException
    {
        setupSchemaReadOperations();

        TokenNameLookup tokenNameLookup = getDefaultTokenNameLookup();

        expectedException.expect( IndexSchemaRuleNotFoundException.class );
        expectedException.expect( new KernelExceptionUserMessageMatcher<>( tokenNameLookup,
                "Index for label 'Label1' and property 'Prop1' not found." ) );

        operationsFacade.indexGetForLabelAndPropertyKey( descriptor );
    }

    @Test
    public void testThrowExceptionWhenDuplicateUniqueIndexFound()
            throws SchemaRuleNotFoundException, DuplicateIndexSchemaRuleException
    {
        SchemaReadOperations readOperations = setupSchemaReadOperations();
        IndexDescriptor index = IndexDescriptorFactory.of( descriptor );
        Mockito.when( readOperations
                .uniqueIndexesGetForLabel( Mockito.any( KernelStatement.class ), Mockito.eq( descriptor.getLabelId() ) ) )
                .thenReturn( Iterators.iterator( index, index ) );
        TokenNameLookup tokenNameLookup = getDefaultTokenNameLookup();

        expectedException.expect( DuplicateIndexSchemaRuleException.class );
        expectedException.expect( new KernelExceptionUserMessageMatcher<>( tokenNameLookup,
                "Multiple uniqueness indexes found for label 'Label1' and property 'Prop1'." ) );

        operationsFacade.uniqueIndexGetForLabelAndPropertyKey( descriptor );
    }

    @Test
    public void testThrowExceptionWhenUniqueIndexNotFound()
            throws SchemaRuleNotFoundException, DuplicateIndexSchemaRuleException
    {
        SchemaReadOperations readOperations = setupSchemaReadOperations();
        TokenNameLookup tokenNameLookup = getDefaultTokenNameLookup();
        Mockito.when( readOperations
                .uniqueIndexesGetForLabel( Mockito.any( KernelStatement.class ), Mockito.eq( descriptor.getLabelId() ) ) )
                .thenReturn( Iterators.<IndexDescriptor>emptyIterator() );

        expectedException.expect( IndexSchemaRuleNotFoundException.class );
        expectedException.expect( new KernelExceptionUserMessageMatcher<>( tokenNameLookup,
                "Uniqueness index for label 'Label1' and property 'Prop1' not found." ) );

        operationsFacade.uniqueIndexGetForLabelAndPropertyKey( descriptor );
    }

    private SchemaReadOperations setupSchemaReadOperations()
    {
        SchemaReadOperations readOperations = Mockito.mock(SchemaReadOperations.class);
        Mockito.when( statementOperationParts.schemaReadOperations() ).thenReturn( readOperations );
        return readOperations;
    }

    private TokenNameLookup getDefaultTokenNameLookup()
    {
        TokenNameLookup tokenNameLookup = Mockito.mock( TokenNameLookup.class );
        Mockito.when( tokenNameLookup.labelGetName( descriptor.getLabelId() ) ).thenReturn( LABEL1 );
        Mockito.when( tokenNameLookup.propertyKeyGetName( descriptor.getPropertyKeyId() ) ).thenReturn( PROP1 );
        return tokenNameLookup;
    }

}
