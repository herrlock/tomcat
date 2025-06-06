/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.http.fileupload;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.tomcat.util.http.fileupload.util.mime.MimeUtility;

/**
 * A simple parser intended to parse sequences of name/value pairs.
 *
 * Parameter values are expected to be enclosed in quotes if they
 * contain unsafe characters, such as '=' characters or separators.
 * Parameter values are optional and can be omitted.
 *
 * <p>
 *  {@code param1 = value; param2 = "anything goes; really"; param3}
 * </p>
 */
public class ParameterParser {

    /**
     * String to be parsed.
     */
    private char[] chars;

    /**
     * Current position in the string.
     */
    private int pos;

    /**
     * Maximum position in the string.
     */
    private int len;

    /**
     * Start of a token.
     */
    private int i1;

    /**
     * End of a token.
     */
    private int i2;

    /**
     * Whether names stored in the map should be converted to lower case.
     */
    private boolean lowerCaseNames;

    /**
     * Default ParameterParser constructor.
     */
    public ParameterParser() {
    }

    /**
     * A helper method to process the parsed token. This method removes
     * leading and trailing blanks as well as enclosing quotation marks,
     * when necessary.
     *
     * @param quoted {@code true} if quotation marks are expected,
     *               {@code false} otherwise.
     * @return the token
     */
    private String getToken(final boolean quoted) {
        // Trim leading white spaces
        while (i1 < i2 && Character.isWhitespace(chars[i1])) {
            i1++;
        }
        // Trim trailing white spaces
        while (i2 > i1 && Character.isWhitespace(chars[i2 - 1])) {
            i2--;
        }
        // Strip away quotation marks if necessary
        if (quoted
            && i2 - i1 >= 2
            && chars[i1] == '"'
            && chars[i2 - 1] == '"') {
            i1++;
            i2--;
        }
        String result = null;
        if (i2 > i1) {
            result = new String(chars, i1, i2 - i1);
        }
        return result;
    }

    /**
     * Are there any characters left to parse?
     *
     * @return {@code true} if there are unparsed characters,
     *         {@code false} otherwise.
     */
    private boolean hasChar() {
        return pos < len;
    }

    /**
     * Returns {@code true} if parameter names are to be converted to lower
     * case when name/value pairs are parsed.
     *
     * @return {@code true} if parameter names are to be
     * converted to lower case when name/value pairs are parsed.
     * Otherwise returns {@code false}
     */
    public boolean isLowerCaseNames() {
        return lowerCaseNames;
    }

