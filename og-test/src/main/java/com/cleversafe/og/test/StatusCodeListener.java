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
// Date: Jun 22, 2014
// ---------------------

package com.cleversafe.og.test;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cleversafe.og.statistic.Statistics;
import com.cleversafe.og.util.OperationType;
import com.google.common.eventbus.Subscribe;

public class StatusCodeListener
{
   private static Logger _logger = LoggerFactory.getLogger(StatusCodeListener.class);
   private final LoadTest test;
   private final OperationType operation;
   private final int statusCode;
   private final long thresholdValue;

   public StatusCodeListener(
         final LoadTest test,
         final OperationType operation,
         final int statusCode,
         final long thresholdValue)
   {
      this.test = checkNotNull(test);
      this.operation = checkNotNull(operation);
      // TODO use guava range
      checkArgument(statusCode >= 100 && statusCode <= 599,
            "statusCode must be in range [100, 599] [%s]", statusCode);
      this.statusCode = statusCode;
      checkArgument(thresholdValue > 0, "thresholdValue must be > 0 [%s]", thresholdValue);
      this.thresholdValue = thresholdValue;
   }

   @Subscribe
   public void handleStatisticEvent(final Statistics stats)
   {
      final long currentValue = stats.getStatusCode(this.operation, this.statusCode);
      if (currentValue >= this.thresholdValue)
         this.test.stopTest();
   }
}
