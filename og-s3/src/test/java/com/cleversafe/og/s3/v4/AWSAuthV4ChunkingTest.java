/*
 * Copyright (C) 2005-2015 Cleversafe, Inc. All rights reserved.
 * 
 * Contact Information: Cleversafe, Inc. 222 South Riverside Plaza Suite 1700 Chicago, IL 60606, USA
 * 
 * licensing@cleversafe.com
 */

package com.cleversafe.og.s3.v4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import com.cleversafe.og.api.DataType;
import com.cleversafe.og.api.Method;
import com.cleversafe.og.api.Request;
import com.cleversafe.og.http.Bodies;
import com.cleversafe.og.http.Headers;
import com.cleversafe.og.http.HttpRequest;
import com.cleversafe.og.http.HttpUtil;
import com.cleversafe.og.util.io.Streams;
import com.google.common.collect.Maps;

public class AWSAuthV4ChunkingTest {

  private final URI URI;

  private static final String SECRET_KEY = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
  private static final String KEY_ID = "AKIDEXAMPLE";

  public AWSAuthV4ChunkingTest() throws URISyntaxException {
    this.URI = new URI("http://127.0.0.1:8080/container/object");
  }

  /**
   * Use the signer to get a chunked buffer without using the wrapping stream.
   */
  private byte[] getCompleteChunkedBuff(final Request request, final AWSAuthV4Chunked auth)
      throws IOException {
    final InputStream requestStream = Streams.create(request.getBody());

    final Map<String, String> headers = HttpUtil.filterOutOgHeaders(request.headers());
    auth.addChunkHeaders(request, headers);

    final AWS4SignerChunked signer = auth.getSigner(request);
    // Call getAuthheaders just to initialize this signer
    signer.getAuthHeaders(headers, Collections.<String, String>emptyMap(),
        AWS4SignerChunked.STREAMING_BODY_SHA256, KEY_ID, SECRET_KEY,
        new Date(request.getMessageTime()));


    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    final byte[] buffer = new byte[auth.getUserDataBlockSize()];
    int bytesRead;
    final boolean zeroesData = request.getBody().getDataType().equals(DataType.ZEROES);
    while ((bytesRead = requestStream.read(buffer, 0, buffer.length)) != -1) {
      // process into a chunk
      final byte[] chunk = signer.constructSignedChunk(bytesRead, buffer, zeroesData);

      // send the chunk
      outputStream.write(chunk);
    }

    // last step is to send a signed zero-length chunk to complete the upload
    final byte[] finalChunk = signer.constructSignedChunk(0, buffer, zeroesData);
    outputStream.write(finalChunk);
    outputStream.close();
    return outputStream.toByteArray();
  }

