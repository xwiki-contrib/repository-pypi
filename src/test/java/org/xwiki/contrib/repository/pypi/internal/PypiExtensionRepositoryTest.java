/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.repository.pypi.internal;

import java.io.File;
import java.util.Date;
import java.util.Optional;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.environment.Environment;
import org.xwiki.extension.ExtensionManagerConfiguration;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.http.internal.DefaultHttpClientFactory;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import static org.mockito.Mockito.when;

/**
 * Validate {@link PypiExtensionRepository}.
 * 
 * @version $Id$
 */
@ComponentList(DefaultHttpClientFactory.class)
public class PypiExtensionRepositoryTest
{
    @Rule
    public MockitoComponentMockingRule<PypiExtensionRepository> mocker =
        new MockitoComponentMockingRule<>(PypiExtensionRepository.class);

    @Before
    public void before() throws Exception
    {
        this.mocker.registerMockComponent(ExtensionManagerConfiguration.class);

        Environment environment = this.mocker.getInstance(Environment.class);

        File testDirectory = new File("target/test-" + new Date().getTime()).getAbsoluteFile();
        File permdir = new File(testDirectory, "perm");
        permdir.mkdirs();
        File tempDir = new File(testDirectory, "temp");
        tempDir.mkdirs();

        when(environment.getPermanentDirectory()).thenReturn(permdir);
        when(environment.getTemporaryDirectory()).thenReturn(tempDir);
    }

    @Test
    @Ignore
    public void testRemote() throws ResolveException, ComponentLookupException
    {
        this.mocker.getComponentUnderTest().getPythonPackageExtension("txrequests", Optional.empty());
    }
}
