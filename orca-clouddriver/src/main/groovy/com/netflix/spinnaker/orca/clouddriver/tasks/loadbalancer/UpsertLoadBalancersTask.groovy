/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.loadbalancer

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.KatoService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/*
 * Task that can create multiple load balancers
 */
@Component
class UpsertLoadBalancersTask extends AbstractCloudProviderAwareTask implements RetryableTask {

  static final String CLOUD_OPERATION_TYPE = "upsertLoadBalancer"

  @Override
  long getBackoffPeriod() {
    return 2000
  }

  @Override
  long getTimeout() {
    return 300000
  }

  @Autowired
  KatoService kato

  @Autowired
  ObjectMapper mapper

  @Override
  TaskResult execute(StageExecution stage) {
    String cloudProvider = getCloudProvider(stage)

    def operations = new ArrayList()

    def context = new HashMap(stage.context)
    for (Map loadBalancer : context.loadBalancers) {
      def lbcontext = new HashMap(loadBalancer)
      lbcontext.availabilityZones = lbcontext.availabilityZones ?: [(lbcontext.region): lbcontext.regionZones]
      def operation = new HashMap()
      operation.put(CLOUD_OPERATION_TYPE, lbcontext)
      operations.add(operation)
    }

    def taskId = kato.requestOperations(cloudProvider, operations)

    def outputs = [
            "notification.type"   : CLOUD_OPERATION_TYPE.toLowerCase(),
            "kato.result.expected": true,
            "kato.last.task.id"   : taskId,
            "targets"             : operations.collect {
              [
                      credentials      : it[CLOUD_OPERATION_TYPE].account,
                      availabilityZones: it[CLOUD_OPERATION_TYPE].availabilityZones,
                      vpcId            : it[CLOUD_OPERATION_TYPE].vpcId,
                      name             : it[CLOUD_OPERATION_TYPE].name,
              ]
            }
    ]
    TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
  }
}
