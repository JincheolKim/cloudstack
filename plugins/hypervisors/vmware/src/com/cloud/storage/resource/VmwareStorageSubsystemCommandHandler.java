/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.cloud.storage.resource;

import com.cloud.agent.api.Answer;
import com.cloud.agent.api.to.DataObjectType;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.DataTO;
import com.cloud.agent.api.to.NfsTO;
import com.cloud.agent.api.to.S3TO;
import com.cloud.agent.api.to.SwiftTO;
import com.cloud.hypervisor.vmware.manager.VmwareStorageManager;
import com.cloud.storage.DataStoreRole;
import org.apache.cloudstack.storage.command.CopyCmdAnswer;
import org.apache.cloudstack.storage.command.CopyCommand;
import org.apache.cloudstack.storage.to.SnapshotObjectTO;

import java.io.File;

public class VmwareStorageSubsystemCommandHandler extends StorageSubsystemCommandHandlerBase {

    private VmwareStorageManager storageManager;
    private PremiumSecondaryStorageResource storageResource;

    public PremiumSecondaryStorageResource getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(PremiumSecondaryStorageResource storageResource) {
        this.storageResource = storageResource;
    }

    public VmwareStorageManager getStorageManager() {
        return storageManager;
    }

    public void setStorageManager(VmwareStorageManager storageManager) {
        this.storageManager = storageManager;
    }

    public VmwareStorageSubsystemCommandHandler(StorageProcessor processor
                                               ) {
        super(processor);
    }


    @Override
    protected Answer execute(CopyCommand cmd) {
        DataTO srcData = cmd.getSrcTO();
        DataTO destData = cmd.getDestTO();
        DataStoreTO srcDataStore = srcData.getDataStore();
        DataStoreTO destDataStore = destData.getDataStore();
        //if copied between s3 and nfs cache, go to resource
        boolean needDelegation = false;
        if (destDataStore instanceof NfsTO
                && destDataStore.getRole() == DataStoreRole.ImageCache) {
            if (srcDataStore instanceof S3TO || srcDataStore instanceof SwiftTO) {
                needDelegation = true;
            }
        }

        if (srcDataStore.getRole() == DataStoreRole.ImageCache && destDataStore.getRole() == DataStoreRole.Image) {
            needDelegation = true;
        }

        if (srcData.getObjectType() == DataObjectType.SNAPSHOT && srcData.getDataStore().getRole() == DataStoreRole.Primary) {
            //for back up snapshot, we need to do backup to cache, then to object store if object store is used.
            if (cmd.getCacheTO() != null) {
                cmd.setDestTO(cmd.getCacheTO());

                CopyCmdAnswer answer = (CopyCmdAnswer)processor.backupSnapshot(cmd);
                if (!answer.getResult()) {
                    return answer;
                }
                NfsTO cacheStore = (NfsTO)cmd.getCacheTO().getDataStore();
                String parentPath = storageResource.getRootDir(cacheStore.getUrl());
                SnapshotObjectTO newSnapshot = (SnapshotObjectTO)answer.getNewData();
                String path = newSnapshot.getPath();
                int index = path.lastIndexOf(File.separator);
                String name = path.substring(index + 1);
                String dir = path.substring(0, index);
                storageManager.createOva(parentPath + File.separator + dir, name);
                newSnapshot.setPath(newSnapshot.getPath() + ".ova");
                newSnapshot.setDataStore(cmd.getCacheTO().getDataStore());
                CopyCommand newCmd = new CopyCommand(newSnapshot, destData, cmd.getWait(), cmd.executeInSequence());
                return storageResource.defaultAction(newCmd);
            }
        }

        if (needDelegation) {
            return storageResource.defaultAction(cmd);
        } else {
            return super.execute(cmd);
        }
    }
}
