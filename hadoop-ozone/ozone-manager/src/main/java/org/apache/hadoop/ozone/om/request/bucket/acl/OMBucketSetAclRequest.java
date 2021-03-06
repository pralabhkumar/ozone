/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.ozone.om.request.bucket.acl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.ozone.om.OzoneManager;
import org.apache.hadoop.ozone.om.request.util.OmResponseUtil;
import org.apache.hadoop.util.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hadoop.ozone.OzoneAcl;
import org.apache.hadoop.ozone.om.OMMetrics;
import org.apache.hadoop.ozone.om.helpers.OmBucketInfo;
import org.apache.hadoop.ozone.util.BooleanBiFunction;
import org.apache.hadoop.ozone.om.response.OMClientResponse;
import org.apache.hadoop.ozone.om.response.bucket.acl.OMBucketAclResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.OMResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos.SetAclResponse;

/**
 * Handle setAcl request for bucket.
 */
public class OMBucketSetAclRequest extends OMBucketAclRequest {
  private static final Logger LOG =
      LoggerFactory.getLogger(OMBucketSetAclRequest.class);

  private static BooleanBiFunction< List<OzoneAcl>,
        OmBucketInfo > bucketAddAclOp;
  private String path;
  private List<OzoneAcl> ozoneAcls;

  static {
    bucketAddAclOp = (ozoneAcls, omBucketInfo) -> {
      return omBucketInfo.setAcls(ozoneAcls);
    };
  }

  @Override
  public OMRequest preExecute(OzoneManager ozoneManager) throws IOException {
    long modificationTime = Time.now();
    OzoneManagerProtocolProtos.SetAclRequest.Builder setAclRequestBuilder =
        getOmRequest().getSetAclRequest().toBuilder()
            .setModificationTime(modificationTime);

    return getOmRequest().toBuilder()
        .setSetAclRequest(setAclRequestBuilder)
        .setUserInfo(getUserInfo())
        .build();
  }

  public OMBucketSetAclRequest(OMRequest omRequest) {
    super(omRequest, bucketAddAclOp);
    OzoneManagerProtocolProtos.SetAclRequest setAclRequest =
        getOmRequest().getSetAclRequest();
    path = setAclRequest.getObj().getPath();
    ozoneAcls = new ArrayList<>();
    setAclRequest.getAclList().forEach(aclInfo ->
        ozoneAcls.add(OzoneAcl.fromProtobuf(aclInfo)));
  }

  @Override
  List<OzoneAcl> getAcls() {
    return ozoneAcls;
  }

  @Override
  String getPath() {
    return path;
  }

  @Override
  OMResponse.Builder onInit() {
    return OmResponseUtil.getOMResponseBuilder(getOmRequest());
  }

  @Override
  OMClientResponse onSuccess(OMResponse.Builder omResponse,
      OmBucketInfo omBucketInfo, boolean operationResult) {
    omResponse.setSuccess(operationResult);
    omResponse.setSetAclResponse(SetAclResponse.newBuilder()
        .setResponse(operationResult));
    return new OMBucketAclResponse(omResponse.build(), omBucketInfo);
  }

  @Override
  OMClientResponse onFailure(OMResponse.Builder omResponse,
      IOException exception) {
    return new OMBucketAclResponse(
        createErrorOMResponse(omResponse, exception));
  }

  @Override
  void onComplete(boolean operationResult, IOException exception,
      OMMetrics omMetrics) {
    if (operationResult) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Set acl: {} for path: {} success!", getAcls(), getPath());
      }
    } else {
      omMetrics.incNumBucketUpdateFails();
      if (exception == null) {
        LOG.error("Set acl {} for path {} failed", getAcls(), getPath());
      } else {
        LOG.error("Set acl {} for path {} failed!", getAcls(), getPath(),
            exception);
      }
    }
  }
}

