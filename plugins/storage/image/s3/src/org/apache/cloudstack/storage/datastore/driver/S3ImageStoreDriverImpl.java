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
package org.apache.cloudstack.storage.datastore.driver;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.engine.subsystem.api.storage.CopyCommandResult;
import org.apache.cloudstack.engine.subsystem.api.storage.CreateCmdResult;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObject;
import org.apache.cloudstack.engine.subsystem.api.storage.DataObjectType;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStore;
import org.apache.cloudstack.engine.subsystem.api.storage.DataStoreManager;
import org.apache.cloudstack.engine.subsystem.api.storage.DataTO;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPoint;
import org.apache.cloudstack.engine.subsystem.api.storage.EndPointSelector;
import org.apache.cloudstack.engine.subsystem.api.storage.VolumeInfo;
import org.apache.cloudstack.framework.async.AsyncCallbackDispatcher;
import org.apache.cloudstack.framework.async.AsyncCompletionCallback;
import org.apache.cloudstack.framework.async.AsyncRpcConext;
import org.apache.cloudstack.storage.command.CommandResult;
import org.apache.cloudstack.storage.datastore.db.ImageStoreDetailsDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.TemplateDataStoreVO;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreDao;
import org.apache.cloudstack.storage.datastore.db.VolumeDataStoreVO;
import org.apache.cloudstack.storage.image.ImageStoreDriver;
import org.apache.cloudstack.storage.image.store.ImageStoreImpl;
import org.apache.cloudstack.storage.image.store.TemplateObject;
import org.apache.cloudstack.storage.snapshot.SnapshotObject;
import org.apache.log4j.Logger;

import com.cloud.agent.AgentManager;
import com.cloud.agent.api.Answer;
import com.cloud.agent.api.DeleteSnapshotBackupCommand;
import com.cloud.agent.api.storage.DeleteTemplateCommand;
import com.cloud.agent.api.storage.DeleteVolumeCommand;
import com.cloud.agent.api.storage.DownloadAnswer;
import com.cloud.agent.api.to.DataStoreTO;
import com.cloud.agent.api.to.S3TO;
import com.cloud.event.EventTypes;
import com.cloud.event.UsageEventUtils;
import com.cloud.host.dao.HostDao;
import com.cloud.storage.RegisterVolumePayload;
import com.cloud.storage.Storage.ImageFormat;
import com.cloud.storage.DataStoreRole;
import com.cloud.storage.SnapshotVO;
import com.cloud.storage.VMTemplateStorageResourceAssoc;
import com.cloud.storage.VMTemplateVO;
import com.cloud.storage.VMTemplateZoneVO;
import com.cloud.storage.VolumeVO;
import com.cloud.storage.dao.SnapshotDao;
import com.cloud.storage.dao.VMTemplateDao;
import com.cloud.storage.dao.VMTemplateZoneDao;
import com.cloud.storage.dao.VolumeDao;
import com.cloud.storage.download.DownloadMonitor;
import com.cloud.storage.s3.S3Manager;
import com.cloud.storage.secondary.SecondaryStorageVmManager;
import com.cloud.storage.snapshot.SnapshotManager;
import com.cloud.storage.swift.SwiftManager;
import com.cloud.user.Account;
import com.cloud.user.dao.AccountDao;
import com.cloud.utils.exception.CloudRuntimeException;
import com.cloud.vm.UserVmVO;
import com.cloud.vm.dao.UserVmDao;

public class S3ImageStoreDriverImpl implements ImageStoreDriver {
    private static final Logger s_logger = Logger
            .getLogger(S3ImageStoreDriverImpl.class);
    @Inject
    VMTemplateZoneDao templateZoneDao;
    @Inject
    VMTemplateDao templateDao;
    @Inject DownloadMonitor _downloadMonitor;
    @Inject
    ImageStoreDetailsDao _imageStoreDetailsDao;
    @Inject VolumeDao volumeDao;
    @Inject VolumeDataStoreDao _volumeStoreDao;
    @Inject HostDao hostDao;
    @Inject SnapshotDao snapshotDao;
    @Inject AgentManager agentMgr;
    @Inject SnapshotManager snapshotMgr;
	@Inject
    private SwiftManager _swiftMgr;
    @Inject
    private S3Manager _s3Mgr;
    @Inject AccountDao _accountDao;
    @Inject UserVmDao _userVmDao;
    @Inject
    SecondaryStorageVmManager _ssvmMgr;
    @Inject
    private AgentManager _agentMgr;
    @Inject TemplateDataStoreDao _templateStoreDao;
    @Inject EndPointSelector _epSelector;
    @Inject DataStoreManager _dataStoreMgr;

