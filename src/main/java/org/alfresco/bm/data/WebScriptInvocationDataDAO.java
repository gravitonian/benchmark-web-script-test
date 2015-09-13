/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.alfresco.bm.data;

import com.mongodb.BasicDBObject;
import com.mongodb.BasicDBObjectBuilder;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoException;

/**
 * Data Access Object (DAO) for Web Script (WS) Invocation data.
 *
 * @author martin.bergljung@alfresco.com
 * @since 2.0
 */
public class WebScriptInvocationDataDAO {
    /**
     * MongoDB collection that contains the WS Data
     */
    private final DBCollection collection;

    /**
     * @param db         MongoDB
     * @param collection name of DB collection containing Web Script Invocation data
     */
    public WebScriptInvocationDataDAO(DB db, String collection) {
        super();
        this.collection = db.getCollection(collection);

        // Initialize indexes
        DBObject idx_WebScriptInvocationName = BasicDBObjectBuilder
                .start(WebScriptInvocationData.FIELD_WS_INVOCATION_NAME, 1)
                .get();
        DBObject opt_WebScriptInvocationName = BasicDBObjectBuilder
                .start("name", "IDX_WS_INVOCATION_NAME")
                .add("unique", true)
                .get();
        this.collection.createIndex(idx_WebScriptInvocationName, opt_WebScriptInvocationName);
    }

    /**
     * Create a new Web Script invocation. It's state is usually set to scheduled so it will be immediately picked
     * up for processing.
     *
     * @param webScriptInvocation - the Web Script Invocation data
     * @return <tt>true</tt> if the insert was successful
     */
    public boolean createWebScriptInvocation(WebScriptInvocationData webScriptInvocation) {
        DBObject insertObj = BasicDBObjectBuilder
                .start()
                .add(WebScriptInvocationData.FIELD_WS_INVOCATION_NAME, webScriptInvocation.getName())
                .add(WebScriptInvocationData.FIELD_WS_INVOCATION_MESSAGE, webScriptInvocation.getMessage())
                .add(WebScriptInvocationData.FIELD_WS_INVOCATION_USERNAME, webScriptInvocation.getUsername())
                .add(WebScriptInvocationData.FIELD_WS_INVOCATION_STATE, webScriptInvocation.getState().toString())
                .get();
        try {
            collection.insert(insertObj);
            return true;
        } catch (MongoException e) {
            // Log and rethrow
            return false;
        }
    }

    /**
     * Find a Web Script Invocation by unique name
     *
     * @param webScriptInvocationName - the name of the Web Script invocation to find
     * @return Returns the data or <tt>null</tt> if not found
     */
    public WebScriptInvocationData findWebScriptInvocationByName(String webScriptInvocationName) {
        DBObject queryObj = BasicDBObjectBuilder
                .start()
                .add(WebScriptInvocationData.FIELD_WS_INVOCATION_NAME, webScriptInvocationName)
                .get();
        DBObject resultObj = collection.findOne(queryObj);
        if (resultObj == null) {
            return null;
        } else {
            WebScriptInvocationData result = new WebScriptInvocationData();
            String stateStr = (String) resultObj.get(WebScriptInvocationData.FIELD_WS_INVOCATION_STATE);
            DataCreationState state = DataCreationState.valueOf(stateStr);
            result.setState(state);
            result.setName((String) resultObj.get(WebScriptInvocationData.FIELD_WS_INVOCATION_NAME));
            result.setMessage((String) resultObj.get(WebScriptInvocationData.FIELD_WS_INVOCATION_MESSAGE));
            result.setUsername((String) resultObj.get(WebScriptInvocationData.FIELD_WS_INVOCATION_USERNAME));
            return result;
        }
    }

    /**
     * Set the state of a Web Script invocation
     *
     * @param webScriptInvocationName - the name of the Web Script invocation to update
     * @param state                   the new invocation state
     * @return <tt>true</tt> if the update was successful
     */
    public boolean updateWebScriptInvacationState(String webScriptInvocationName, DataCreationState state) {
        DBObject findObj = new BasicDBObject()
                .append(WebScriptInvocationData.FIELD_WS_INVOCATION_NAME, webScriptInvocationName);
        DBObject setObj = BasicDBObjectBuilder
                .start()
                .push("$set")
                .append(WebScriptInvocationData.FIELD_WS_INVOCATION_STATE, state.toString())
                .pop()
                .get();
        DBObject foundObj = collection.findAndModify(findObj, setObj);
        return foundObj != null;
    }
}

