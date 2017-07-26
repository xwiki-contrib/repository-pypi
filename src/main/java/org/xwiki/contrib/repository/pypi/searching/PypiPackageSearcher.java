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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
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

import jdk.nashorn.internal.runtime.regexp.joni.Regex;

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
        if (documentId.isPresent()) {
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

    public IterableResult<String> search(String searchQuery, int offset, int hitsPerPage)
            throws ParseException, IOException
    {
        Query q = new RegexpQuery(new Term(LuceneParameters.PACKAGE_NAME, ".*" + searchQuery + ".*"));
        TopDocs hits = indexSearcher.search(q, LuceneParameters.MAX_NUMBER_OF_SEARCHING_HITS);

        List<String> packageNames = Arrays.stream(hits.scoreDocs).map(scoreDoc -> {
            try {
                return obtainPackageName(scoreDoc.doc);
            } catch (IOException e) {
            }
            return null;
        }).filter(Objects::nonNull).distinct().collect(Collectors.toList());

        int totalHits = packageNames.size();

        if (hitsPerPage == 0 || offset >= totalHits) {
            return new CollectionIterableResult<String>(totalHits, offset, Collections.<String>emptyList());
        }

        int fromIndex = offset < 0 ? 0 : offset;
        int toId = offset + hitsPerPage > totalHits || hitsPerPage < 0 ? totalHits : offset + hitsPerPage;

        List<String> result = packageNames.subList(fromIndex, toId);
        return new CollectionIterableResult<String>(totalHits, offset, result);
    }

    private String obtainPackageName(int docId) throws IOException
    {
        return indexSearcher.doc(docId).get(LuceneParameters.PACKAGE_NAME);
    }

    public File getIndexDirectoryFile()
    {
        return indexDirectoryFile;
    }
}