    @Override
    public String grantAccess(DataObject data, EndPoint ep) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public DataTO getTO(DataObject data) {
        return null;
    }


    @Override
    public DataStoreTO getStoreTO(DataStore store) {
        ImageStoreImpl imgStore = (ImageStoreImpl)store;
        Map<String, String> details = _imageStoreDetailsDao.getDetails(imgStore.getId());
        return new S3TO(imgStore.getId(), imgStore.getUuid(), details.get(ApiConstants.S3_ACCESS_KEY),
                details.get(ApiConstants.S3_SECRET_KEY),
                details.get(ApiConstants.S3_END_POINT),
                details.get(ApiConstants.S3_BUCKET_NAME),
                details.get(ApiConstants.S3_HTTPS_FLAG) == null ? false : Boolean.parseBoolean(details.get(ApiConstants.S3_HTTPS_FLAG)),
                details.get(ApiConstants.S3_CONNECTION_TIMEOUT) == null ? null : Integer.valueOf(details.get(ApiConstants.S3_CONNECTION_TIMEOUT)),
                details.get(ApiConstants.S3_MAX_ERROR_RETRY) == null ? null : Integer.valueOf(details.get(ApiConstants.S3_MAX_ERROR_RETRY)),
                details.get(ApiConstants.S3_SOCKET_TIMEOUT) == null ? null : Integer.valueOf(details.get(ApiConstants.S3_SOCKET_TIMEOUT)),
                imgStore.getCreated());

    }

    @Override
    public boolean revokeAccess(DataObject data, EndPoint ep) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Set<DataObject> listObjects(DataStore store) {
        // TODO Auto-generated method stub
        return null;
    }

    class CreateContext<T> extends AsyncRpcConext<T> {
        final DataObject data;
        public CreateContext(AsyncCompletionCallback<T> callback, DataObject data) {
            super(callback);
            this.data = data;
        }
    }

    @Override
    public void createAsync(DataObject data,
            AsyncCompletionCallback<CreateCmdResult> callback) {

        CreateContext<CreateCmdResult> context = new CreateContext<CreateCmdResult>(callback, data);
        AsyncCallbackDispatcher<S3ImageStoreDriverImpl, DownloadAnswer> caller =
                AsyncCallbackDispatcher.create(this);
        caller.setContext(context);
        caller.setCallback(caller.getTarget().createAsyncCallback(null, null));


        if (data.getType() == DataObjectType.TEMPLATE) {
            _downloadMonitor.downloadTemplateToStorage(data, caller);
        } else if (data.getType() == DataObjectType.VOLUME) {
            _downloadMonitor.downloadVolumeToStorage(data, caller);
        }
    }