  @Test
  public void testChunking() throws IOException {
    final int userDataBlockSize = 10;
    final int bodySize = 35;
    final AWSAuthV4Chunked auth = new AWSAuthV4Chunked("dsnet", "s3", userDataBlockSize, 100);
    final HttpRequest.Builder reqBuilder = new HttpRequest.Builder(Method.PUT, this.URI);
    reqBuilder.withHeader(Headers.X_OG_USERNAME, KEY_ID);
    reqBuilder.withHeader(Headers.X_OG_PASSWORD, SECRET_KEY);
    reqBuilder.withBody(Bodies.zeroes(bodySize));
    reqBuilder.withMessageTime(1430419247000l);
    final Request request = reqBuilder.build();

    final Map<String, String> actualHeaders = auth.getAuthorizationHeaders(request);

    final Map<String, String> expectedHeaders = Maps.newHashMap();
    expectedHeaders.put("x-amz-date", "20150430T184047Z");
    expectedHeaders.put("Authorization",
        "AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20150430/dsnet/s3/aws4_request, SignedHeaders=content-encoding;date;host;x-amz-content-sha256;x-amz-date;x-amz-decoded-content-length, Signature=cce603cbbefe2709183044d8511a58a48c019b6c9c1165cab000fe3aa3589431");
    expectedHeaders.put("x-amz-decoded-content-length", "35");
    expectedHeaders.put("Host", "127.0.0.1");
    expectedHeaders.put("Date", "Thu, 30 Apr 2015 13:40:47 -0500");
    expectedHeaders.put("x-amz-content-sha256", "STREAMING-AWS4-HMAC-SHA256-PAYLOAD");
    expectedHeaders.put("content-encoding", "aws-chunked");

    Assert.assertEquals(expectedHeaders, actualHeaders);

    final byte[] actualChunkedBuff = getCompleteChunkedBuff(request, auth);
    System.out.println(BinaryUtils.toHex(actualChunkedBuff));
    final byte[] expectedChunkedBuff = BinaryUtils.fromHex(
        "613b6368756e6b2d7369676e61747572653d316163656433333232356131633330666363306432616437636461636363303663393633656537386361646237373965356632393530656333393337303933610d0a000000000000000000000d0a613b6368756e6b2d7369676e61747572653d633731346631303661353934656134383439396533666265666262323439316537366133613361366233633834663339353831386639303932313132356166620d0a000000000000000000000d0a613b6368756e6b2d7369676e61747572653d656533613230646663316136393337386637363561333030386162636135646138386664353435306335336164326337373234653535326334373966656562350d0a000000000000000000000d0a353b6368756e6b2d7369676e61747572653d313931373064356363383661343239373463306630313033663063303033616335633235613538383162626362643933366461376539356339326561323066660d0a00000000000d0a303b6368756e6b2d7369676e61747572653d666230373237623836313536333730393833613239623831323530303832623036393334393232346430653464393037663037613462363436366630356562610d0a0d0a");
    Assert.assertTrue(Arrays.equals(expectedChunkedBuff, actualChunkedBuff));
  }

  @Test
  public void wrapTest() throws IOException {
    for (int bodySize = 0; bodySize <= 5; bodySize++) {
      for (int userDataBlockSize = 1; userDataBlockSize <= bodySize; userDataBlockSize++) {
        // Build a request and auth for this body size and block size
        final AWSAuthV4Chunked auth = new AWSAuthV4Chunked("dsnet", "s3", userDataBlockSize, 100);
        final HttpRequest.Builder reqBuilder = new HttpRequest.Builder(Method.PUT, this.URI);
        reqBuilder.withHeader(Headers.X_OG_USERNAME, KEY_ID);
        reqBuilder.withHeader(Headers.X_OG_PASSWORD, SECRET_KEY);
        reqBuilder.withBody(Bodies.zeroes(bodySize));
        final Request request = reqBuilder.build();

        // Get the expected chunked buff without using a wrapped stream
        final byte[] expectedBuff = getCompleteChunkedBuff(request, auth);

        {
          // Test the wrapping stream reading 1 byte at a time
          final InputStream wrappedStream =
              auth.wrapStream(request, Streams.create(request.getBody()));

          final ByteArrayOutputStream actualOutput = new ByteArrayOutputStream();
          int read = wrappedStream.read();
          while (read != -1) {
            actualOutput.write(read);
            read = wrappedStream.read();
          }
          Assert.assertTrue("1 byte reads failed with bodySize = " + bodySize
              + ", userDataBlockSize = " + userDataBlockSize,
              Arrays.equals(expectedBuff, actualOutput.toByteArray()));
        }

        // Test the stream reading 1 - N bytes at a time
        for (int readAmount = 1; readAmount <= expectedBuff.length; readAmount++) {
          final InputStream wrappedStream =
              auth.wrapStream(request, Streams.create(request.getBody()));

          final ByteArrayOutputStream actualOutput = new ByteArrayOutputStream();
          final byte[] buff = new byte[readAmount];
          int read = wrappedStream.read(buff, 0, readAmount);
          while (read != -1) {
            actualOutput.write(buff, 0, read);
            read = wrappedStream.read(buff, 0, readAmount);
          }
          actualOutput.close();
          Assert.assertTrue(
              readAmount + " byte reads failed with bodySize = " + bodySize
                  + ", userDataBlockSize = " + userDataBlockSize,
              Arrays.equals(expectedBuff, actualOutput.toByteArray()));
        }
      }
    }
  }
}
