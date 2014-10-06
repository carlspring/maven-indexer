package org.apache.maven.index;

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
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.Assert;

import org.apache.lucene.search.Query;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.UserInputSearchExpression;

public class ConcurrentUseTest
    extends AbstractNexusIndexerTest
{
    public static final int THREAD_COUNT = 10;

    protected File repo = new File( getBasedir(), "src/test/repo" );

    @Override
    protected void prepareIndexer( Indexer indexer )
        throws Exception
    {
        context =
            indexer.createIndexingContext( "test-default", "test", repo, indexDir, null, null, DEFAULT_CREATORS );

        assertNull( context.getTimestamp() ); // unknown upon creation

        indexer.scan(context);

        assertNotNull( context.getTimestamp() );
    }

    protected IndexUserThread createThread( final ArtifactInfo ai )
    {
        // we search and modify same context concurrently
        return new IndexUserThread( this, indexer, context, context, ai );
    }

    public void testConcurrency()
        throws Exception
    {
        IndexUserThread[] threads = new IndexUserThread[THREAD_COUNT];

        ArtifactInfo ai =
            new ArtifactInfo( "test-default", "org.apache.maven.indexer", "index-concurrent-artifact", "", null, "jar" );

        for ( int i = 0; i < THREAD_COUNT; i++ )
        {
            threads[i] = createThread( ai );

            threads[i].start();
        }

        Thread.sleep( 5000 );

        boolean thereWereProblems = false;

        int totalAdded = 0;

        for ( int i = 0; i < THREAD_COUNT; i++ )
        {
            threads[i].stopThread();

            threads[i].join();

            thereWereProblems = thereWereProblems || threads[i].hadProblem();

            totalAdded += threads[i].getAdded();
        }

        Assert.assertFalse( "Not all thread did clean job!", thereWereProblems );

        context.commit();

        // sleep more than bottleWarmer does, to be sure commit and reopen happened
        // BottleWarmer sleeps 1000 millis
        Thread.sleep( 2000 );

        //

        Query q = indexer.constructQuery( MAVEN.GROUP_ID, new UserInputSearchExpression( ai.getGroupId() ) );

        FlatSearchResponse result = indexer.searchFlat( new FlatSearchRequest( q, context ) );

        Assert.assertEquals( "All added should be found after final commit!", totalAdded, result.getTotalHitsCount() );
    }

    // ==

    private static final AtomicInteger versionSource = new AtomicInteger( 1 );

    protected void addToIndex( final Indexer indexer, final IndexingContext indexingContext )
        throws IOException
    {
        final ArtifactInfo artifactInfo =
            new ArtifactInfo( "test-default", "org.apache.maven.indexer", "index-concurrent-artifact", "1."
                + String.valueOf( versionSource.getAndIncrement() ), null , "jar");

        final ArtifactContext ac = new ArtifactContext( null, null, null, artifactInfo, artifactInfo.calculateGav() );

        indexer.addArtifactsToIndex( Collections.singleton( ac ), indexingContext );
    }

    protected void deleteFromIndex( final Indexer indexer, final IndexingContext indexingContext )
        throws IOException
    {
        // TODO: delete some of those already added
        // artifactInfo.version = "1." + String.valueOf( versionSource.getAndIncrement() );
        //
        // ac = new ArtifactContext( null, null, null, artifactInfo, artifactInfo.calculateGav() );
        //
        // indexer.deleteArtifactFromIndex( ac, indexingContext );
        //
        // deleted++;
    }

    protected int readIndex( final Indexer indexer, final IndexingContext indexingContext )
        throws IOException
    {
        final Query q =
            indexer.constructQuery( MAVEN.GROUP_ID, new UserInputSearchExpression( "org.apache.maven.indexer" ) );

        FlatSearchResponse result = indexer.searchFlat( new FlatSearchRequest( q, indexingContext ) );

        return result.getReturnedHitsCount();
    }

    // ==

    public static class IndexUserThread
        extends Thread
    {
        private final ConcurrentUseTest test;

        private final Indexer indexer;

        private final IndexingContext searchIndexingContext;

        private final IndexingContext modifyIndexingContext;

        private boolean stopped = false;

        private int added = 0;

        private int deleted = 0;

        private int lastSearchHitCount = 0;

        private Throwable t;

        public IndexUserThread( final ConcurrentUseTest test,
                                final Indexer indexer,
                                final IndexingContext searchIndexingContext,
                                final IndexingContext modifyIndexingContext,
                                ArtifactInfo artifactInfo )
        {
            this.test = test;

            this.indexer = indexer;

            this.searchIndexingContext = searchIndexingContext;

            this.modifyIndexingContext = modifyIndexingContext;
        }

        public int getAdded()
        {
            return added;
        }

        public int getDeleted()
        {
            return deleted;
        }

        public boolean hadProblem()
        {
            return t != null;
        }

        public int getLastSearchHitCount()
        {
            return lastSearchHitCount;
        }

        public void stopThread()
            throws IOException
        {
            this.modifyIndexingContext.commit();

            this.stopped = true;
        }

        public void run()
        {
            while ( !stopped )
            {
                if ( System.currentTimeMillis() % 5 == 0 )
                {
                    try
                    {
                        test.addToIndex(indexer, modifyIndexingContext );

                        added++;
                    }
                    catch ( Throwable e )
                    {
                        t = e;

                        e.printStackTrace();

                        throw new IllegalStateException( "error", e );
                    }
                }

                if ( System.currentTimeMillis() % 11 == 0 )
                {
                    try
                    {
                        // test.deleteFromIndex( indexer, modifyIndexingContext );
                        // deleted++;
                    }
                    catch ( Throwable e )
                    {
                        t = e;

                        throw new IllegalStateException( "error", e );
                    }
                }

                try
                {
                    lastSearchHitCount = test.readIndex(indexer, searchIndexingContext );
                }
                catch ( Throwable e )
                {
                    t = e;

                    throw new IllegalStateException( "error", e );
                }
            }
        }
    }
}