    private void deleteVolume(DataObject data, AsyncCompletionCallback<CommandResult> callback) {
        VolumeVO vol = volumeDao.findById(data.getId());
        if (s_logger.isDebugEnabled()) {
            s_logger.debug("Expunging " + vol);
        }

        // Find out if the volume is present on secondary storage
        VolumeDataStoreVO volumeStore = _volumeStoreDao.findByVolume(vol.getId());
        if (volumeStore != null) {
            if (volumeStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.DOWNLOADED) {
                DataStore store = this._dataStoreMgr.getDataStore(volumeStore.getDataStoreId(), DataStoreRole.Image);
                EndPoint ep = _epSelector.select(store);
                DeleteVolumeCommand dtCommand = new DeleteVolumeCommand(
                        store.getTO(), volumeStore.getVolumeId(), volumeStore.getInstallPath());
                Answer answer = ep.sendMessage(dtCommand);
                if (answer == null || !answer.getResult()) {
                    s_logger.debug("Failed to delete "
                            + volumeStore
                            + " due to "
                            + ((answer == null) ? "answer is null" : answer
                                    .getDetails()));
                    return;
                }
            } else if (volumeStore.getDownloadState() == VMTemplateStorageResourceAssoc.Status.DOWNLOAD_IN_PROGRESS) {
                s_logger.debug("Volume: " + vol.getName()
                        + " is currently being uploaded; cant' delete it.");
                throw new CloudRuntimeException(
                        "Please specify a volume that is not currently being uploaded.");
            }
            _volumeStoreDao.remove(volumeStore.getId());
            volumeDao.remove(vol.getId());
            CommandResult result = new CommandResult();
            callback.complete(result);
            return;
        }
    }


    protected Void createAsyncCallback(AsyncCallbackDispatcher<S3ImageStoreDriverImpl, DownloadAnswer> callback,
            CreateContext<CreateCmdResult> context) {
        DownloadAnswer answer = callback.getResult();
        DataObject obj = context.data;
        DataStore store = obj.getDataStore();

        TemplateDataStoreVO updateBuilder = _templateStoreDao.createForUpdate();
        updateBuilder.setDownloadPercent(answer.getDownloadPct());
        updateBuilder.setDownloadState(answer.getDownloadStatus());
        updateBuilder.setLastUpdated(new Date());
        updateBuilder.setErrorString(answer.getErrorString());
        updateBuilder.setJobId(answer.getJobId());
        updateBuilder.setLocalDownloadPath(answer.getDownloadPath());
        updateBuilder.setInstallPath(answer.getInstallPath());
        updateBuilder.setSize(answer.getTemplateSize());
        updateBuilder.setPhysicalSize(answer.getTemplatePhySicalSize());
        _templateStoreDao.update(store.getId(), updateBuilder);

        AsyncCompletionCallback<CreateCmdResult> caller = context.getParentCallback();

        if (answer.getDownloadStatus() == VMTemplateStorageResourceAssoc.Status.DOWNLOAD_ERROR ||
                answer.getDownloadStatus() == VMTemplateStorageResourceAssoc.Status.ABANDONED ||
                answer.getDownloadStatus() == VMTemplateStorageResourceAssoc.Status.UNKNOWN) {
            CreateCmdResult result = new CreateCmdResult(null, null);
            result.setSucess(false);
            result.setResult(answer.getErrorString());
            caller.complete(result);
        } else if (answer.getDownloadStatus() == VMTemplateStorageResourceAssoc.Status.DOWNLOADED) {
            if (answer.getCheckSum() != null) {
                VMTemplateVO templateDaoBuilder = templateDao.createForUpdate();
                templateDaoBuilder.setChecksum(answer.getCheckSum());
                templateDao.update(obj.getId(), templateDaoBuilder);
            }


            CreateCmdResult result = new CreateCmdResult(null, null);
            caller.complete(result);
        }
        return null;
    }

