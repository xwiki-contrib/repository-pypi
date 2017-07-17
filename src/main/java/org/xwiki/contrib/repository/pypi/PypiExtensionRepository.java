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
package org.xwiki.contrib.repository.pypi;

import org.slf4j.Logger;
import org.xwiki.extension.Extension;
import org.xwiki.extension.ExtensionDependency;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.ExtensionLicenseManager;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.internal.ExtensionFactory;
import org.xwiki.extension.repository.AbstractExtensionRepository;
import org.xwiki.extension.repository.ExtensionRepositoryDescriptor;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;
import org.xwiki.extension.repository.result.IterableResult;
import org.xwiki.extension.version.Version;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiExtensionRepository extends AbstractExtensionRepository
{
    private final ExtensionLicenseManager licenseManager;

    private final ExtensionFactory extensionFactory;

    private final HttpClientFactory httpClientFactory;

    private final Logger logger;

    /**
     *
     * @param extensionRepositoryDescriptor -
     * @param licenseManager -
     * @param extensionFactory -
     * @param httpClientFactory -
     * @param logger -
     */
    public PypiExtensionRepository(ExtensionRepositoryDescriptor extensionRepositoryDescriptor,
            ExtensionLicenseManager licenseManager, ExtensionFactory extensionFactory,
            HttpClientFactory httpClientFactory,
            Logger logger)
    {
        super(extensionRepositoryDescriptor);
        this.licenseManager = licenseManager;
        this.extensionFactory = extensionFactory;
        this.httpClientFactory = httpClientFactory;
        this.logger = logger;
    }

    @Override public Extension resolve(ExtensionId extensionId) throws ResolveException
    {
        return null;
    }

    @Override public Extension resolve(ExtensionDependency extensionDependency) throws ResolveException
    {
        return null;
    }

    @Override public IterableResult<Version> resolveVersions(String s, int i, int i1) throws ResolveException
    {
        return null;
    }
}
