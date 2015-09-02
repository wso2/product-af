/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package impl;

import bean.CheckListItemBean;
import bean.LifecycleBean;
import bean.StageBean;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.appfactory.common.AppFactoryConstants;
import org.wso2.carbon.governance.api.exception.GovernanceException;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import service.LifecycleManagementService;

import javax.ws.rs.core.Response;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.*;

/**
 * Contains the implementation of the LifecycleManagementService
 */

public class LifecycleManagementServiceImpl implements LifecycleManagementService {
    public static final String LC_STATE_ELEMENT = "state";
    public static final String LC_DATA_MODEL_ELEMENT = "datamodel";
    public static final String LC_ATTRIBUTE_ID = "id";
    private static final String LC_DATA_ELEMENT = "data";
    private static final String LC_ITEM_ELEMENT = "item";
    private static final String LC_ATTRIBUTE_NAME = "name";
    private static final String LC_SCXML_ELEMENT = "scxml";
    private static Map<String, LifecycleBean> lifecycleMap;
    Log log = LogFactory.getLog(LifecycleManagementServiceImpl.class);

    public LifecycleManagementServiceImpl() throws LifecycleManagementException {
        init();
    }

    /**
     * Method to retrieve life cycles and their details from the registry
     *
     * @return collection of lifecycle objects
     */
    public Collection<LifecycleBean> getAllLifeCycles() {
        return lifecycleMap.values();
    }

    /**
     * Method to add the life cycles and their details to a map
     */
    private void init() throws LifecycleManagementException {
        if (lifecycleMap == null) {
            lifecycleMap = new HashMap<String, LifecycleBean>();
            LifecycleDAO lifecycleDAO = new LifecycleDAO();
                String[] lifecycleNameList = lifecycleDAO.getLifeCycleList();
                for (String LifecycleName : lifecycleNameList) {
                    LifecycleBean lifecycle = new LifecycleBean();
                    lifecycle.setLifecycleName(LifecycleName);
                    lifecycle.setStages(getAllStages(LifecycleName));
                    lifecycleMap.put(LifecycleName, lifecycle);
                }
        }
    }

    /**
     * Method to get next stage name
     *
     * @param lifecycleName name of the lifecycle
     * @param currentStage  current stage of the application
     * @return next stage name
     */
    public Response getNextStage(String lifecycleName, String currentStage) throws LifecycleManagementException {
        String nextStage = null;
        Response response;
        LifecycleBean lifecycle = lifecycleMap.get(lifecycleName);
        if (lifecycle == null) {
            String msg = "Unable to load lifecycle details of life cycle :" + lifecycleName;
            log.error(msg);
            throw new LifecycleManagementException(msg, Response.Status.NOT_FOUND);
        } else {
            Iterator<StageBean> stages = lifecycle.getStages().iterator();
            while (stages.hasNext()) {
                //if the currentStage equals to the stage name of the current stage object go to next stage object
                if (stages.next().getStageName().equals(currentStage)) {
                    if (stages.hasNext()) {
                        nextStage = stages.next().getStageName();
                    } else {
                        //if there is no any next stage to currentStage LifecycleManagementException is thrown
                        String msg =
                                "There is no stage after " + currentStage + " stage in the lifecycle :" + lifecycleName;
                        log.error(msg);
                        throw new LifecycleManagementException(msg, Response.Status.BAD_REQUEST);
                    }
                    break;
                }
            }
        }
        if (nextStage == null) {
            String msg = "There is no stage called " + currentStage + " in the lifecycle :" + lifecycleName;
            log.error(msg);
            throw new LifecycleManagementException(msg, Response.Status.NOT_FOUND);
        } else {
            response = Response.status(Response.Status.OK).entity(nextStage).build();
        }
        return response;
    }

