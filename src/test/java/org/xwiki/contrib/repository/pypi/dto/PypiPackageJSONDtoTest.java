package org.xwiki.contrib.repository.pypi.dto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by Krzysztof on 17.07.2017.
 */
public class PypiPackageJSONDtoTest
{
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void deserializationTest() throws Exception
    {
        String filenameOfJson = "PypiPackage.json";
        String json = getJson(filenameOfJson);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        //no exception expected - all fields are mapped

        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getZipUrlDtoForVersion("1.5");
        assertTrue(zipUrlDtoForVersion.isPresent());

        Optional<PypiPackageUrlDto> zipUrlDtoForNewestVersion = pypiPackageJSONDto.getZipUrlDtoForNewestVersion();
        assertTrue(zipUrlDtoForNewestVersion.isPresent());

    }

    @Test
    public void shouldWorkWhenNoDownloadUrlsArePresent() throws Exception
    {
        String filenameOfJson = "PyplotPypiPackage.json";
        String json = getJson(filenameOfJson);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getZipUrlDtoForVersion("1.5.0");
        assertFalse(zipUrlDtoForVersion.isPresent());
        Optional<PypiPackageUrlDto> zipUrlDtoForNewestVersion = pypiPackageJSONDto.getZipUrlDtoForNewestVersion();
        assertFalse(zipUrlDtoForNewestVersion.isPresent());
    }

    public String getJson(String filename) throws IOException
    {
        InputStream resourceAsStream = getClass().getResourceAsStream(filename);
        try {
            return IOUtils.toString(resourceAsStream);
        } finally {
            IOUtils.closeQuietly(resourceAsStream);
        }
    }


}