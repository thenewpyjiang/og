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
// Date: Jan 21, 2014
// ---------------------

package com.cleversafe.oom.operation;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * An <code>Operation</code> implementation for HTTP requests and responses.
 */
public class HTTPOperation extends BaseOperation
{
   private URL url;
   private HTTPMethod method;
   private final Map<String, String> requestHeaders;
   private int responseCode;
   private final Map<String, String> responseHeaders;

   /**
    * Constructs an <code>HTTPOperation</code> instance.
    * 
    * @param operationType
    *           the type of operation
    * @param url
    *           the url for this request
    * @throws NullPointerException
    *            if operationType is null
    * @throws IllegalArgumentException
    *            if operationType is ALL
    * @throws NullPointerException
    *            if url is null
    */
   public HTTPOperation(final OperationType operationType, final URL url, final HTTPMethod method)
   {
      super(operationType);
      this.url = checkNotNull(url, "url must not be null");
      this.method = checkNotNull(method, "method must not be null");
      this.requestHeaders = new HashMap<String, String>();
      this.responseCode = -1;
      this.responseHeaders = new HashMap<String, String>();
   }

   /**
    * Gets the request url for this operation.
    * 
    * @return the request url for this operation
    */
   public URL getURL()
   {
      return this.url;
   }

   /**
    * Sets the request url for this operation.
    * 
    * @param url
    *           the url for this request
    * @throws NullPointerException
    *            if url is null
    */
   public void setURL(final URL url)
   {
      this.url = checkNotNull(url, "url must not be null");
   }

   /**
    * Gets the request method for this operation.
    * 
    * @return the request method for this operation
    */
   public HTTPMethod getMethod()
   {
      return this.method;
   }

   /**
    * Sets the request method for this operation
    * 
    * @param method
    *           the request method for this operation
    * @throws NullPointerException
    *            if method is null
    */
   public void setMethod(final HTTPMethod method)
   {
      this.method = checkNotNull(method, "method must not be null");
   }

   /**
    * Gets the value of the request header with the specified key.
    * 
    * @param key
    *           the key of the header
    * @return the value for the header with the specified key, or null if no such mapping exists
    * @throws NullPointerException
    *            if key is null
    */
   public String getRequestHeader(final String key)
   {
      checkNotNull(key, "key must not be null");
      return this.requestHeaders.get(key);
   }

   /**
    * Sets a request header with the specified key/value pair.
    * 
    * @param key
    *           the key of the header
    * @param value
    *           the value of the header
    * @throws NullPointerException
    *            if key is null
    * @throws NullPointerException
    *            if value is null
    */
   public void setRequestHeader(final String key, final String value)
   {
      checkNotNull(key, "key must not be null");
      checkNotNull(value, "value must not be null");
      this.requestHeaders.put(key, value);
   }

   /**
    * Produces an iterator over the configured request headers.
    * 
    * @return a request header iterator
    */
   public Iterator<Entry<String, String>> requestHeaderIterator()
   {
      return this.requestHeaders.entrySet().iterator();
   }

   /**
    * Gets the response code for this operation.
    * 
    * @return the response code for this operation, or -1 if the response code has not been set
    */
   public int getResponseCode()
   {
      return this.responseCode;
   }

   /**
    * Sets the response code for this operation.
    * 
    * @param code
    *           the response code for this operation
    * @throws IllegalArgumentException
    *            if code is less than one
    */
   public void setResponseCode(final int code)
   {
      checkArgument(code > 0, "code must be > 0 [%s]", code);
      this.responseCode = code;
   }

   /**
    * Gets the value of the response header with the specified key.
    * 
    * @param key
    *           the key of the header
    * @return the value for the header with the specified key, or null if no such mapping exists
    * @throws NullPointerException
    *            if key is null
    */
   public String getResponseHeader(final String key)
   {
      checkNotNull(key, "key must not be null");
      return this.responseHeaders.get(key);
   }

   /**
    * Sets a response header with the specified key/value pair.
    * 
    * @param key
    *           the key of the header
    * @param value
    *           the value of the header
    * @throws NullPointerException
    *            if key is null
    * @throws NullPointerException
    *            if value is null
    */
   public void setResponseHeader(final String key, final String value)
   {
      checkNotNull(key, "key must not be null");
      checkNotNull(value, "value must not be null");
      this.responseHeaders.put(key, value);
   }

   /**
    * Produces an iterator over the configured response headers.
    * 
    * @return a response header iterator
    */
   public Iterator<Entry<String, String>> responseHeaderIterator()
   {
      return this.responseHeaders.entrySet().iterator();
   }
}