    /**
     * Method to get previous stage name
     *
     * @param lifecycleName name of the lifecycle
     * @param currentStage  current stage of the application
     * @return previous stage name
     */
    public Response getPreviousStage(String lifecycleName, String currentStage)
            throws LifecycleManagementException {
        String previousStage = null;
        Response response;
        LifecycleBean lifecycle = lifecycleMap.get(lifecycleName);
        if (lifecycle == null) {
            String msg = "Unable to load lifecycle details of life cycle :" + lifecycleName;
            log.error(msg);
            throw new LifecycleManagementException(msg, Response.Status.NOT_FOUND);
        } else {
            ListIterator<StageBean> stages = lifecycle.getStages().listIterator();
            while (stages.hasNext()) {
                //if the currentStage equals to the stage name of the current stage object go to previous stage object
                if (stages.next().getStageName().equals(currentStage)) {
                    stages.previous();
                    if (stages.hasPrevious()) {
                        previousStage = stages.previous().getStageName();
                    } else {
                        //if there is no any previous stage to currentStage LifecycleManagementException is thrown
                        String msg =
                                "There is no stage before " + currentStage + " stage in the lifecycle :"
                                        + lifecycleName;
                        log.error(msg);
                        throw new LifecycleManagementException(msg, Response.Status.BAD_REQUEST);
                    }
                    break;
                }
            }
        }
        if (previousStage == null) {
            String msg = "There is no stage called " + currentStage + " in the lifecycle :" + lifecycleName;
            log.error(msg);
            throw new LifecycleManagementException(msg, Response.Status.NOT_FOUND);
        } else {
            response = Response.status(Response.Status.OK).entity(previousStage).build();
        }

        return response;
    }

    /**
     * Method to attach lifecycle to an application
     *
     * @param appKey       application key
     * @param appVersion   application version
     * @param tenantDomain tenant domain
     * @return response
     */
    public Response updateAppVersionLifecycle(String appKey, String appVersion, String tenantDomain) {
        Response response = null;
        LifecycleDAO dao = new LifecycleDAO();
        if (dao.isAppLifecycleChanged(appKey, tenantDomain)) {
            dao.updateAppVersionLifeCycle(appKey, tenantDomain, appVersion);
            response = Response.status(Response.Status.OK).build();
            if (log.isDebugEnabled()) {
                log.debug("Life cycle name of application :" + appKey + " with the app version" + appVersion
                        + " of the tenant :" + tenantDomain + "is successfully updated.");
            }
        }
        return response;
    }

    /**
     * Method to get stages (with checklist items)of a lifecycle
     *
     * @param lifecycleName life cycle name
     * @return array of stage objects
     */
    private List<StageBean> getAllStages(String lifecycleName) throws LifecycleManagementException {
        List<StageBean> stages = new ArrayList<StageBean>();

        LifecycleDAO lifecycleDAO = new LifecycleDAO();
        //The lifecycle configuration file is retrieved from the registry. Therefore there's no need to
        // validate it again.
        String lifecycleXml = lifecycleDAO.getLifeCycleConfiguration(lifecycleName);
        OMElement configurationElement;
        try {
            configurationElement = AXIOMUtil.stringToOM(lifecycleXml);

        } catch (XMLStreamException e) {
            String msg = "Unable to load the lifecycle configuration from registry for lifecycle :" + lifecycleName;
            log.error(msg, e);
            throw new LifecycleManagementException(e, Response.Status.NOT_FOUND);
        }

        OMElement typeElement = configurationElement.getFirstElement();
        configurationElement = typeElement.getFirstElement();
        Iterator scxmlElement = configurationElement.getChildrenWithName(new QName(LC_SCXML_ELEMENT));

        while (scxmlElement.hasNext()) {

            OMElement lifecycleElement = (OMElement) scxmlElement.next();
            Iterator stateElements = lifecycleElement.getChildrenWithName(new QName(LC_STATE_ELEMENT));

            while (stateElements.hasNext()) {

                OMElement nextStage = (OMElement) stateElements.next();
                String stageName = nextStage.getAttributeValue(new QName(LC_ATTRIBUTE_ID));

                StageBean stage = new StageBean();
                stage.setStageName(stageName);
                stages.add(stage);

                if (log.isDebugEnabled()) {
                    log.debug("Stage :" + stageName + " is successfully added to stage list of lifecycle :"
                            + lifecycleName);
                }

                Iterator dataModelElement = nextStage.getChildrenWithName(new QName(LC_DATA_MODEL_ELEMENT));

                List<CheckListItemBean> checkListItems = getCheckListItems(dataModelElement, stageName);
                stage.setCheckListItems(checkListItems);

            }
        }

        return stages;
    }

    /**
     * Method to retrieve details of a lifecycle
     *
     * @param lifecycleName the name of lifecycle, that should be associated with the application
     * @return life cycle
     */
    private LifecycleBean getLifeCycleByName(String lifecycleName) {
        return lifecycleMap.get(lifecycleName);
    }

