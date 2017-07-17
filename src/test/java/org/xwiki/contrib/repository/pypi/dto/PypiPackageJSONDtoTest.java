package org.xwiki.contrib.repository.pypi.dto;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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

        PypiPackageUrlDto zipUrlDtoForVersion = pypiPackageJSONDto.getZipUrlDtoForVersion("1.5");
        assertNotNull(zipUrlDtoForVersion);

        PypiPackageUrlDto zipUrlDtoForNewestVersion = pypiPackageJSONDto.getZipUrlDtoForNewestVersion();
        assertNotNull(zipUrlDtoForNewestVersion);

    }

    @Test
    public void shouldWorkWhenNoDownloadUrlsArePresent() throws Exception
    {
        String filenameOfJson = "PyplotPypiPackage.json";
        String json = getJson(filenameOfJson);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        PypiPackageUrlDto zipUrlDtoForVersion = pypiPackageJSONDto.getZipUrlDtoForVersion("1.5.0");
        assertNull(zipUrlDtoForVersion);
        PypiPackageUrlDto zipUrlDtoForNewestVersion = pypiPackageJSONDto.getZipUrlDtoForNewestVersion();
        assertNull(zipUrlDtoForNewestVersion);
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