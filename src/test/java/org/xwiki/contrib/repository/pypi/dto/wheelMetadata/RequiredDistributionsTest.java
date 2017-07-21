package org.xwiki.contrib.repository.pypi.dto.wheelMetadata;

import java.net.URISyntaxException;
import java.nio.file.Paths;

import org.junit.Test;
import org.xwiki.contrib.repository.pypi.TestUtils;
import org.xwiki.extension.ResolveException;

import static org.junit.Assert.*;

/**
 * Created by Krzysztof on 21.07.2017.
 */
public class RequiredDistributionsTest
{
    @Test
    public void shouldParseMETADATAFileCorrectly_FlexGet() throws Exception
    {
        String filename = "FlexGetMETADATA";
        RequiredDistributions requiredDistributions = performParsing(filename);
        assertEquals(47, requiredDistributions.getDistributions().size());
        assertEquals("aniso8601", requiredDistributions.getDistributions().get(0).getPackageName());
        assertEquals("1.2.1", requiredDistributions.getDistributions().get(0).getRequiredVersion());
        assertEquals("zxcvbn-python", requiredDistributions.getDistributions().get(46).getPackageName());
        assertEquals("4.4.14", requiredDistributions.getDistributions().get(46).getRequiredVersion());
    }

    @Test
    public void shouldParseMETADATAFileCorrectly_Requests() throws Exception
    {
        String filename = "RequestsMETADATA";
        RequiredDistributions requiredDistributions = performParsing(filename);
        assertEquals(4, requiredDistributions.getDistributions().size());
        assertEquals("certifi", requiredDistributions.getDistributions().get(0).getPackageName());
        assertEquals("2017.4.17", requiredDistributions.getDistributions().get(0).getRequiredVersion());
        assertEquals("urllib3", requiredDistributions.getDistributions().get(3).getPackageName());
        assertEquals("1.21.1", requiredDistributions.getDistributions().get(3).getRequiredVersion());
    }

    private RequiredDistributions performParsing(String filename) throws URISyntaxException, ResolveException
    {
        return RequiredDistributions.parseFile(
                Paths.get(getClass().getResource(filename).toURI()));

    }
}