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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;

/**
 * Service SAS generator.
 */
public class ServiceSasGenerator extends SasGenerator {
  private static final Logger LOG = LoggerFactory.getLogger(ServiceSasGenerator.class);
  private static final Duration SAS_START_TIME_BACKOFF = Duration.ofMinutes(5);

  private final String account;

  /**
   * Creates a Service SAS generator that uses the Storage Account key
   * to sign SAS tokens.
   * @param accountName the Storage Account name
   * @param accountKey - the Storage Account key.  This key is used to sign SAS tokens.
   */
  public ServiceSasGenerator(
      String accountName,
      String accountKey) {
    account = accountName;
    initializeMac(Base64.getDecoder().decode(accountKey));
  }

  /**
   * Generate a Service SAS with path (or blob) scope, the specified
   * expiry, and permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param path the path that will be accessed with the SAS token.  The path should not
   *             begin with / and the root path is the empty string.
   * @param expiryTime the expiration time of the SAS token.
   * @param sp the permissions granted by the SAS token.  And combination of the
   *           characters "racwdxltfmeop" is valid, but they must appear in this order.
   * @return the SAS token
   */
  public String generateSasForPath(
      String container,
      String path,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || path == null
        || (path.length() != 0 && path.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "b";
    return generateInternal(container, path, expiryTime, sp, sr, null);
  }

/**
 * Generate a Service SAS with directory scope, the specified
 * expiry, and permissions.
 * @param container the container that will be accessed with the SAS token.
 * @param directory the directory that will be accessed with the SAS token.  The directory
 *                 should not begin with / and the root path is the empty string.
 * @param expiryTime the expiration time of the SAS token.
 * @param sp the permissions granted by the SAS token.  And combination of the
 *           characters "racwdxltfmeop" is valid, but they must appear in this order.
 * @return the SAS token
 */
  public String generateSasForDirectory(
      String container,
      String directory,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || directory == null
        || (directory.length() != 0 && directory.charAt(0) == '/')  // should not begin with '/' (root path is empty string)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "d";
    final String sdd = Integer.toString(getDirectoryDepth(directory));
    return generateInternal(container, directory, expiryTime, sp, sr, sdd);
  }

  /**
   * Generate a Service SAS with container scope, the specified
   * expiry, and permissions.
   * @param container the container that will be accessed with the SAS token.
   * @param expiryTime the expiration time of the SAS token.
   * @param sp the permissions granted by the SAS token.  And combination of the
   *           characters "racwdxltfmeop" is valid, but they must appear in this order.
   * @return the SAS token
   */
  public String generateSasForContainer(
      String container,
      OffsetDateTime expiryTime,
      String sp) {
    if (isNullOrEmpty(container)
        || !isValidSignedPermission(sp)) {
      throw new IllegalArgumentException();
    }
    final String sr = "c";
    return generateInternal(container, null, expiryTime, sp, sr, null);
  }

  private String generateInternal(
      String container,
      String path,
      OffsetDateTime expiryTime,
      String sp,
      String sr,
      String sdd) {
    final String st = ISO_8601_FORMATTER.format(OffsetDateTime.now().minus(SAS_START_TIME_BACKOFF));
    final String se = ISO_8601_FORMATTER.format(expiryTime);
    final String sv = AuthenticationVersion.Feb20.toString();

    String signature = computeSignatureForSAS(container, path,
        sp, st, se, sv, sr);

    StringBuilder sb = new StringBuilder(512);
    sb.append("sp=");
    sb.append(urlEncode(sp));
    sb.append("&st=");
    sb.append(urlEncode(st));
    sb.append("&se=");
    sb.append(urlEncode(se));
    sb.append("&sv=");
    sb.append(urlEncode(sv));
    sb.append("&sr=");
    sb.append(urlEncode(sr));
    if (sdd != null) {
      sb.append("&sdd=");
      sb.append(urlEncode(sdd));
    }
    sb.append("&sig=");
    sb.append(urlEncode(signature));

    return sb.toString();
  }

  private String computeSignatureForSAS(
      String containerName, String path,
      String sp, String st, String se, String sv, String sr) {
    StringBuilder sb = new StringBuilder();
    sb.append(sp);
    sb.append("\n");
    sb.append(st);
    sb.append("\n");
    sb.append(se);
    sb.append("\n");
    // canonicalized resource
    sb.append("/blob/");
    sb.append(account);
    sb.append("/");
    sb.append(containerName);
    if (path != null && !sr.equals("c")) {
      sb.append("/");
      sb.append(path);
    }
    sb.append("\n");
    sb.append("\n"); // si
    sb.append("\n"); // sip
    sb.append("\n"); // spr
    sb.append(sv);
    sb.append("\n");
    sb.append(sr);
    sb.append("\n");
    sb.append("\n"); // - For optional : rscc - ResponseCacheControl
    sb.append("\n"); // - For optional : rscd - ResponseContentDisposition
    sb.append("\n"); // - For optional : rsce - ResponseContentEncoding
    sb.append("\n"); // - For optional : rscl - ResponseContentLanguage
    sb.append("\n"); // - For optional : rsct - ResponseContentType

    String stringToSign = sb.toString();
    LOG.debug("string-to-sign=[{}]", stringToSign.replace('\n', '.'));
    return computeHmac256(stringToSign);
  }
}