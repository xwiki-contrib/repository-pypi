package org.xwiki.contrib.repository.pypi.internal.utils;

import org.junit.Test;
import org.xwiki.contrib.repository.pypi.internal.utils.PypiUtils;

import static org.junit.Assert.*;

/**
 * Created by Krzysztof on 24.07.2017.
 */
public class PypiUtilsTest
{
    @Test
    public void comparingVersionsTest() throws Exception
    {
        assertTrue(PypiUtils.isSecondVersionNewer("1.2.3", "1.2.4"));
        assertTrue(PypiUtils.isSecondVersionNewer("1.2.3", "1.12"));
        assertTrue(PypiUtils.isSecondVersionNewer("1.2.3", "1.12.3"));
        assertTrue(PypiUtils.isSecondVersionNewer("1.2", "1.12.3"));

        assertFalse(PypiUtils.isSecondVersionNewer("1.12", "1.2.3"));
        assertFalse(PypiUtils.isSecondVersionNewer("1.12", "1.12"));


    }
}