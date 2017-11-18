package org.xwiki.contrib.repository.pypi.internal.dto.packagesInJython;

import org.junit.Test;
import org.xwiki.contrib.repository.pypi.internal.dto.packagesInJython.PackagesInJython;

import static org.junit.Assert.*;

/**
 * Created by Krzysztof on 25.07.2017.
 */
public class PackagesInJythonTest
{
    @Test
    public void shouldReadFileWithListedPackages() throws Exception
    {
        PackagesInJython packagesIncludedInJython = PackagesInJython.getPackagesIncludedInJython();
        assertEquals(210, packagesIncludedInJython.getPackages().size());
    }
}