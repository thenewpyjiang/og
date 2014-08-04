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
// Date: Jul 15, 2014
// ---------------------

package com.cleversafe.og.soh;

import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map.Entry;

import org.junit.Assert;
import org.junit.Test;

import com.cleversafe.og.operation.Metadata;
import com.google.common.base.Charsets;

public class SOHWriteResponseBodyConsumerTest
{
   @Test(expected = NullPointerException.class)
   public void testNullInputStream() throws IOException
   {
      new SOHWriteResponseBodyConsumer().consume(201, null);
   }

   @Test
   public void testInvalidStatusCode() throws IOException
   {
      final SOHWriteResponseBodyConsumer c = new SOHWriteResponseBodyConsumer();
      final InputStream in = mock(InputStream.class);
      final Iterator<Entry<String, String>> it = c.consume(500, in);
      Assert.assertFalse(it.hasNext());
   }

   @Test
   public void testConsume() throws IOException
   {
      final SOHWriteResponseBodyConsumer consumer = new SOHWriteResponseBodyConsumer();
      final StringBuilder s = new StringBuilder();
      for (int i = 0; i < 10000; i++)
      {
         s.append("objectName").append(i).append("\n");
      }
      final InputStream in = new ByteArrayInputStream(s.toString().getBytes(Charsets.UTF_8));
      final Iterator<Entry<String, String>> it = consumer.consume(201, in);
      Assert.assertTrue(it.hasNext());
      final Entry<String, String> e = it.next();
      Assert.assertEquals(Metadata.OBJECT_NAME.toString(), e.getKey());
      Assert.assertEquals("objectName0", e.getValue());
      Assert.assertFalse(it.hasNext());
      Assert.assertEquals(0, in.available());
   }
}
