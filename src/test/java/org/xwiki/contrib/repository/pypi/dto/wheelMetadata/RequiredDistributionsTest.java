package org.xwiki.contrib.repository.pypi.dto.wheelMetadata;

import java.net.URISyntaxException;
import java.util.Optional;

import org.junit.Before;
import org.junit.Test;
import org.xwiki.contrib.repository.pypi.PypiExtensionRepository;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageInfoDto;
import org.xwiki.contrib.repository.pypi.dto.pypiJsonApi.PypiPackageJSONDto;
import org.xwiki.extension.ResolveException;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Created by Krzysztof on 21.07.2017.
 */
public class RequiredDistributionsTest
{
    private PypiExtensionRepository pypiExtensionRepository;

    @Before
    public void setUp() throws Exception
    {
        pypiExtensionRepository = mock(PypiExtensionRepository.class);
        PypiPackageJSONDto functiontoolsPackageJSONDto = new PypiPackageJSONDto();
        PypiPackageInfoDto functiontoolsPackageInfoDto = new PypiPackageInfoDto();
        functiontoolsPackageInfoDto.setVersion("2.18.1");
        functiontoolsPackageJSONDto.setInfo(functiontoolsPackageInfoDto);
        when(pypiExtensionRepository.getPypiPackageData("functools32", Optional.empty()))
                .thenReturn(functiontoolsPackageJSONDto);
    }

    @Test
    public void shouldParseMETADATAFileCorrectly_FlexGet() throws Exception
    {
        String filename = "FlexGetMETADATA";
        RequiredDistributions requiredDistributions = performParsing(filename);
        assertEquals(47, requiredDistributions.getDependencies().size());
        assertEquals("aniso8601", requiredDistributions.getDependencies().get(0).getId().split(":")[1]);
        assertEquals("1.2.1", requiredDistributions.getDependencies().get(0).getVersionConstraint().getValue());
        assertEquals("zxcvbn-python", requiredDistributions.getDependencies().get(46).getId().split(":")[1]);
        assertEquals("4.4.14", requiredDistributions.getDependencies().get(46).getVersionConstraint().getValue());
    }

    @Test
    public void shouldParseMETADATAFileCorrectly_Requests() throws Exception
    {
        String filename = "RequestsMETADATA";
        RequiredDistributions requiredDistributions = performParsing(filename);
        assertEquals(4, requiredDistributions.getDependencies().size());
        assertEquals("certifi", requiredDistributions.getDependencies().get(0).getId().split(":")[1]);
        assertEquals("2017.4.17", requiredDistributions.getDependencies().get(0).getVersionConstraint().getValue());
        assertEquals("urllib3", requiredDistributions.getDependencies().get(3).getId().split(":")[1]);
        assertEquals("1.21.1", requiredDistributions.getDependencies().get(3).getVersionConstraint().getValue());
    }

    @Test
    public void shouldParseMETADATAFileCorrectly_JsonSchema() throws Exception
    {
        String filename = "JsonSchemaMETADATA";
        RequiredDistributions requiredDistributions = performParsing(filename);
        assertEquals(1, requiredDistributions.getDependencies().size());
        assertEquals("functools32", requiredDistributions.getDependencies().get(0).getId().split(":")[1]);
        assertEquals("2.18.1", requiredDistributions.getDependencies().get(0).getVersionConstraint().getValue());
    }

    private RequiredDistributions performParsing(String filename) throws URISyntaxException, ResolveException
    {
        return RequiredDistributions.parseFile(getClass().getResourceAsStream(filename), pypiExtensionRepository);
    }
}