    private void deleteTemplate(DataObject data, AsyncCompletionCallback<CommandResult> callback) {
        TemplateObject templateObj = (TemplateObject) data;
        VMTemplateVO template = templateObj.getImage();
        ImageStoreImpl store = (ImageStoreImpl) templateObj.getDataStore();
        long storeId = store.getId();
        Long sZoneId = store.getDataCenterId();
        long templateId = template.getId();

        Account account = _accountDao.findByIdIncludingRemoved(template.getAccountId());
        String eventType = "";

        if (template.getFormat().equals(ImageFormat.ISO)) {
            eventType = EventTypes.EVENT_ISO_DELETE;
        } else {
            eventType = EventTypes.EVENT_TEMPLATE_DELETE;
        }

        // TODO: need to understand why we need to mark destroyed in
        // template_store_ref table here instead of in callback.
        // Currently I did that in callback, so I removed previous code to mark template_host_ref

        UsageEventUtils.publishUsageEvent(eventType, account.getId(), sZoneId, templateId, null, null, null);

        List<UserVmVO> userVmUsingIso = _userVmDao.listByIsoId(templateId);
        // check if there is any VM using this ISO.
        if (userVmUsingIso == null || userVmUsingIso.isEmpty()) {
             // get installpath of this template on image store
            TemplateDataStoreVO tmplStore = _templateStoreDao.findByStoreTemplate(storeId, templateId);
            String installPath = tmplStore.getInstallPath();
            if (installPath != null) {
                DeleteTemplateCommand cmd = new DeleteTemplateCommand(store.getTO(), installPath, template.getId(), template.getAccountId());
                EndPoint ep = _epSelector.select(templateObj);
                Answer answer = ep.sendMessage(cmd);

                if (answer == null || !answer.getResult()) {
                    s_logger.debug("Failed to deleted template at store: " + store.getName());
                    CommandResult result = new CommandResult();
                    result.setSucess(false);
                    result.setResult("Delete template failed");
                    callback.complete(result);

                } else {
                    s_logger.debug("Deleted template at: " + installPath);
                    CommandResult result = new CommandResult();
                    result.setSucess(false);
                    callback.complete(result);
                }

                // for S3, a template can be associated with multiple zones
                List<VMTemplateZoneVO> templateZones = templateZoneDao
                        .listByZoneTemplate(sZoneId, templateId);
                if (templateZones != null) {
                    for (VMTemplateZoneVO templateZone : templateZones) {
                        templateZoneDao.remove(templateZone.getId());
                    }
                }
            }
        }
    }

    private void deleteSnapshot(DataObject data, AsyncCompletionCallback<CommandResult> callback) {
        SnapshotObject snapshotObj = (SnapshotObject)data;
        DataStore secStore = snapshotObj.getDataStore();
        CommandResult result = new CommandResult();
        SnapshotVO snapshot = snapshotObj.getSnapshotVO();

        if (snapshot == null) {
            s_logger.debug("Destroying snapshot " + snapshotObj.getId() + " backup failed due to unable to find snapshot ");
            result.setResult("Unable to find snapshot: " + snapshotObj.getId());
            callback.complete(result);
            return;
        }

        try {
            String secondaryStoragePoolUrl = secStore.getUri();
            Long dcId = snapshot.getDataCenterId();
            Long accountId = snapshot.getAccountId();
            Long volumeId = snapshot.getVolumeId();

            String backupOfSnapshot = snapshotObj.getPath();
            if (backupOfSnapshot == null) {
                callback.complete(result);
                return;
            }

            DeleteSnapshotBackupCommand cmd = new DeleteSnapshotBackupCommand(
                    secStore.getTO(), secondaryStoragePoolUrl, dcId, accountId, volumeId,
                    backupOfSnapshot, false);
            EndPoint ep = _epSelector.select(secStore);
            Answer answer = ep.sendMessage(cmd);

            if (answer != null) {
                result.setResult(answer.getDetails());
            }
        } catch (Exception e) {
            s_logger.debug("failed to delete snapshot: " + snapshotObj.getId() + ": " + e.toString());
            result.setResult(e.toString());
        }
        callback.complete(result);
    }


    @Override
    public void deleteAsync(DataObject data,
            AsyncCompletionCallback<CommandResult> callback) {
        if (data.getType() == DataObjectType.VOLUME) {
            deleteVolume(data, callback);
        } else if (data.getType() == DataObjectType.TEMPLATE) {
            deleteTemplate(data, callback);
        } else if (data.getType() == DataObjectType.SNAPSHOT) {
        	deleteSnapshot(data, callback);
        }
    }

    @Override
    public void copyAsync(DataObject srcdata, DataObject destData,
            AsyncCompletionCallback<CopyCommandResult> callback) {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean canCopy(DataObject srcData, DataObject destData) {
        // TODO Auto-generated method stub
        return false;
    }

	@Override
	public void resize(DataObject data,
			AsyncCompletionCallback<CreateCmdResult> callback) {
		// TODO Auto-generated method stub

	}

}