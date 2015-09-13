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

/**
 * Web Script (WS) invocation data transfer object
 *
 * @author martin.bergljung@alfresco.com
 * @since 2.0
 */
public class WebScriptInvocationData {
    public static final String FIELD_WS_INVOCATION_NAME = "name";
    public static final String FIELD_WS_INVOCATION_USERNAME = "username";
    public static final String FIELD_WS_INVOCATION_MESSAGE = "message";
    public static final String FIELD_WS_INVOCATION_STATE = "state";

    private String name;
    private String username;
    private String message;
    private DataCreationState state;

    public WebScriptInvocationData() {
        state = DataCreationState.Unknown;
    }

    public String getName() {
        return name;
    }

    public void setName(String processName) {

        this.name = processName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public DataCreationState getState() {
        return state;
    }

    public void setState(DataCreationState state) {
        this.state = state;
    }
}
