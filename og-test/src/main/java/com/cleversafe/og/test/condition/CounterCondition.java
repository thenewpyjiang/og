/*
 * Copyright (C) 2005-2015 Cleversafe, Inc. All rights reserved.
 * 
 * Contact Information: Cleversafe, Inc. 222 South Riverside Plaza Suite 1700 Chicago, IL 60606, USA
 * 
 * licensing@cleversafe.com
 */

package com.cleversafe.og.test.condition;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.cleversafe.og.api.Request;
import com.cleversafe.og.api.Response;
import com.cleversafe.og.statistic.Counter;
import com.cleversafe.og.statistic.Statistics;
import com.cleversafe.og.test.LoadTest;
import com.cleversafe.og.util.Operation;
import com.cleversafe.og.util.Pair;
import com.google.common.eventbus.Subscribe;

/**
 * A test condition which is triggered when a counter reaches a threshold value
 * 
 * @since 1.0
 */
public class CounterCondition implements TestCondition {
  protected final Operation operation;
  private final Counter counter;
  protected final long thresholdValue;
  protected final LoadTest test;
  private final Statistics stats;

  /**
   * Creates an instance
   * 
   * @param operation the operation type to query
   * @param counter the counter to query
   * @param thresholdValue the value at which this condition should be triggered
   * @param test the load test to stop when this condition is triggered
   * @param stats the statistics instance to query
   * @throws NullPointerException if operation, counter, test, or stats is null
   * @throws IllegalArgumentException if thresholdValue is zero or negative
   */
  public CounterCondition(final Operation operation, final Counter counter,
      final long thresholdValue, final LoadTest test, final Statistics stats) {
    this.operation = checkNotNull(operation);
    this.counter = checkNotNull(counter);
    checkArgument(thresholdValue > 0, "thresholdValue must be > 0 [%s]", thresholdValue);
    this.thresholdValue = thresholdValue;
    this.test = checkNotNull(test);
    this.stats = checkNotNull(stats);
  }

  /**
   * Triggers a check of this condition
   * 
   * @param operation a completed request
   */
  @Subscribe
  public void update(final Pair<Request, Response> operation) {
    if (isTriggered()) {
      this.test.stopTest();
    }
  }

  @Override
  public boolean isTriggered() {
    final long currentValue = this.stats.get(this.operation, this.counter);
    if (currentValue >= this.thresholdValue) {
      return true;
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format(
        "CounterCondition [%n" + "operation=%s,%n" + "counter=%s,%n" + "thresholdValue=%s%n" + "]",
        this.operation, this.counter, this.thresholdValue);
  }
}
