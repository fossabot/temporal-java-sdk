/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.internal;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.Deadline;
import io.grpc.MethodDescriptor;
import io.temporal.proto.workflowservice.WorkflowServiceGrpc;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Set RPC call deadlines according to ServiceFactoryOptions. */
class GrpcDeadlineInterceptor implements ClientInterceptor {

  private static final Logger log = LoggerFactory.getLogger(GrpcDeadlineInterceptor.class);

  private final WorkflowServiceStubsOptions options;

  GrpcDeadlineInterceptor(WorkflowServiceStubsOptions options) {
    this.options = options;
  }

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
    Deadline deadline = callOptions.getDeadline();
    long duration;
    if (deadline == null) {
      duration = options.getRpcTimeoutMillis();
    } else {
      duration = deadline.timeRemaining(TimeUnit.MILLISECONDS);
    }
    if (method == WorkflowServiceGrpc.getGetWorkflowExecutionHistoryMethod()) {
      if (deadline == null) {
        duration = options.getRpcLongPollTimeoutMillis();
      } else {
        duration = deadline.timeRemaining(TimeUnit.MILLISECONDS);
        if (duration > options.getRpcLongPollTimeoutMillis()) {
          duration = options.getRpcLongPollTimeoutMillis();
        }
      }
    } else if (method == WorkflowServiceGrpc.getPollForDecisionTaskMethod()
        || method == WorkflowServiceGrpc.getPollForActivityTaskMethod()) {
      duration = options.getRpcLongPollTimeoutMillis();
    } else if (method == WorkflowServiceGrpc.getQueryWorkflowMethod()) {
      duration = options.getRpcQueryTimeoutMillis();
    }
    if (log.isTraceEnabled()) {
      String name = method.getFullMethodName();
      log.trace("method=" + name + ", timeoutMs=" + duration);
    }
    return next.newCall(method, callOptions.withDeadlineAfter(duration, TimeUnit.MILLISECONDS));
  }
}