    /**
     * Method to retrieve lifecycle details of a given appVersion artifact
     *
     * @param appKey application key
     * @return lifecycle object
     */
    public Response getCurrentAppVersionLifeCycle(String appKey, String appVersion, String tenantDomain)
            throws LifecycleManagementException {
        Response response;
        LifecycleDAO dao = new LifecycleDAO();
        String lifecycleName = dao.getLifeCycleName(appKey, appVersion, tenantDomain);
        if (lifecycleName == null) {
            String errorMsg =
                    "Unable to load the lifecycle of the application :" + appKey + " with application version :"
                            + appVersion + "of the tenant :" + tenantDomain;
            log.error(errorMsg);
            throw new LifecycleManagementException(errorMsg, Response.Status.NOT_FOUND);
        } else {
            response = Response.status(Response.Status.OK).entity(getLifeCycleByName(lifecycleName)).build();
        }
        return response;
    }

    /**
     * Method to set lifecycle name of an appInfo artifact
     *
     * @param appKey        application key
     * @param lifecycleName life cycle name
     * @param tenantDomain  tenant domain
     */
    public Response setAppLifecycle(String appKey, String lifecycleName, String tenantDomain)
            throws LifecycleManagementException {
        LifecycleDAO dao = new LifecycleDAO();
        Response response = null;
        try {
            GenericArtifact appInfoArtifact =
                    dao.getAppArtifact(appKey, AppFactoryConstants.APPLICATION_ARTIFACT_NAME, tenantDomain);

            if (appInfoArtifact != null && dao.isAppLifecycleChangeValid(appKey,lifecycleName, tenantDomain)) {
                if (appInfoArtifact.getLifecycleName() != null
                        && appInfoArtifact.getLifecycleName().equals(lifecycleName)) {
                    String msg = "Unable to update the lifecycle of the application :" + appKey + " of the tenant :"
                            + tenantDomain + ". It has already been updated with the lifecycle :" + lifecycleName;
                    log.error(msg);
                    throw new LifecycleManagementException(msg, Response.Status.BAD_REQUEST);
                } else {
                    dao.setAppInfoLifecycleName(appKey, lifecycleName, tenantDomain);
                    dao.updateAppVersionList(appKey, tenantDomain);
                    response = Response.status(Response.Status.OK).build();
                    if (log.isDebugEnabled()) {
                        log.debug("Lifecycle :" + lifecycleName + " for the application :" + appKey + " of the tenant :"
                                + tenantDomain + " is successfully added.");
                    }
                }
            } else {
                String errorMsg =
                        "Lifecycle can not be changed in the application :" + appKey + " of the tenant :"
                                + tenantDomain + " into lifecycle :" + lifecycleName;
                log.error(errorMsg);
                throw new LifecycleManagementException(errorMsg, Response.Status.BAD_REQUEST);
            }

        } catch (GovernanceException e) {
            String errorMsg =
                    "Error while loading the lifecycle name " + lifecycleName + " to the application " + appKey
                            + "of the tenant :" + tenantDomain;
            log.error(errorMsg, e);
            throw new LifecycleManagementException(e, Response.Status.NOT_FOUND);
        }
        return response;
    }

    /**
     * Method to get checklist items of a given stage
     *
     * @param dataModelElement xml element with check list items
     * @return array of stage objects
     */
    private List<CheckListItemBean> getCheckListItems(Iterator dataModelElement, String stageName) {

        List<CheckListItemBean> checkListItems = new ArrayList<CheckListItemBean>();

        OMElement nextDataModel = (OMElement) dataModelElement.next();

        Iterator dataElement = nextDataModel.getChildrenWithName(new QName(LC_DATA_ELEMENT));
        while (dataElement.hasNext()) {

            OMElement nextData = (OMElement) dataElement.next();
            Iterator checkElements = nextData.getChildrenWithName(new QName(LC_ITEM_ELEMENT));

            while (checkElements.hasNext()) {
                OMElement nextItem = (OMElement) checkElements.next();
                String itemName = nextItem.getAttributeValue(new QName(LC_ATTRIBUTE_NAME));

                CheckListItemBean checkListItem = new CheckListItemBean();
                checkListItem.setCheckItemName(itemName);
                checkListItems.add(checkListItem);

                if (log.isDebugEnabled()) {
                    log.debug("Checklist item :" + itemName
                            + "is successfully added to checklist item list of the stage :" + stageName);
                }
            }

        }
        return checkListItems;
    }

}


