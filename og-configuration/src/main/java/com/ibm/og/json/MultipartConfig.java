/* Copyright (c) IBM Corporation 2016. All Rights Reserved.
 * Project name: Object Generator
 * This project is licensed under the Apache License 2.0, see LICENSE.
 */

package com.ibm.og.json;

public class MultipartConfig {
  public SelectionConfig<Long> partSize;
  public int targetSessions;

  public MultipartConfig() {
    this.partSize = null;
    this.targetSessions = 1;
  }
}