    /**
     * Tests if the given character is present in the array of characters.
     *
     * @param ch the character to test for presence in the array of characters
     * @param charray the array of characters to test against
     *
     * @return {@code true} if the character is present in the array of
     *   characters, {@code false} otherwise.
     */
    private boolean isOneOf(final char ch, final char[] charray) {
        boolean result = false;
        for (final char element : charray) {
            if (ch == element) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Extracts a map of name/value pairs from the given array of
     * characters. Names are expected to be unique.
     *
     * @param charArray the array of characters that contains a sequence of
     * name/value pairs
     * @param separator the name/value pairs separator
     *
     * @return a map of name/value pairs
     */
    public Map<String, String> parse(final char[] charArray, final char separator) {
        if (charArray == null) {
            return new HashMap<>();
        }
        return parse(charArray, 0, charArray.length, separator);
    }

    /**
     * Extracts a map of name/value pairs from the given array of characters. Names are expected to be unique.
     *
     * @param charArray the array of characters that contains a sequence of name/value pairs
     * @param offset    the initial offset.
     * @param length    the length.
     * @param separator the name/value pairs separator
     *
     * @return a map of name/value pairs
     */
    public Map<String, String> parse(final char[] charArray, final int offset, final int length, final char separator) {
        if (charArray == null) {
            return new HashMap<>();
        }
        final HashMap<String, String> params = new HashMap<>();
        chars = charArray.clone();
        pos = offset;
        len = length;
        while (hasChar()) {
            String paramName = parseToken(new char[] { '=', separator });
            String paramValue = null;
            if (hasChar() && charArray[pos] == '=') {
                pos++; // skip '='
                paramValue = parseQuotedToken(new char[] { separator });
                if (paramValue != null) {
                    try {
                        paramValue = RFC2231Utility.hasEncodedValue(paramName) ? RFC2231Utility.decodeText(paramValue)
                                : MimeUtility.decodeText(paramValue);
                    } catch (final UnsupportedEncodingException e) {
                        // let's keep the original value in this case
                    }
                }
            }
            if (hasChar() && charArray[pos] == separator) {
                pos++; // skip separator
            }
            if (paramName != null && !paramName.isEmpty()) {
                paramName = RFC2231Utility.stripDelimiter(paramName);
                if (lowerCaseNames) {
                    paramName = paramName.toLowerCase(Locale.ROOT);
                }
                params.put(paramName, paramValue);
            }
        }
        return params;
    }

    /**
     * Extracts a map of name/value pairs from the given string. Names are
     * expected to be unique.
     *
     * @param str the string that contains a sequence of name/value pairs
     * @param separator the name/value pairs separator
     *
     * @return a map of name/value pairs
     */
    public Map<String, String> parse(final String str, final char separator) {
        if (str == null) {
            return new HashMap<>();
        }
        return parse(str.toCharArray(), separator);
    }

    /**
     * Extracts a map of name/value pairs from the given string. Names are
     * expected to be unique. Multiple separators may be specified and
     * the earliest found in the input string is used.
     *
     * @param str the string that contains a sequence of name/value pairs
     * @param separators the name/value pairs separators
     *
     * @return a map of name/value pairs
     */
    public Map<String, String> parse(final String str, final char[] separators) {
        if (separators == null || separators.length == 0) {
            return new HashMap<>();
        }
        char separator = separators[0];
        if (str != null) {
            int idx = str.length();
            for (final char separator2 : separators) {
                final int tmp = str.indexOf(separator2);
                if (tmp != -1 && tmp < idx) {
                    idx = tmp;
                    separator = separator2;
                }
            }
        }
        return parse(str, separator);
    }

    /**
     * Parses out a token until any of the given terminators
     * is encountered outside the quotation marks.
     *
     * @param terminators the array of terminating characters. Any of these
     * characters when encountered outside the quotation marks signify the end
     * of the token
     *
     * @return the token
     */
    private String parseQuotedToken(final char[] terminators) {
        char ch;
        i1 = pos;
        i2 = pos;
        boolean quoted = false;
        boolean charEscaped = false;
        while (hasChar()) {
            ch = chars[pos];
            if (!quoted && isOneOf(ch, terminators)) {
                break;
            }
            if (!charEscaped && ch == '"') {
                quoted = !quoted;
            }
            charEscaped = !charEscaped && ch == '\\';
            i2++;
            pos++;

        }
        return getToken(true);
    }

    /**
     * Parses out a token until any of the given terminators
     * is encountered.
     *
     * @param terminators the array of terminating characters. Any of these
     * characters when encountered signify the end of the token
     *
     * @return the token
     */
    private String parseToken(final char[] terminators) {
        char ch;
        i1 = pos;
        i2 = pos;
        while (hasChar()) {
            ch = chars[pos];
            if (isOneOf(ch, terminators)) {
                break;
            }
            i2++;
            pos++;
        }
        return getToken(false);
    }

    /**
     * Sets the flag if parameter names are to be converted to lower case when
     * name/value pairs are parsed.
     *
     * @param b {@code true} if parameter names are to be
     * converted to lower case when name/value pairs are parsed.
     * {@code false} otherwise.
     */
    public void setLowerCaseNames(final boolean b) {
        lowerCaseNames = b;
    }

}
