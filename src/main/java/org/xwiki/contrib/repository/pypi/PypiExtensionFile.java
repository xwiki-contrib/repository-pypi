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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpException;
import org.apache.http.client.protocol.HttpClientContext;
import org.xwiki.contrib.repository.pypi.utils.PyPiHttpUtils;
import org.xwiki.extension.ExtensionFile;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PypiExtensionFile implements ExtensionFile
{
    private final URI uriToDownload;

    private final long sizeOfFile;

    private final HttpClientFactory httpClientFactory;

    private final HttpClientContext localContext;

    /**
     * @param uriToDownload -
     * @param sizeOfFile -
     * @param httpClientFactory -
     */
    public PypiExtensionFile(URI uriToDownload, long sizeOfFile, HttpClientFactory httpClientFactory)
    {

        this.uriToDownload = uriToDownload;
        this.sizeOfFile = sizeOfFile;
        this.httpClientFactory = httpClientFactory;
        this.localContext = HttpClientContext.create();
    }

    @Override public long getLength()
    {
        return sizeOfFile;
    }

    @Override public InputStream openStream() throws IOException
    {
        try {
            return PyPiHttpUtils.performGet(uriToDownload, httpClientFactory, localContext);
        } catch (HttpException e) {
            throw new IOException("Failed to resolve package - wrong download URI: " + uriToDownload, e);
        }
    }
}
