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
// Date: Jun 5, 2014
// ---------------------

package com.cleversafe.og.client;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.HttpRequestExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cleversafe.og.api.Client;
import com.cleversafe.og.api.Data;
import com.cleversafe.og.api.Request;
import com.cleversafe.og.api.Response;
import com.cleversafe.og.http.Bodies;
import com.cleversafe.og.http.Headers;
import com.cleversafe.og.http.HttpAuth;
import com.cleversafe.og.http.HttpResponse;
import com.cleversafe.og.http.ResponseBodyConsumer;
import com.cleversafe.og.util.io.Streams;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ForwardingListenableFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;

/**
 * A {@code Client} implementation that uses the Apache HttpComponents HttpClient library as its
 * backing library for executing http requests
 * 
 * @since 1.0
 */
public class ApacheClient implements Client
{
   private static final Logger _logger = LoggerFactory.getLogger(ApacheClient.class);
   private static final Logger _requestLogger = LoggerFactory.getLogger("RequestLogger");
   private final int connectTimeout;
   private final int soTimeout;
   private final boolean soReuseAddress;
   private final int soLinger;
   private final boolean soKeepAlive;
   private final boolean tcpNoDelay;
   private final boolean chunkedEncoding;
   private final boolean expectContinue;
   private final int waitForContinue;
   private final HttpAuth authentication;
   private final String userAgent;
   private final long writeThroughput;
   private final long readThroughput;
   private final Map<String, ResponseBodyConsumer> responseBodyConsumers;
   private final CloseableHttpClient client;
   private final ListeningExecutorService executorService;
   private final Gson gson;

   private ApacheClient(final Builder builder)
   {
      this.connectTimeout = builder.connectTimeout;
      this.soTimeout = builder.soTimeout;
      this.soReuseAddress = builder.soReuseAddress;
      this.soLinger = builder.soLinger;
      this.soKeepAlive = builder.soKeepAlive;
      this.tcpNoDelay = builder.tcpNoDelay;
      this.chunkedEncoding = builder.chunkedEncoding;
      this.expectContinue = builder.expectContinue;
      this.waitForContinue = builder.waitForContinue;
      this.authentication = builder.authentication;
      this.userAgent = builder.userAgent;
      this.writeThroughput = builder.writeThroughput;
      this.readThroughput = builder.readThroughput;
      this.responseBodyConsumers = ImmutableMap.copyOf(builder.responseBodyConsumers);
      // perform checks on instance fields rather than builder fields
      checkArgument(this.connectTimeout >= 0, "connectTimeout must be >= 0 [%s]",
            this.connectTimeout);
      checkArgument(this.soTimeout >= 0, "soTimeout must be >= 0 [%s]", this.soTimeout);
      checkArgument(this.soLinger >= -1, "soLinger must be >= -1 [%s]", this.soLinger);
      checkArgument(this.waitForContinue > 0, "waitForContinue must be > 0 [%s]",
            this.waitForContinue);
      checkArgument(this.writeThroughput >= 0, "writeThroughput must be >= 0 [%s]",
            this.writeThroughput);
      checkArgument(this.readThroughput >= 0, "readThroughput must be >= 0 [%s]",
            this.readThroughput);

      final ThreadFactory fac = new ThreadFactoryBuilder().setNameFormat("client-%d").build();
      this.executorService = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool(fac));
      this.gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .setLongSerializationPolicy(LongSerializationPolicy.STRING)
            .create();

      final HttpClientBuilder clientBuilder = HttpClients.custom();
      if (this.userAgent != null)
         clientBuilder.setUserAgent(this.userAgent);

