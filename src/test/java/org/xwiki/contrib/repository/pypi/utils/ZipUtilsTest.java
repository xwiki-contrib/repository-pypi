package org.xwiki.contrib.repository.pypi.utils;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by Krzysztof on 26.07.2017.
 */
public class ZipUtilsTest
{
    @Test
    public void upackTest() throws Exception
    {
        File src  = new File("D:\\XWiki\\repository-pypi\\src\\main\\resources\\luceneIndexOfValidPackages\\index.zip");
        File dest  = new File("D:\\tmp\\zipDest");
        ZipUtils.unpack(src, dest);
    }
}