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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import org.xwiki.contrib.repository.pypi.PypiExtension;
import org.xwiki.contrib.repository.pypi.PypiExtensionRepository;
import org.xwiki.contrib.repository.pypi.PypiParameters;
import org.xwiki.contrib.repository.pypi.dto.packagesInJython.PackagesInJython;
import org.xwiki.contrib.repository.pypi.utils.ObjectSerializingUtils;
import org.xwiki.contrib.repository.pypi.utils.PyPiHttpUtils;
import org.xwiki.contrib.repository.pypi.utils.PypiUtils;
import org.xwiki.environment.Environment;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiPackageListIndexUpdateTask extends TimerTask
{
    private final HttpClientContext localContext;

    private AtomicReference<File> pypiPackageListIndexDirectory;

    private PypiExtensionRepository pypiExtensionRepository;

    private Environment environment;

    private HttpClientFactory httpClientFactory;

    private Logger logger;

    public PypiPackageListIndexUpdateTask(AtomicReference<File> pypiPackageListIndexDirectory,
            PypiExtensionRepository pypiExtensionRepository, Environment environment,
            HttpClientFactory httpClientFactory, Logger logger)
    {
        this.pypiPackageListIndexDirectory = pypiPackageListIndexDirectory;
        this.pypiExtensionRepository = pypiExtensionRepository;
        this.environment = environment;
        this.httpClientFactory = httpClientFactory;
        this.localContext = HttpClientContext.create();
        this.logger = logger;
    }

    @Override public void run()
    {
        logger.info("Start of update lucene index task");
        boolean indexUpdated = false;
        boolean isFirstUpdate = false;
        File currentIndex = pypiPackageListIndexDirectory.get();
        File indexDir = environment.getTemporaryDirectory();
        if (currentIndex == null) {
            isFirstUpdate = true;
        } else {
            try {
                FileUtils.copyDirectory(currentIndex, indexDir);
            } catch (IOException e) {
                logger.error("Could not copy current lucene index for pypi packages to new directory", e);
                return;
            }
        }
        IndexWriter indexWriter = null;
        Optional<InputStream> htmlPageInputStream = null;
        try {
            Directory indexDirectory = FSDirectory.open(indexDir.toPath());
            indexWriter = new IndexWriter(indexDirectory, new IndexWriterConfig(new StandardAnalyzer()));
            htmlPageInputStream = getHtmlPageInputStream();
            if (htmlPageInputStream.isPresent()) {
                List<String> packageNames = parseHtmlPageToPackagenames(htmlPageInputStream.get());
                packageNames = removePackagesIncludedInJython(packageNames);
                if (isFirstUpdate) {
                    addAllValidPackagesToIndex(indexWriter, packageNames);
                } else {
                    updatePackageIndex(indexWriter, currentIndex, packageNames);
                }
                indexUpdated = true;
            }
        } catch (IOException e) {
            logger.error("IO problem whilst updating python package index", e);
        } catch (SAXException | ParserConfigurationException e) {
            logger.error("Problem occured whilst parsing list of packages from PyPi", e);
        } finally {
            IOUtils.closeQuietly(indexWriter);
            IOUtils.closeQuietly(htmlPageInputStream.orElse(null));
        }
        if (indexUpdated) {
            File previousIndexDir = pypiPackageListIndexDirectory.get();
            pypiPackageListIndexDirectory.set(indexDir);
            try {
                if (previousIndexDir != null) {
                    FileUtils.forceDelete(previousIndexDir);
                }
            } catch (Exception e) {

            }
            logger.info("End of update lucene index task. Pypi packages list index updated");
        }
        logger.info("End of update lucene index task. Pypi packages list update called but index not updated");
    }

    private List<String> removePackagesIncludedInJython(List<String> packageNames)
    {
        packageNames.removeAll(PackagesInJython.getPackagesIncludedInJython().getPackages());
        return packageNames;
    }

    private void updatePackageIndex(IndexWriter indexWriter, File currentIndex, List<String> packageNames)
            throws IOException
    {
        PypiPackageSearcher pypiPackageSearcher = new PypiPackageSearcher(currentIndex, logger);
        for (String packageName : packageNames) {
            try {
                Optional<String> packageVersion = pypiPackageSearcher.searchOneAndGetItsVersion(packageName);
                if (packageVersion.isPresent()) {
                    //check if there's newer version
                    String newestVersion =
                            pypiExtensionRepository.getPypiPackageData(packageName, Optional.empty()).getInfo()
                                    .getVersion();
                    if (PypiUtils.isSecondVersionNewer(packageVersion.get(), newestVersion)) {
                        Document newDocument = createNewDocument(packageName);
                        indexWriter.updateDocument(new Term(LuceneParameters.PACKAGE_NAME, packageName), newDocument);
                    }
                } else {
                    Document newDocument = createNewDocument(packageName);
                    indexWriter.addDocument(newDocument);
                }
            } catch (HttpException | ResolveException e) {
                logger.debug("Cannot obtain newer version for package " + packageName);
            }
        }
    }

    private void addAllValidPackagesToIndex(IndexWriter indexWriter, List<String> packageNames)
    {
        packageNames.parallelStream().forEach(packageName -> {

            try {
                indexWriter.addDocument(createNewDocument(packageName));
                logger.info("[" + packageName + "] added to index");
            } catch (ResolveException | HttpException e) {
                logger.debug("Could not resolve " + packageName + " package", e);
            } catch (IOException e) {
                logger.debug("IO problems whilst serializing " + packageName + " package extension", e);
            }
        });
    }

    private Document createNewDocument(String packageName, String version, PypiExtension pypiExtension)
            throws ResolveException, HttpException, IOException
    {
        Document document = new Document();
        document.add(new TextField(LuceneParameters.PACKAGE_NAME, packageName, Field.Store.YES));
        document.add(new StringField(LuceneParameters.ID, packageName, Field.Store.YES));
        document.add(new StoredField(LuceneParameters.VERSION, version));
        return document;
    }

    private Document createNewDocument(String packageName) throws ResolveException, HttpException, IOException
    {
        PypiExtension pypiExtension =
                (PypiExtension) pypiExtensionRepository
                        .getPythonPackageExtension(packageName, Optional.empty());
        String version = PypiUtils.getVersion(pypiExtension.getId()).get();
        return createNewDocument(packageName, version, pypiExtension);
    }

    protected List<String> parseHtmlPageToPackagenames(InputStream is)
            throws ParserConfigurationException, SAXException, IOException
    {
        SAXParserFactory parserFactor = SAXParserFactory.newInstance();
        SAXParser parser = parserFactor.newSAXParser();
        PypiPackageListSAXHandler handler = new PypiPackageListSAXHandler();
        parser.parse(is, handler);
        return handler.getPackageNames();
    }

    public Optional<InputStream> getHtmlPageInputStream()
    {
        try {
            return Optional.of(
                    PyPiHttpUtils
                            .performGet(new URI(PypiParameters.PACKAGE_LIST_SIMPLE), httpClientFactory, localContext));
        } catch (HttpException e) {
            logger.error("Failed to get list of python packages from PyPi", e);
        } catch (URISyntaxException e) {
            //should never happen
        }
        return Optional.empty();
    }

    class PypiPackageListSAXHandler extends DefaultHandler
    {
        List<String> packageNames = new LinkedList<>();

        private boolean aIsOpened = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException
        {
            switch (qName) {
                case "a":
                    aIsOpened = true;
            }
        }

        @Override public void characters(char[] ch, int start, int length) throws SAXException
        {
            if (aIsOpened) {
                String packageName = new String(ch, start, length);
                packageNames.add(packageName);
            }
        }

        @Override public void endElement(String uri, String localName, String qName) throws SAXException
        {
            switch (qName) {
                case "a":
                    aIsOpened = false;
            }
        }

        public List<String> getPackageNames()
        {
            return packageNames;
        }
    }
}