      this.client = clientBuilder
            // TODO HTTPS: setHostnameVerifier, setSslcontext, and SetSSLSocketFactory methods
            // TODO investigate ConnectionConfig, particularly bufferSize and fragmentSizeHint
            // TODO defaultCredentialsProvider and defaultAuthSchemeRegistry for pre/passive auth?
            .setRequestExecutor(new HttpRequestExecutor(this.waitForContinue))
            .setMaxConnTotal(Integer.MAX_VALUE)
            .setMaxConnPerRoute(Integer.MAX_VALUE)
            .setDefaultSocketConfig(SocketConfig.custom()
                  .setSoTimeout(this.soTimeout)
                  .setSoReuseAddress(this.soReuseAddress)
                  .setSoLinger(this.soLinger)
                  .setSoKeepAlive(this.soKeepAlive)
                  .setTcpNoDelay(this.tcpNoDelay)
                  .build())
            .setConnectionReuseStrategy(DefaultConnectionReuseStrategy.INSTANCE)
            .setKeepAliveStrategy(DefaultConnectionKeepAliveStrategy.INSTANCE)
            .disableConnectionState()
            .disableCookieManagement()
            .disableContentCompression()
            .disableAuthCaching()
            .disableAutomaticRetries()
            .setRedirectStrategy(new CustomRedirectStrategy())
            .setDefaultRequestConfig(RequestConfig.custom()
                  .setExpectContinueEnabled(this.expectContinue)
                  // TODO investigate performance impact of stale check (30ms reported)
                  .setStaleConnectionCheckEnabled(true)
                  .setRedirectsEnabled(true)
                  .setRelativeRedirectsAllowed(true)
                  .setConnectTimeout(this.connectTimeout)
                  .setSocketTimeout(this.soTimeout)
                  // TODO should this be infinite? length of time allowed to request a connection
                  // from the pool
                  .setConnectionRequestTimeout(0)
                  .build())
            .build();
   }

   @Override
   public ListenableFuture<Response> execute(final Request request)
   {
      checkNotNull(request);
      final HttpUriRequest apacheRequest = createRequest(request);
      final ListenableFuture<Response> baseFuture =
            this.executorService.submit(new BlockingHttpOperation(request, apacheRequest));

      return new ForwardingListenableFuture.SimpleForwardingListenableFuture<Response>(baseFuture)
      {
         @Override
         public boolean cancel(final boolean mayInterruptIfRunning)
         {
            apacheRequest.abort();
            return delegate().cancel(mayInterruptIfRunning);
         }
      };
   }

   private HttpUriRequest createRequest(final Request request)
   {
      final RequestBuilder builder = RequestBuilder.create(request.getMethod().toString())
            .setUri(request.getUri());

      if (this.authentication != null)
         builder.addHeader("Authorization", this.authentication.nextAuthorizationHeader(request));

      for (final Entry<String, String> header : request.headers().entrySet())
      {
         builder.addHeader(header.getKey(), header.getValue());
      }

      if (Data.NONE != request.getBody().getData())
         builder.setEntity(createEntity(request));

      return builder.build();
   }

   private HttpEntity createEntity(final Request request)
   {
      // TODO verify httpclient consumes request entity correctly automatically
      // TODO may need to implement a custom HttpEntity that returns false for isStreaming call,
      // if this makes a performance difference
      final InputStream stream = Streams.create(request.getBody());
      final InputStreamEntity entity = new ThrottledEntity(stream, request.getBody().getSize());
      // TODO chunk size for chunked encoding is hardcoded to 2048 bytes. Can only be overridden
      // by implementing a custom connection factory
      entity.setChunked(this.chunkedEncoding);
      return entity;
   }

   class ThrottledEntity extends InputStreamEntity
   {
      public ThrottledEntity(final InputStream instream, final long length)
      {
         super(instream, length);
      }

      @Override
      public void writeTo(OutputStream outstream) throws IOException
      {
         if (ApacheClient.this.writeThroughput > 0)
            outstream = Streams.throttle(outstream, ApacheClient.this.writeThroughput);
         super.writeTo(outstream);
      }
   }

   @Override
   public ListenableFuture<Boolean> shutdown(final boolean immediate)
   {
      final SettableFuture<Boolean> future = SettableFuture.create();
      final Thread t = new Thread(getShutdownRunnable(future, immediate));
      t.setName("clientShutdown");
      t.start();
      return future;
   }

   private Runnable getShutdownRunnable(
         final SettableFuture<Boolean> future,
         final boolean immediate)
   {
      return new Runnable()
      {
         @Override
         public void run()
         {
            if (immediate)
               closeSockets();

            shutdownClient();
            future.set(true);
         }

         private void closeSockets()
         {
            try
            {
               _logger.info("Attempting to close client connection pool");
               ApacheClient.this.client.close();
               _logger.info("Client connection pool is closed");
            }
            catch (final IOException e)
            {
               _logger.error("Error closing client connection pool", e);
            }
         }

         private void shutdownClient()
         {
            _logger.info("Issuing client shutdown");
            ApacheClient.this.executorService.shutdown();
            while (!ApacheClient.this.executorService.isTerminated())
            {
               awaitShutdown(1, TimeUnit.HOURS);
            }
            _logger.info("Client is shutdown");
         }

         private void awaitShutdown(final long timeout, final TimeUnit unit)
         {
            try
            {
               _logger.info("Awaiting client executor service termination for {} {}", timeout, unit);
               final boolean result =
                     ApacheClient.this.executorService.awaitTermination(timeout, unit);
               _logger.info("Client executor service termination result [{}]", result
                     ? "success"
                     : "failure");
            }
            catch (final InterruptedException e)
            {
               _logger.error("Interrupted while waiting for client executor service termination", e);
            }
         }
      };
   }
   private class BlockingHttpOperation implements Callable<Response>
   {
      private final Request request;
      private final HttpUriRequest apacheRequest;
      private final byte[] buf;

      public BlockingHttpOperation(final Request request, final HttpUriRequest apacheRequest)
      {
         this.request = request;
         this.apacheRequest = apacheRequest;
         // TODO inject buf size from config
         this.buf = new byte[4096];
      }

      @Override
      public Response call()
      {
         final long timestampStart = System.currentTimeMillis();
         final HttpResponse.Builder responseBuilder = new HttpResponse.Builder();
         final String requestId = this.request.metadata().get(Headers.X_OG_REQUEST_ID);
         if (requestId != null)
            responseBuilder.withMetadata(Headers.X_OG_REQUEST_ID, requestId);
         final Response response;
         try
         {
            sendRequest(this.apacheRequest, responseBuilder);
         }
         catch (final Exception e)
         {
            _logger.error("Exception executing request", e);
            responseBuilder.withStatusCode(499).withMetadata(Headers.X_OG_ABORTED, "");
         }
         response = responseBuilder.build();
         final long timestampFinish = System.currentTimeMillis();

         final RequestLogEntry entry =
               new RequestLogEntry(this.request, response, timestampStart, timestampFinish);
         _requestLogger.info(ApacheClient.this.gson.toJson(entry));

         return response;
      }

      private void sendRequest(
            final HttpUriRequest apacheRequest,
            final HttpResponse.Builder responseBuilder) throws IOException
      {
         ApacheClient.this.client.execute(apacheRequest, new ResponseHandler<Void>()
         {
            @Override
            public Void handleResponse(final org.apache.http.HttpResponse response)
                  throws IOException
            {
               setResponseStatusCode(responseBuilder, response);
               setResponseHeaders(responseBuilder, response);
               receiveResponseContent(responseBuilder, response);
               return null;
            }
         });
      }

      private void setResponseStatusCode(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response)
      {
         responseBuilder.withStatusCode(response.getStatusLine().getStatusCode());
      }

      private void setResponseHeaders(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response)
      {
         final HeaderIterator headers = response.headerIterator();
         while (headers.hasNext())
         {
            final Header header = headers.nextHeader();
            // TODO header value may be null, is this acceptable?
            responseBuilder.withHeader(header.getName(), header.getValue());
         }
      }

      private void receiveResponseContent(
            final HttpResponse.Builder responseBuilder,
            final org.apache.http.HttpResponse response) throws IOException
      {
         final HttpEntity entity = response.getEntity();
         if (entity != null)
         {
            InputStream in = entity.getContent();
            final long readThroughput = ApacheClient.this.readThroughput;
            if (readThroughput > 0)
               in = Streams.throttle(in, readThroughput);

            // TODO clean this up, should always try to set response entity to response size;
            // will InstrumentedInputStream help with this?
            final String consumerId =
                  this.request.metadata().get(Headers.X_OG_RESPONSE_BODY_CONSUMER);
            final ResponseBodyConsumer consumer =
                  ApacheClient.this.responseBodyConsumers.get(consumerId);
            if (consumer != null)
            {
               final Iterator<Entry<String, String>> it =
                     consumer.consume(response.getStatusLine().getStatusCode(), in);
               while (it.hasNext())
               {
                  final Entry<String, String> e = it.next();
                  responseBuilder.withMetadata(e.getKey(), e.getValue());
               }
            }
            else
            {
               consumeBytes(responseBuilder, in);
            }
         }
      }

      private void consumeBytes(
            final HttpResponse.Builder responseBuilder,
            final InputStream responseContent) throws IOException
      {
         long totalBytes = 0;
         int bytesRead;
         while ((bytesRead = responseContent.read(this.buf)) > 0)
         {
            totalBytes += bytesRead;
         }

         if (totalBytes > 0)
            responseBuilder.withBody(Bodies.zeroes(totalBytes));
      }
   }

   @Override
   public String toString()
   {
      return String.format("ApacheClient [%n"
            + "connectTimeout=%s,%n"
            + "soTimeout=%s,%n"
            + "soReuseAddress=%s,%n"
            + "soLinger=%s,%n"
            + "soKeepAlive=%s,%n"
            + "tcpNoDelay=%s,%n"
            + "chunkedEncoding=%s,%n"
            + "expectContinue=%s,%n"
            + "waitForContinue=%s,%n"
            + "authentication=%s,%n"
            + "userAgent=%s,%n"
            + "writeThroughput=%s,%n"
            + "readThroughput=%s%n]",
            this.connectTimeout,
            this.soTimeout,
            this.soReuseAddress,
            this.soLinger,
            this.soKeepAlive,
            this.tcpNoDelay,
            this.chunkedEncoding,
            this.expectContinue,
            this.waitForContinue,
            this.authentication,
            this.userAgent,
            this.writeThroughput,
            this.readThroughput);
   }

   /**
    * A builder of apache client instances
    */
   public static class Builder
   {
      private int connectTimeout;
      private int soTimeout;
      private boolean soReuseAddress;
      private int soLinger;
      private boolean soKeepAlive;
      private boolean tcpNoDelay;
      private boolean chunkedEncoding;
      private boolean expectContinue;
      private int waitForContinue;
      private HttpAuth authentication;
      private String userAgent;
      private long writeThroughput;
      private long readThroughput;
      private final Map<String, ResponseBodyConsumer> responseBodyConsumers;

      /**
       * Constructs a new builder
       */
      public Builder()
      {
         this.connectTimeout = 0;
         this.soTimeout = 0;
         this.soReuseAddress = false;
         this.soLinger = -1;
         this.soKeepAlive = true;
         this.tcpNoDelay = true;
         this.chunkedEncoding = false;
         this.expectContinue = false;
         this.waitForContinue = 3000;
         this.writeThroughput = 0;
         this.readThroughput = 0;
         this.responseBodyConsumers = Maps.newHashMap();
      }

      /**
       * Configures the timeout in milliseconds until a connection is established. A timeout of zero
       * is interpreted as an infinite timeout
       * 
       * @param connectTimeout
       *           connection open timeout, in milliseconds
       * @return this builder
       */
      public Builder withConnectTimeout(final int connectTimeout)
      {
         this.connectTimeout = connectTimeout;
         return this;
      }

      /**
       * Configures the socket {@code SO_TIMEOUT} timeout in milliseconds, the maximum duration
       * between consecutive packets. A timeout of zero is interpreted as an infinite timeout
       * 
       * @param soTimeout
       *           socket timeout, in milliseconds
       * @return this builder
       */
      public Builder withSoTimeout(final int soTimeout)
      {
         this.soTimeout = soTimeout;
         return this;
      }

      /**
       * Configures the {@code SO_REUSEADDR} socket option
       * 
       * @param soReuseAddress
       *           socket reuse flag
       * @return this builder
       */
      public Builder usingSoReuseAddress(final boolean soReuseAddress)
      {
         this.soReuseAddress = soReuseAddress;
         return this;
      }

      /**
       * Configures {@code SO_LINGER} in <em>seconds</em>. A linger of zero disables linger, and a
       * linger of {@code -1} uses the system default.
       * 
       * @param soLinger
       *           linger, in seconds
       * @return this builder
       */
      public Builder withSoLinger(final int soLinger)
      {
         this.soLinger = soLinger;
         return this;
      }

      /**
       * Configures the {@code SO_KEEPALIVE} socket option
       * 
       * @param soKeepAlive
       *           keepalive flag
       * @return this builder
       */
      public Builder usingSoKeepAlive(final boolean soKeepAlive)
      {
         this.soKeepAlive = soKeepAlive;
         return this;
      }

      /**
       * Configures the {@code TCP_NODELAY} socket option
       * 
       * @param tcpNoDelay
       *           tcp no delay flag
       * @return this builder
       */
      public Builder usingTcpNoDelay(final boolean tcpNoDelay)
      {
         this.tcpNoDelay = tcpNoDelay;
         return this;
      }

      /**
       * Configures the use of http chunked encoding for request bodies
       * 
       * @param chunkedEncoding
       *           chunked encoding flag
       * @return this builder
       */
      public Builder usingChunkedEncoding(final boolean chunkedEncoding)
      {
         this.chunkedEncoding = chunkedEncoding;
         return this;
      }

      /**
       * Configures the use of expect: 100-continue flag for PUT and POST requests
       * 
       * @param expectContinue
       *           expect continue flag
       * @return this builder
       */
      public Builder usingExpectContinue(final boolean expectContinue)
      {
         this.expectContinue = expectContinue;
         return this;
      }

      /**
       * Configure the duration to wait for a continue response from the target host after sending a
       * 100-continue message prior to continuing with the request. Duration is in milliseconds
       * 
       * @param waitForContinue
       *           wait for continue duration, in milliseconds
       * @return this builder
       */
      public Builder withWaitForContinue(final int waitForContinue)
      {
         this.waitForContinue = waitForContinue;
         return this;
      }

      /**
       * Configures the use of authentication for every request
       * 
       * @param authentication
       *           the authentication type to use
       * @return this builder
       */
      public Builder withAuthentication(final HttpAuth authentication)
      {
         this.authentication = authentication;
         return this;
      }

      /**
       * Configures the user-agent request header to send with every request
       * 
       * @param userAgent
       *           the user agent string to send
       * @return this builder
       */
      public Builder withUserAgent(final String userAgent)
      {
         this.userAgent = userAgent;
         return this;
      }

      /**
       * Configures throughput throttling for PUT and POST requests
       * 
       * @param bytesPerSecond
       *           maximum throughput
       * @return this builder
       */
      public Builder withWriteThroughput(final long bytesPerSecond)
      {
         this.writeThroughput = bytesPerSecond;
         return this;
      }

      /**
       * Configures throughput throttling for GET and HEAD requests
       * 
       * @param bytesPerSecond
       *           maximum throughput
       * @return this builder
       */
      public Builder withReadThroughput(final long bytesPerSecond)
      {
         this.readThroughput = bytesPerSecond;
         return this;
      }

      /**
       * Configures a response body consumer to be used to process response bodies for requests
       * configured with a matching consumerId
       * 
       * @param consumerId
       *           the consumerId for which the provided consumer should be used
       * @param consumer
       *           a response body consumer
       * @return this builder
       * @see Headers#X_OG_RESPONSE_BODY_CONSUMER
       */
      public Builder withResponseBodyConsumer(
            final String consumerId,
            final ResponseBodyConsumer consumer)
      {
         this.responseBodyConsumers.put(consumerId, consumer);
         return this;
      }

      /**
       * Constructs a new apache client instance
       * 
       * @return an apache client instance
       * @throws IllegalArgumentException
       *            if connectTimeout, soTimeout, writeThroughput, or readThroughput are negative
       * @throws IllegalArgumentException
       *            if soLinger is less than {@code -1}
       * @throws IllegalArgumentException
       *            if waitForContinue is negative or zero
       */
      public ApacheClient build()
      {
         return new ApacheClient(this);
      }
   }
}
