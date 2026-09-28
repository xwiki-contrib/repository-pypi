package org.xwiki.contrib.repository.pypi.internal.dto.pypiJsonApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;
import org.xwiki.contrib.repository.pypi.internal.PypiParameters;
import org.xwiki.contrib.repository.pypi.internal.TestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by Krzysztof on 17.07.2017.
 */
public class PypiPackageJSONDtoTest
{
    private ObjectMapper objectMapper = new ObjectMapper();

    private PypiPackageJSONDto read(String filename) throws Exception
    {
        return objectMapper.readValue(TestUtils.getFileAsString(filename, this), PypiPackageJSONDto.class);
    }

    private static PypiPackageJSONDto withWheels(String... filenames)
    {
        PypiPackageJSONDto dto = new PypiPackageJSONDto();
        List<PypiPackageUrlDto> urls = new ArrayList<>();
        for (String filename : filenames) {
            PypiPackageUrlDto url = new PypiPackageUrlDto();
            url.setFilename(filename);
            url.setPackagetype(filename != null && filename.endsWith(".whl") ? PypiParameters.PACKAGE_TYPE_WHEEL
                : PypiParameters.PACKAGE_TYPE_SDIST);
            urls.add(url);
        }
        dto.setUrls(urls);

        return dto;
    }

    private static String getWheel(PypiPackageJSONDto dto)
    {
        return dto.getWhlFileUrlDto().map(PypiPackageUrlDto::getFilename).orElse(null);
    }

    @Test
    public void shouldFindUniversalWheel() throws Exception
    {
        PypiPackageJSONDto pypiPackageJSONDto = read("NetworkXPypiPackage.json");

        assertEquals("networkx-1.11-py2.py3-none-any.whl", getWheel(pypiPackageJSONDto));
    }

    @Test
    public void shouldNotFindAnyPackageWhenOnlyNativeWheels() throws Exception
    {
        PypiPackageJSONDto pypiPackageJSONDto = read("NumpyPypiPackage.json");

        assertFalse(pypiPackageJSONDto.getWhlFileUrlDto().isPresent());
    }

    @Test
    public void shouldFindWheelWithFileMetadata() throws Exception
    {
        PypiPackageJSONDto pypiPackageJSONDto = read("DecoratorPypiPackage.json");

        Optional<PypiPackageUrlDto> whlUrlDto = pypiPackageJSONDto.getWhlFileUrlDto();
        assertTrue(whlUrlDto.isPresent());
        assertEquals("decorator-4.0.11-py2.py3-none-any.whl", whlUrlDto.get().getFilename());
        assertEquals(PypiParameters.PACKAGE_TYPE_WHEEL, whlUrlDto.get().getPackagetype());
        assertEquals(8854, whlUrlDto.get().getSize());
    }

    @Test
    public void shouldWorkWhenNoDownloadUrlsArePresent() throws Exception
    {
        PypiPackageJSONDto pypiPackageJSONDto = read("PyplotPypiPackage.json");

        assertFalse(pypiPackageJSONDto.getWhlFileUrlDto().isPresent());
    }

    @Test
    public void shouldWorkWhenUrlsAreMissing()
    {
        assertFalse(new PypiPackageJSONDto().getWhlFileUrlDto().isPresent());
    }

    @Test
    public void shouldIgnoreYankedFiles() throws Exception
    {
        PypiPackageJSONDto pypiPackageJSONDto = read("DecoratorPypiPackage.json");
        pypiPackageJSONDto.getUrls().forEach(url -> url.setYanked(true));

        assertFalse(pypiPackageJSONDto.getWhlFileUrlDto().isPresent());
    }

    @Test
    public void shouldOnlySelectPurePython3Wheels()
    {
        assertEquals("pkg-1.0-py3-none-any.whl", getWheel(withWheels("pkg-1.0.tar.gz",
            "pkg-1.0-cp312-cp312-manylinux_2_17_x86_64.whl", "pkg-1.0-py2-none-any.whl", "pkg-1.0-py3-none-any.whl")));
        assertEquals("pkg-1.0-1-py311-none-any.whl", getWheel(withWheels("pkg-1.0-1-py311-none-any.whl")));

        assertNull(getWheel(withWheels("pkg-1.0-py2-none-any.whl")));
        assertNull(getWheel(withWheels("pkg-1.0-py3-abi3-any.whl")));
        assertNull(getWheel(withWheels("pkg-1.0-py3-none-win_amd64.whl")));
        assertNull(getWheel(withWheels("pkg-1.0-cp312-none-any.whl")));
        assertNull(getWheel(withWheels("none-any.whl")));
        assertNull(getWheel(withWheels((String) null)));
    }
}
