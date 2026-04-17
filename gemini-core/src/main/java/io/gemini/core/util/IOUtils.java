/*
 * Copyright © 2023 - present, the original author or authors. All Rights Reserved.
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
 * Utility class for I/O stream operations used throughout the Gemini AOP framework.
 * <p>
 * Provides helpers for copying streams, reading byte arrays, saving files, and
 * creating in-memory {@link URL} instances backed by byte arrays.
 * </p>
 *
 * @author   martin.liu
 */
public abstract class IOUtils {

    public static final int EOF = -1;

    public static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Copies all bytes from the given input stream to the output stream using a default buffer.
     *
     * @param inputStream  the source stream
     * @param outputStream the destination stream
     * @return the number of bytes copied
     * @throws IOException if an I/O error occurs
     */
    public static long copy(final InputStream inputStream, final OutputStream outputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");
        Assert.notNull(outputStream, "'outputStream' must not be null.");

        return copy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * Copies all bytes from the given input stream to the output stream using the given buffer.
     *
     * @param inputStream  the source stream
     * @param outputStream the destination stream
     * @param buffer       the byte buffer to use
     * @return the number of bytes copied
     * @throws IOException if an I/O error occurs
     */
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


    /**
     * Copies all characters from the given reader to the writer using a default buffer.
     *
     * @param input  the source reader
     * @param output the destination writer
     * @return the number of characters copied
     * @throws IOException if an I/O error occurs
     */
    public static long copy(final Reader input, final Writer output) throws IOException {
        Assert.notNull(input, "'input' must not be null.");
        Assert.notNull(output, "'output' must not be null.");

        return doCopy(input, output, new char[DEFAULT_BUFFER_SIZE]);
    }

    /**
     * Copies all characters from the given reader to the writer using the given buffer.
     *
     * @param input  the source reader
     * @param output the destination writer
     * @param buffer the char buffer to use
     * @return the number of characters copied
     * @throws IOException if an I/O error occurs
     */
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

    /**
     * Reads all bytes from the given input stream and returns them as a string.
     *
     * @param inputStream the source stream
     * @return the stream content as a string
     * @throws IOException if an I/O error occurs
     */
    public static String toString(final InputStream inputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doCopy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);

        return outputStream.toString();
    }

    /**
     * Reads all characters from the given reader and returns them as a string.
     *
     * @param input the source reader
     * @return the reader content as a string
     * @throws IOException if an I/O error occurs
     */
    public static String toString(final Reader input) throws IOException {
        Assert.notNull(input, "'input' must not be null.");

        final CharArrayWriter output = new CharArrayWriter();
        doCopy(input, output, new char[DEFAULT_BUFFER_SIZE]);

        return output.toString();
    }

    /**
     * Reads all bytes from the given input stream and returns them as a byte array.
     *
     * @param inputStream the source stream
     * @return the stream content as a byte array
     * @throws IOException if an I/O error occurs
     */
    public static byte[] toByteArray(final InputStream inputStream) throws IOException {
        Assert.notNull(inputStream, "'inputStream' must not be null.");

        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        doCopy(inputStream, outputStream, new byte[DEFAULT_BUFFER_SIZE]);

        return outputStream.toByteArray();
    }

    /**
     * Closes the given input stream quietly, ignoring any {@link IOException}.
     *
     * @param inStream the stream to close (may be {@code null})
     */
    public static void closeQuietly(InputStream inStream) {
        try {
            if (inStream != null)
                inStream.close();
        } catch (IOException ignored) { /**/ }
    }

    /**
     * Closes the given output stream quietly, ignoring any {@link IOException}.
     *
     * @param outStream the stream to close (may be {@code null})
     */
    public static void closeQuietly(OutputStream outStream) {
        try {
            if (outStream != null)
                outStream.close();
        } catch (IOException ignored) { /**/ }
    }

    /**
     * Writes the given byte array to the specified file path, creating parent directories as needed.
     *
     * @param sourceBytes the bytes to write
     * @param targetFile  the target file path
     * @throws IOException if an I/O error occurs
     */
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
     * Creates an in-memory {@link URL} backed by the given byte array.
     * The URL uses the {@code "byteArray"} protocol and the given path as the host.
     *
     * @param path  the path used as the URL host
     * @param bytes the byte array to serve as the URL content
     * @return the in-memory URL
     * @throws MalformedURLException if the URL cannot be constructed
     */
    public static URL toURL(String path, byte[] bytes) throws MalformedURLException {
        return new URL("byteArray", path, -1, "", new ByteArrayURLStreamHandler(bytes));
    }


    /**
     * A {@link URLStreamHandler} that serves content from an in-memory byte array.
     */
    static class ByteArrayURLStreamHandler extends URLStreamHandler {

        private final byte[] byteCode;

        /**
         * Constructs a {@code ByteArrayURLStreamHandler} with the given byte array.
         *
         * @param byteCode the byte array to serve
         */
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

    /**
     * A {@link URLConnection} that reads from an in-memory {@link InputStream}.
     */
    static class ByteArrayURLConnection extends URLConnection {

        private final InputStream inputStream;

        /**
         * Constructs a {@code ByteArrayURLConnection} for the given URL and input stream.
         *
         * @param url         the URL this connection is for
         * @param inputStream the input stream to read from
         */
        protected ByteArrayURLConnection(URL url, InputStream inputStream) {
            super(url);
            this.inputStream = inputStream;
        }

        /**
         * Marks this connection as connected.
         */
        @Override
        public void connect() {
            this.connected  = true;
        }

        /**
         * Returns the input stream for reading the byte array content.
         *
         * @return the input stream
         */
        public InputStream getInputStream() {
            connect(); 
            return inputStream;
        }
    }
}
