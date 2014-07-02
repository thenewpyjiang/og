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
// @author: shoran
//
// Date: Aug 19, 2013
// ---------------------

package com.cleversafe.og.client;

import java.net.URI;
import java.util.Locale;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import com.cleversafe.og.operation.EntityType;
import com.cleversafe.og.operation.Metadata;
import com.cleversafe.og.operation.Method;
import com.cleversafe.og.operation.Request;
import com.cleversafe.og.operation.Response;
import com.google.common.net.HttpHeaders;

public class RequestLogEntry
{
   final String type = "http";
   final String serverName;
   final String remoteAddress;
   final String user;
   final long timestampStart;
   final long timestampFinish;
   final String timeStart;
   final String timeFinish;
   final Method requestMethod;
   final String requestUri;
   final String objectId;
   final int status;
   final long requestLength;
   final long responseLength;
   final String userAgent;
   final long requestLatency;

   final String clientRequestId;
   final String requestId;
   final String stat;
   final Long objectLength;

   private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern(
         "dd/MMM/yyyy:HH:mm:ss Z").withLocale(Locale.US);
   private static final String X_CLV_REQUEST_ID = "X-Clv-Request-Id";

   public RequestLogEntry(
         final Request request,
         final Response response,
         final long timestampStart,
         final long timestampFinish)
   {
      final URI uri = request.getUri();
      // FIXME reliably get localaddress? Name should be clientName? Do we even need this field?
      this.serverName = null;
      this.remoteAddress = uri.getHost();
      this.user = request.getMetadata(Metadata.USERNAME);
      this.timestampStart = timestampStart;
      this.timestampFinish = timestampFinish;
      this.timeStart = RequestLogEntry.FORMATTER.print(this.timestampStart);
      this.timeFinish = RequestLogEntry.FORMATTER.print(this.timestampFinish);
      this.requestMethod = request.getMethod();

      this.requestUri = uri.getPath() + (uri.getQuery() != null ? uri.getQuery() : "");

      String objectName = request.getMetadata(Metadata.OBJECT_NAME);
      // SOH writes
      if (objectName == null)
         objectName = response.getMetadata(Metadata.OBJECT_NAME);
      this.objectId = objectName;

      long objectSize = 0;
      if (EntityType.NONE != request.getEntity().getType())
         objectSize = request.getEntity().getSize();

      this.status = response.getStatusCode();
      // TODO requestLength will not equal objectLength with AWSv4 request overhead
      this.requestLength = objectSize;
      // TODO is this correct?
      this.responseLength = response.getEntity().getSize();
      this.userAgent = request.getHeader(HttpHeaders.USER_AGENT);
      // TODO ask: dsnet access.log uses System.currentTimeMillis() - request.getTimeStamp();
      this.requestLatency = this.timestampFinish - this.timestampStart;

      // custom
      this.clientRequestId = String.valueOf(request.getId());
      this.requestId = response.getHeader(X_CLV_REQUEST_ID);
      this.stat = null;
      this.objectLength = objectSize;
   }
}
