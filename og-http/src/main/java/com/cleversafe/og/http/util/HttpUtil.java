//
// Copyright (C) 2005-2011 Cleversafe, Inc. All rights reserved.
//
// Contact Information:
// Cleversafe, Inc.
// 222 South Riverside Plaza
// Suite 1700
// Chicago, IL 60606, USA
//
// licensing@cleversafe.com
//
// END-OF-HEADER
//
// -----------------------
// @author: rveitch
//
// Date: Jun 26, 2014
// ---------------------

package com.cleversafe.og.http.util;

import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cleversafe.og.http.Scheme;
import com.cleversafe.og.operation.Method;
import com.cleversafe.og.util.Operation;
import com.google.common.base.Splitter;

public class HttpUtil
{
   private static final Logger _logger = LoggerFactory.getLogger(HttpUtil.class);
   private static final Splitter URI_SPLITTER = Splitter.on("/").omitEmptyStrings();
   public static final List<Integer> SUCCESS_STATUS_CODES;
   static
   {
      final List<Integer> sc = new ArrayList<Integer>();
      sc.add(200);
      sc.add(201);
      sc.add(204);
      SUCCESS_STATUS_CODES = Collections.unmodifiableList(sc);
   }

   private HttpUtil()
   {}

   public static Operation toOperation(final Method method)
   {
      checkNotNull(method);
      switch (method)
      {
         case PUT :
         case POST :
            return Operation.WRITE;
         case GET :
         case HEAD :
            return Operation.READ;
         case DELETE :
            return Operation.DELETE;
         default :
            throw new IllegalArgumentException(String.format("Unrecognized method [%s]", method));
      }
   }

   public static String getObjectName(final URI uri)
   {
      checkNotNull(uri);
      checkNotNull(uri.getScheme());
      // make sure this uri uses a known scheme
      Scheme.valueOf(uri.getScheme().toUpperCase(Locale.US));
      final List<String> parts = URI_SPLITTER.splitToList(uri.getPath());

      if (parts.size() == 3)
         return parts.get(2);

      if (parts.size() == 2)
      {
         try
         {
            // if 2 parts and first part is an api, must be soh write
            Api.valueOf(parts.get(0).toUpperCase(Locale.US));
            return null;
         }
         catch (final IllegalArgumentException e)
         {
            return parts.get(1);
         }
      }
      return null;
   }
}
