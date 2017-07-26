/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.repository.pypi.searching;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.xwiki.contrib.repository.pypi.PypiExtension;
import org.xwiki.contrib.repository.pypi.utils.ObjectSerializingUtils;
import org.xwiki.extension.Extension;
import org.xwiki.extension.repository.result.CollectionIterableResult;
import org.xwiki.extension.repository.result.IterableResult;

/**
 * Created by Krzysztof on 24.07.2017.
 */
public class PypiPackageSearcher
{
    private final IndexSearcher indexSearcher;

    private final StandardAnalyzer analyzer;

    private File indexDirectoryFile;

    private final Logger logger;

    public PypiPackageSearcher(File indexDirectoryFile, Logger logger) throws IOException
    {
        this.indexDirectoryFile = indexDirectoryFile;
        this.logger = logger;
        Directory indexDirectory = FSDirectory.open(indexDirectoryFile.toPath());
        IndexReader reader = DirectoryReader.open(indexDirectory);
        indexSearcher = new IndexSearcher(reader);
        analyzer = new StandardAnalyzer();
    }

    public Optional<Document> searchOneAndGetItsDocument(String packageName)
    {
        Optional<Integer> documentId = searchOneAndGetItsDocumentId(packageName);
        if(documentId.isPresent()){
            try {
                return Optional.of(indexSearcher.doc(documentId.get()));
            } catch (IOException e) {
                logger.error("Could not get document of id: " + documentId.get());
            }
        }
        return Optional.empty();
    }

    public Optional<String> searchOneAndGetItsVersion(String packageName)
    {
        return searchOneAndGetField(packageName, LuceneParameters.VERSION);
    }

    public Optional<String> searchOneAndGetField(String packageName, String field)
    {
        Optional<Integer> id = searchOneAndGetItsDocumentId(packageName);
        if (id.isPresent()) {
            try {
                return Optional.of(indexSearcher.doc(id.get()).get(field));
            } catch (IOException e) {
                logger.error("Could not get document of id: " + id.get());
            }
        }
        return Optional.empty();
    }

    public Optional<Integer> searchOneAndGetItsDocumentId(String packageName)
    {
        Query q = null;
        try {
            q = new QueryParser(LuceneParameters.ID, analyzer).parse(packageName);
            TopDocs hits = indexSearcher.search(q, 1);
            if (hits.totalHits == 1) {
                return Optional.of(hits.scoreDocs[0].doc);
            }
        } catch (ParseException e) {
            logger.debug("Could not parse query resolving package: " + packageName, e);
        } catch (IOException e) {
            logger.debug("Could not perform searching in lucene index for package: " + packageName, e);
        }
        return Optional.empty();
    }

    public IterableResult<Extension> search(String searchQuery, int offset, int hitsPerPage)
            throws ParseException, IOException
    {
        Query q = new QueryParser(LuceneParameters.PACKAGE_NAME, analyzer).parse(searchQuery);
        TopDocs hits = indexSearcher.search(q, LuceneParameters.MAX_NUMBER_OF_SEARCHING_HITS);

        int totalHits = hits.totalHits;

        if (hitsPerPage == 0 || offset >= totalHits) {
            return new CollectionIterableResult<Extension>(totalHits, offset, Collections.<Extension>emptyList());
        }

        int fromIndex = offset < 0 ? 0 : offset;
        int toId = offset + hitsPerPage > totalHits || hitsPerPage < 0 ? totalHits : offset + hitsPerPage;

        List<Extension> result = new ArrayList<Extension>(toId - fromIndex);
        for (int i = fromIndex; i < toId; ++i) {
            try {
                result.add(deserializeExtension(hits.scoreDocs[i]));
            } catch (ClassNotFoundException e) {
                logger.error("Problem whilst deserializing pypi extension object", e);
            }
        }
        return new CollectionIterableResult<Extension>(totalHits, offset, result);
    }

    private PypiExtension deserializeExtension(ScoreDoc scoreDoc) throws IOException, ClassNotFoundException
    {
        int docId = scoreDoc.doc;
        String serializedExtension = indexSearcher.doc(docId).get(LuceneParameters.EXTENSION);
        return (PypiExtension) ObjectSerializingUtils.fromString(serializedExtension);
    }

    public File getIndexDirectoryFile()
    {
        return indexDirectoryFile;
    }

    public Optional<PypiExtension> searchOneAndExtension(String packageName)
    {
        Optional<String> serializedExtension = searchOneAndGetField(packageName, LuceneParameters.EXTENSION);
        if (serializedExtension.isPresent()) {
            try {
                return Optional.of((PypiExtension) ObjectSerializingUtils.fromString(serializedExtension.get()));
            } catch (IOException | ClassNotFoundException e) {
                logger.error("Error whilst deserializing extension for package: " + packageName);
            }
        }
        return Optional.empty();
    }
}
