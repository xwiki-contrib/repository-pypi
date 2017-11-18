package org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi;

import java.util.Optional;

import org.junit.Test;
import org.xwiki.contrib.repository.pypi.internal.TestUtils;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi.PypiPackageUrlDto;

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
    public void shouldFindPackageRegardingPythonVersion27() throws Exception
    {
        String filenameOfJson = "NetworkXPypiPackage.json";
        String json = TestUtils.getFileAsString(filenameOfJson, this);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        //no exception expected - all fields are mapped

        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getEggOrWhlFileUrlDtoForVersion("1.5");
        assertTrue(zipUrlDtoForVersion.isPresent());

        Optional<PypiPackageUrlDto> zipUrlDtoForNewestVersion = pypiPackageJSONDto.getEggOrWhlUrlDtoForNewestVersion();
        assertTrue(zipUrlDtoForNewestVersion.isPresent());
    }

    @Test
    public void shouldNotFindAnyPackageWhenNoPythonVersion27() throws Exception
    {
        String filenameOfJson = "NumpyPypiPackage.json";
        String json = TestUtils.getFileAsString(filenameOfJson, this);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        //no exception expected - all fields are mapped

        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getEggOrWhlFileUrlDtoForVersion("1.13.1");
        assertFalse(zipUrlDtoForVersion.isPresent());
    }

    @Test
    public void shouldFindPackageRegardingUrlFileNamePy2() throws Exception
    {
        String filenameOfJson = "DecoratorPypiPackage.json";
        String json = TestUtils.getFileAsString(filenameOfJson, this);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);

        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getEggOrWhlFileUrlDtoForVersion("4.0.1");
        assertTrue(zipUrlDtoForVersion.isPresent());

        Optional<PypiPackageUrlDto> zipUrlDtoForNewestVersion = pypiPackageJSONDto.getEggOrWhlUrlDtoForNewestVersion();
        assertTrue(zipUrlDtoForNewestVersion.isPresent());
    }



    @Test
    public void shouldWorkWhenNoDownloadUrlsArePresent() throws Exception
    {
        String filenameOfJson = "PyplotPypiPackage.json";
        String json = TestUtils.getFileAsString(filenameOfJson, this);
        PypiPackageJSONDto pypiPackageJSONDto = objectMapper.readValue(json, PypiPackageJSONDto.class);
        Optional<PypiPackageUrlDto> zipUrlDtoForVersion = pypiPackageJSONDto.getZipUrlDtoForVersion("1.5.0");
        assertFalse(zipUrlDtoForVersion.isPresent());
        Optional<PypiPackageUrlDto> zipUrlDtoForNewestVersion = pypiPackageJSONDto.getEggOrWhlUrlDtoForNewestVersion();
        assertFalse(zipUrlDtoForNewestVersion.isPresent());
    }

}