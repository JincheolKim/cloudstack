// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.engine.datacenter.entity.api.db.dao;

import java.util.List;
import java.util.Map;

import org.apache.cloudstack.engine.datacenter.entity.api.DataCenterResourceEntity;
import org.apache.cloudstack.engine.datacenter.entity.api.db.EngineClusterVO;


import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.utils.db.GenericDao;
import com.cloud.utils.fsm.StateDao;

public interface EngineClusterDao extends GenericDao<EngineClusterVO, Long>, StateDao<DataCenterResourceEntity.State, DataCenterResourceEntity.State.Event, DataCenterResourceEntity> {
    List<EngineClusterVO> listByPodId(long podId);
    EngineClusterVO findBy(String name, long podId);
    List<EngineClusterVO> listByHyTypeWithoutGuid(String hyType);
    List<EngineClusterVO> listByZoneId(long zoneId);

    List<HypervisorType> getAvailableHypervisorInZone(Long zoneId);
    List<EngineClusterVO> listByDcHyType(long dcId, String hyType);
    Map<Long, List<Long>> getPodClusterIdMap(List<Long> clusterIds);
    List<Long> listDisabledClusters(long zoneId, Long podId);
    List<Long> listClustersWithDisabledPods(long zoneId);
}
