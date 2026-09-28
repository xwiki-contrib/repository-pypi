package org.xwiki.contrib.repository.pypi.internal.searching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;
import org.xwiki.environment.Environment;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiPackageListIndexUpdateTask}.
 *
 * @version $Id$
 */
public class PypiPackageListIndexUpdateTaskTest
{
    private File permanentDirectory;

    private AtomicReference<File> indexDirectory = new AtomicReference<>();

    private CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

    private StatusLine statusLine = mock(StatusLine.class);

    private HttpEntity entity = mock(HttpEntity.class);

    private PypiPackageListIndexUpdateTask task;

    @Before
    public void before() throws Exception
    {
        this.permanentDirectory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();

        Environment environment = mock(Environment.class);
        when(environment.getPermanentDirectory()).thenReturn(this.permanentDirectory);

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(this.statusLine);
        when(response.getEntity()).thenReturn(this.entity);
        when(this.httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(response);
        HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
        when(httpClientFactory.createClient(isNull(), isNull())).thenReturn(this.httpClient);

        this.task = new PypiPackageListIndexUpdateTask(this.indexDirectory, environment, httpClientFactory,
            mock(Logger.class));
    }

    private List<String> parsePackageNames(InputStream stream) throws IOException
    {
        List<String> packageNames = new ArrayList<>();
        this.task.parsePackageNames(stream, packageNames::add);

        return packageNames;
    }

    private List<String> parsePackageNames(String json) throws IOException
    {
        return parsePackageNames(IOUtils.toInputStream(json, StandardCharsets.UTF_8));
    }

    @Test
    public void parsePackageNames() throws Exception
    {
        try (InputStream stream = getClass().getResourceAsStream("SimpleIndex.json")) {
            assertEquals(
                Arrays.asList("0", "0-._.-._.-._.-._.-._.-._.-0", "000", "decorator", "networkx", "numpy", "requests"),
                parsePackageNames(stream));
        }
    }

    @Test
    public void parsePackageNamesIgnoresUnknownFields() throws Exception
    {
        String json = "{\"unknown\":{\"projects\":[{\"name\":\"nested\"}]},"
            + "\"projects\":[{\"extra\":[1,{\"name\":\"x\"}],\"name\":\"package\"}],\"meta\":{}}";

        assertEquals(Arrays.asList("package"), parsePackageNames(json));
    }

    @Test
    public void parsePackageNamesWithoutProjects() throws Exception
    {
        assertTrue(parsePackageNames("{}").isEmpty());
    }

    @Test(expected = IOException.class)
    public void parsePackageNamesWhenNotAnObject() throws Exception
    {
        parsePackageNames("[]");
    }

    @Test
    public void run() throws Exception
    {
        File previousIndex = new File(this.permanentDirectory, "previous");
        previousIndex.mkdirs();
        this.indexDirectory.set(previousIndex);

        when(this.statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(this.entity.getContent()).thenReturn(getClass().getResourceAsStream("SimpleIndex.json"));

        this.task.run();

        ArgumentCaptor<HttpUriRequest> request = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(this.httpClient).execute(request.capture(), any(HttpContext.class));
        assertEquals(PypiParameters.PACKAGE_LIST_SIMPLE_API, request.getValue().getURI().toString());
        assertEquals(PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE,
            request.getValue().getFirstHeader(HttpHeaders.ACCEPT).getValue());

        assertNotEquals(previousIndex, this.indexDirectory.get());
        assertTrue(!previousIndex.exists());

        PypiPackageSearcher searcher = new PypiPackageSearcher(this.indexDirectory.get(), mock(Logger.class));
        assertEquals(7, searcher.search("", 0, -1).getTotalHits());
        List<String> result = new ArrayList<>();
        searcher.search("decorator", 0, -1).forEach(result::add);
        assertEquals(Collections.singletonList("decorator"), result);
    }

    @Test
    public void runWhenIndexIsNotAvailable() throws Exception
    {
        File previousIndex = new File(this.permanentDirectory, "previous");
        previousIndex.mkdirs();
        this.indexDirectory.set(previousIndex);

        when(this.statusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);

        this.task.run();

        // Keep the current index instead of replacing it with an empty one
        assertEquals(previousIndex, this.indexDirectory.get());
        assertTrue(previousIndex.exists());
    }
}
