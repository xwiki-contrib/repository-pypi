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
package org.xwiki.contrib.repository.pypi.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import org.apache.http.HttpException;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.protocol.HttpContext;
import org.xwiki.extension.ExtensionNotFoundException;
import org.xwiki.extension.ResolveException;
import org.xwiki.extension.repository.http.internal.HttpClientFactory;

/**
 * @version $Id: 81a55f3a16b33bcf2696d0cac493b25c946b6ee4 $
 * @since 1.0
 */
public class PyPiHttpUtils
{
    public static InputStream performGet(URI uri, HttpClientFactory httpClientFactory, HttpContext localContext)
            throws HttpException
    {
        HttpGet getMethod = new HttpGet(uri);
        CloseableHttpClient httpClient = httpClientFactory.createClient(null, null);
        CloseableHttpResponse response;
        try {
            if (localContext != null) {
                response = httpClient.execute(getMethod, localContext);
            } else {
                response = httpClient.execute(getMethod);
            }
        } catch (Exception e) {
            throw new HttpException(String.format("Failed to request [%s]", getMethod.getURI()), e);
        }

        int statusCode = response.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK) {
            try {
                return response.getEntity().getContent();
            } catch (IOException e) {
                throw new HttpException(String.format("Failed to parse response body of request [%s]",
                        getMethod.getURI()), e);
            }
        } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
            return null;
        } else {
            throw new HttpException(String.format("Invalid answer [%s] from the server when requesting [%s]",
                    response.getStatusLine().getStatusCode(), getMethod.getURI()));
        }
    }
}
