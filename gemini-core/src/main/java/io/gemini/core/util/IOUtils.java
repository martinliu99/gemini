/*
 * Copyright © 2023, the original author or authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gemini.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * 
 * @author martin.liu
 *
 */
public class IOUtils {

    public static final int EOF = -1;

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    public static long copy(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");
        Assert.notNull(outputStream, "'outputStream' must not be null.");

        return copy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);
    }

    public static long copy(final InputStream inputStream, final OutputStream outputStream, final byte[] buffer) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");
        Assert.notNull(outputStream, "'outputStream' must not be null.");
        Assert.notNull(buffer, "'buffer' must not be null.");

        return doCopy(inputStream, outputStream, buffer);
    }

    private static long doCopy(final InputStream inputStream, final OutputStream outputStream, final byte[] buffer) throws IOException {
        long count = 0;
        int n;
        while (EOF != (n = inputStream.read(buffer))) {
            outputStream.write(buffer, 0, n);
            count += n;
        }
        return count;
    }


    public static long copy(final Reader input, final Writer output) throws IOException {
        Assert.notNull(input, "'input' must not be null.");
        Assert.notNull(output, "'output' must not be null.");

        return doCopy(input, output, new char[DEFAULT_BUFFER_SIZE]);
    }

    public static long copy(final Reader input, final Writer output, final char[] buffer) throws IOException {
        Assert.notNull(input, "'input' must not be null.");
        Assert.notNull(output, "'output' must not be null.");
        Assert.notNull(buffer, "'buffer' must not be null.");

        return doCopy(input, output, buffer);
    }

    private static long doCopy(final Reader input, final Writer output, final char[] buffer) throws IOException {
        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    public static String toString(final InputStream inputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doCopy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);

        return outputStream.toString();
    }

    public static String toString(final Reader input) throws IOException {
        Assert.notNull(input, "'input' must not be null.");

        final CharArrayWriter output = new CharArrayWriter();
        doCopy(input, output, new char[DEFAULT_BUFFER_SIZE]);

        return output.toString();
    }

    public static byte[] toByteArray(final InputStream inputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doCopy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);

        return outputStream.toByteArray();
    }

    public static void closeQuietly(InputStream inStream) {
        try {
            if(inStream != null)
                inStream.close();
        } catch (IOException ignored) { /**/ }
    }

    public static void closeQuietly(OutputStream outStream) {
        try {
            if(outStream != null)
                outStream.close();
        } catch (IOException ignored) { /**/ }
    }

    public static void saveToFile(byte[] sourceBytes, String targetFile) throws IOException {
        File file = new File(targetFile);
        file.getParentFile().mkdir();
        OutputStream os = new FileOutputStream(file);
        try {
            os.write(sourceBytes);
        } finally {
            closeQuietly(os);
        }
    }

    /**
     * 
     * @param path
     * @param bytes
     * @return
     * @throws MalformedURLException
     */
    public static URL toURL(String path, byte[] bytes) throws MalformedURLException {
        return new URL("byteArray", path, -1, "", new ByteArrayURLStreamHandler(bytes));
    }


    static class ByteArrayURLStreamHandler extends URLStreamHandler {

        private final byte[] byteCode;

        public ByteArrayURLStreamHandler(byte[] byteCode) {
            this.byteCode = byteCode;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            return new ByteArrayURLConnection(u, new ByteArrayInputStream(byteCode));
        }
        
    }

    static class ByteArrayURLConnection extends URLConnection {

        private final InputStream inputStream;

        /**
         * @param url
         */
        protected ByteArrayURLConnection(URL url, InputStream inputStream) {
            super(url);
            this.inputStream = inputStream;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void connect() {
            this.connected  = true;
        }

        public InputStream getInputStream() {
            connect(); 
            return inputStream;
        }
    }
}
