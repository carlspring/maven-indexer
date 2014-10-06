package org.apache.maven.index.archetype;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0    
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.index.AbstractIndexCreatorHelper;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexingContext;

public class NexusArchetypeDataSourceTest
    extends AbstractIndexCreatorHelper
{
    private IndexingContext context;

    private Indexer indexer;

    // private IndexUpdater indexUpdater;

    private AbstractArchetypeDataSource nexusArchetypeDataSource;

    protected void setUp()
        throws Exception
    {
        super.setUp();

        prepare();
    }

    private void prepare()
        throws Exception
    {
        indexer = lookup( Indexer.class );

        Directory indexDir = null;

            File indexDirFile = super.getDirectory( "index/test" );

            super.deleteDirectory( indexDirFile );

            indexDir = FSDirectory.open( indexDirFile );

        File repo = new File( getBasedir(), "src/test/repo" );

        context =
            indexer.createIndexingContext( "test", "public", repo, indexDir,
                                                "http://repository.sonatype.org/content/groups/public/", null,
                                                true, true, DEFAULT_CREATORS );
        indexer.scan( context );

        // to update, uncomment this
        // IndexUpdateRequest updateRequest = new IndexUpdateRequest( context );
        // indexUpdater.fetchAndUpdateIndex( updateRequest );

        nexusArchetypeDataSource = new AbstractArchetypeDataSource( indexer )
        {
            @Override
            protected List<IndexingContext> getIndexingContexts()
            {
                return Collections.singletonList(context);
            }
        };
    }

    public void testArchetype()
        throws Exception
    {
        ArchetypeCatalog catalog = nexusArchetypeDataSource.getArchetypeCatalog( null );

        assertEquals( "Not correct numbers of archetypes in catalog!", 4, catalog.getArchetypes().size() );
    }

}
