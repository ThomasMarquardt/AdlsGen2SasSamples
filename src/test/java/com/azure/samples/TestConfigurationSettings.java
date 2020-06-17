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

import java.io.File;
import java.io.IOException;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;

// Configuration settings class
public class TestConfigurationSettings {
  class TestDelegationSASSettings {
    TestDelegationSASSettings(JsonParser jp) throws IOException {
      jp.nextToken();
      while (jp.getCurrentToken() != null && jp.getCurrentToken() != JsonToken.END_OBJECT) {
        if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
          switch (jp.getCurrentName()) {
            case "accountName": {
              jp.nextToken();
              accountName = jp.getText();
              break;
            }
            case "RegisteredApplication": {
              jp.nextToken();
              while (jp.getCurrentToken() != null && jp.getCurrentToken() != JsonToken.END_OBJECT) {
                if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
                  String name = jp.getCurrentName();
                  switch (name) {
                    case "appName": {
                      jp.nextToken();
                      appName = jp.getText();
                      break;
                    }
                    case "appId": {
                      jp.nextToken();
                      appId = jp.getText();
                      break;
                    }
                    case "appSecret": {
                      jp.nextToken();
                      appSecret = jp.getText();
                      break;
                    }
                    case "appServicePrincipalOID": {
                      jp.nextToken();
                      appServicePrincipalOID = jp.getText();
                      break;
                    }
                    case "appServicePrincipalTID": {
                      jp.nextToken();
                      appServicePrincipalTID = jp.getText();
                      break;
                    }
                    default: {
                      throw new IllegalArgumentException("Invalid field name encountered while parsing TestDelegationSASSettings in settings.json.");
                    }
                  }
                }
                jp.nextToken();
              }

              break;
            }
            default: {
              throw new IllegalArgumentException("Invalid field name encountered while parsing TestDelegationSASSettings in settings.json.");
            }
          }
        }
        jp.nextToken();
      }
    }
    String accountName;
    String appName;
    String appId;
    String appSecret;
    String appServicePrincipalOID;
    String appServicePrincipalTID;
  }

  class TestServiceSASSettings {
    TestServiceSASSettings(JsonParser jp) throws IOException {
      jp.nextToken();
      while (jp.getCurrentToken() != null && jp.getCurrentToken() != JsonToken.END_OBJECT) {
        if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
          switch (jp.getCurrentName()) {
            case "accountName": {
              jp.nextToken();
              accountName = jp.getText();
              break;
            }
            case "accountKey": {
              jp.nextToken();
              accountKey = jp.getText();
              break;
            }
            default: {
              throw new IllegalArgumentException("Invalid field name encountered while parsing TestDelegationSASSettings in settings.json.");
            }
          }
        }
        jp.nextToken();
      }
    }
    String accountName;
    String accountKey;
  }

  private TestDelegationSASSettings testDelegationSASSettings;
  private TestServiceSASSettings testServiceSASSettings;

  public TestConfigurationSettings() throws IOException {
    final String defaultConfigurationFile = "src/test/resources/settings.json";
    initialize(defaultConfigurationFile);
  }

  public String getTestDelegationSASAccountName() {
    return testDelegationSASSettings.accountName;
  }

  public String getTestDelegationSASAppId() {
    return testDelegationSASSettings.appId;
  }

  public String getTestDelegationSASAppSecret() {
    return testDelegationSASSettings.appSecret;
  }

  public String getTestDelegationSASAppServicePrincipalOID() {
    return testDelegationSASSettings.appServicePrincipalOID;
  }

  public String getTestDelegationSASAppServicePrincipalTID() {
    return testDelegationSASSettings.appServicePrincipalTID;
  }

  public String getTestServiceSASAccountName() {
    return testServiceSASSettings.accountName;
  }

  public String getTestServiceSASAccountKey() {
    return testServiceSASSettings.accountKey;
  }

  private void initialize(String configurationFile) throws IOException {
    JsonFactory jf = new JsonFactory();
    try (JsonParser jp = jf.createJsonParser(new File(configurationFile))) {
      jp.nextToken();
      while (jp.getCurrentToken() != null) {
        if (jp.getCurrentToken() == JsonToken.FIELD_NAME) {
          switch (jp.getCurrentName()) {
            case "TestDelegationSASSettings": {
              testDelegationSASSettings = new TestDelegationSASSettings(jp);
              break;
            }
            case "TestServiceSASSettings": {
              testServiceSASSettings = new TestServiceSASSettings(jp);
              break;
            }
          }
        }
        jp.nextToken();
      }
    }
  }
}
