package org.xwiki.contrib.repository.pypi.internal.searching;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    private PypiPackageIndex index;

    private CloseableHttpClient httpClient = mock(CloseableHttpClient.class);

    private StatusLine statusLine = mock(StatusLine.class);

    private HttpEntity entity = mock(HttpEntity.class);

    private PypiPackageListIndexUpdateTask task;

    @Before
    public void before() throws Exception
    {
        this.permanentDirectory = new File("target/test-" + System.nanoTime()).getAbsoluteFile();
        this.index = new PypiPackageIndex(this.permanentDirectory, mock(Logger.class));

        CloseableHttpResponse response = mock(CloseableHttpResponse.class);
        when(response.getStatusLine()).thenReturn(this.statusLine);
        when(response.getEntity()).thenReturn(this.entity);
        when(this.httpClient.execute(any(HttpUriRequest.class), any(HttpContext.class))).thenReturn(response);
        HttpClientFactory httpClientFactory = mock(HttpClientFactory.class);
        when(httpClientFactory.createClient(isNull(), isNull())).thenReturn(this.httpClient);

        this.task = new PypiPackageListIndexUpdateTask(this.index, httpClientFactory, mock(Logger.class));
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
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        when(this.statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(this.entity.getContent()).thenReturn(getClass().getResourceAsStream("SimpleIndex.json"));

        this.task.run();

        ArgumentCaptor<HttpUriRequest> request = ArgumentCaptor.forClass(HttpUriRequest.class);
        verify(this.httpClient).execute(request.capture(), any(HttpContext.class));
        assertEquals(PypiParameters.PACKAGE_LIST_SIMPLE_API, request.getValue().getURI().toString());
        assertEquals(PypiParameters.SIMPLE_API_JSON_MEDIA_TYPE,
            request.getValue().getFirstHeader(HttpHeaders.ACCEPT).getValue());

        assertNotEquals(previousIndex, this.index.getFile());
        assertFalse(previousIndex.exists());
        assertEquals(
            Arrays.asList("0", "0-._.-._.-._.-._.-._.-._.-0", "000", "decorator", "networkx", "numpy", "requests"),
            Files.readAllLines(this.index.getFile().toPath()));
    }

    @Test
    public void runWhenIndexIsNotAvailable() throws Exception
    {
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        when(this.statusLine.getStatusCode()).thenReturn(HttpStatus.SC_NOT_FOUND);

        this.task.run();

        // Keep the current index instead of replacing it with an empty one
        assertEquals(previousIndex, this.index.getFile());
        assertTrue(previousIndex.exists());
    }

    @Test
    public void runWhenIndexIsInvalid() throws Exception
    {
        this.index.update(consumer -> consumer.accept("previous"));
        File previousIndex = this.index.getFile();

        when(this.statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(this.entity.getContent()).thenReturn(IOUtils.toInputStream("<html>", StandardCharsets.UTF_8));

        this.task.run();

        assertEquals(previousIndex, this.index.getFile());
        assertEquals(Arrays.asList("previous"), Files.readAllLines(previousIndex.toPath()));
        assertEquals(1, this.permanentDirectory.list().length);
    }
}
