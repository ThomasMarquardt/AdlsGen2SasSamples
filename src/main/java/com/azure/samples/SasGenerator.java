/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.azure.samples;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

/**
 * SAS generator base class.
 */
abstract class SasGenerator {
  private static final String VALID_PERMISSION_ORDER = "racwdxltfmeop";

  // ISO-8601 date formatter
  protected static final DateTimeFormatter ISO_8601_FORMATTER =
      DateTimeFormatter
          .ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
          .withZone(ZoneId.of("UTC"));

  // Get signed directory depth.  The value does not begin with forward
  // slash and the root folder is the empty string.
  protected static int getDirectoryDepth(String value) {
    int count = 0;
    if (isNullOrEmpty(value)) {
      return count;
    }
    count++;
    for (int i = 0; i < value.length(); i++) {
      if ('/' == value.charAt(i)) {
        count++;
      }
    }
    return count;
  }

  // Determines if a string is null or empty.
  protected static boolean isNullOrEmpty(String value) {
    return value == null || value.length() == 0;
  }

  // Validate a signed permission parameter.
  protected static boolean isValidSignedPermission(String sp) {
    if (isNullOrEmpty(sp)) {
      return false;
    }
    int offset = 0;
    for (char ch: sp.toCharArray())
    {
      boolean found = false;
      for (int i = offset; i < VALID_PERMISSION_ORDER.length(); i++)
      {
        if (ch == VALID_PERMISSION_ORDER.charAt(i)) {
          offset = i + 1;
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }
    return true;
  }

  // URL encode a string but replace ' ' with %20 and don't encode '/'.
  protected static String urlEncode(final String value) {
    try {
      return URLEncoder.encode(value, "utf-8")
          .replace("+", "%20")
          .replace("%2F", "/");
    } catch(UnsupportedEncodingException ex) {
      throw new IllegalArgumentException("The string " + value + " cannot be URL encoded.", ex);
    }
  }

  enum AuthenticationVersion {
    Feb20("2020-02-10");

    private final String ver;

    AuthenticationVersion(String version) {
      this.ver = version;
    }

    @Override
    public String toString() {
      return ver;
    }
  }

  private Mac hmacSha256;

  // hide default constructor
  protected SasGenerator() {
  }

  /**
   * Called by subclasses to initialize the cryptographic SHA-256 HMAC provider.
   * @param key - a 256-bit secret key
   */
  protected void initializeMac(byte[] key) {
    // Initializes the HMAC-SHA256 provider.
    try {
      synchronized (this) {
        hmacSha256 = Mac.getInstance("HmacSHA256");
        hmacSha256.init(new SecretKeySpec(key, "HmacSHA256"));
      }
    } catch (final Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  protected String computeHmac256(final String stringToSign) {
    byte[] utf8Bytes;
    try {
      utf8Bytes = stringToSign.getBytes(StandardCharsets.UTF_8.toString());
    } catch (final UnsupportedEncodingException e) {
      throw new IllegalArgumentException(e);
    }
    byte[] hmac;
    synchronized (this) {
      hmac = hmacSha256.doFinal(utf8Bytes);
    }
    return Base64.getEncoder().encodeToString(hmac);
  }
}