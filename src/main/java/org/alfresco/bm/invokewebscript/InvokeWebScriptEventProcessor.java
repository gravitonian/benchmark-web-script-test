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
package org.alfresco.bm.invokewebscript;

import java.net.URLEncoder;
import java.util.Collections;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.data.WebScriptInvocationData;
import org.alfresco.bm.data.WebScriptInvocationDataDAO;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.http.AuthenticatedHttpEventProcessor;

import org.alfresco.bm.user.UserData;
import org.alfresco.bm.user.UserDataService;
import org.alfresco.http.AuthenticationDetailsProvider;
import org.alfresco.http.HttpClientProvider;
import org.alfresco.http.SimpleHttpRequestCallback;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;

/**
 * Invoke a Web Script (WS) with a Message.
 * <p/>
 * <h1>Input</h1>
 * <p/>
 * A {@link WebScriptInvocationData data object } containing the message to be sent as parameter to Web Script, and
 * the username to use when authenticating.
 * <p/>
 * <h1>Data</h1>
 * <p/>
 * A MongoDB collection containing Web Script Invocation data.
 * <p/>
 * <h1>Actions</h1>
 * <p/>
 * Fetches the Web Script Invocation data from the MongoDB collection and invokes the Hello World Web Script with it.
 * After that the invocation is marked as done and the result is recorded.
 * <p/>
 * <h1>Output</h1>
 * <p/>
 * {@link #EVENT_NAME_WEB_SCRIPT_INVOCATION_DONE}: The Web Script Invocation name<br/>
 *
 * @author martin.bergljung@alfresco.com
 * @since 2.0
 */
public class InvokeWebScriptEventProcessor extends AuthenticatedHttpEventProcessor {
    public static final String EVENT_NAME_WEB_SCRIPT_INVOCATION_DONE = "webScriptInvocationDone";

    /**
     * Hello World Web Script Service URL
     */
    private static final String HELLO_WORLD_WS_URL = "/alfresco/service/sample/helloworld?message=";

    /**
     * User data service to get hold of usernames to use for Web Script invocations.
     * There must be some users created in Alfresco (and in the mirror) for this to work.
     * This can be done via the Sign-Up test.
     */
    private final UserDataService userDataService;

    /**
     * Web Script Invocation data access object
     */
    private final WebScriptInvocationDataDAO webScriptInvocationDataDAO;

    /**
     * Name of the event denoting WS Invocation is done
     */
    private String eventNameWebScriptInvocationDone;

    /**
     * @param httpClientProvider
     * @param authenticationDetailsProvider
     * @param baseUrl
     * @param webScriptInvocationDataDAO    general DAO for accessing data
     * @param userDataService
     */
    public InvokeWebScriptEventProcessor(
            HttpClientProvider httpClientProvider,
            AuthenticationDetailsProvider authenticationDetailsProvider,
            String baseUrl,
            WebScriptInvocationDataDAO webScriptInvocationDataDAO,
            UserDataService userDataService) {
        super(httpClientProvider, authenticationDetailsProvider, baseUrl);
        this.userDataService = userDataService;
        this.webScriptInvocationDataDAO = webScriptInvocationDataDAO;
        this.eventNameWebScriptInvocationDone = EVENT_NAME_WEB_SCRIPT_INVOCATION_DONE;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception {
        // Usually, the entire method is timed but we can choose to control this
        super.suspendTimer();

        // Get the Web Script Invocation name
        String webScriptInvocationName = (String) event.getData();

        // Locate the Web Script Invocation data and make a quick check on it
        WebScriptInvocationData webScriptInvocationData =
                webScriptInvocationDataDAO.findWebScriptInvocationByName(webScriptInvocationName);
        EventResult result = null;
        if (webScriptInvocationData == null) {
            result = new EventResult(
                    "Skipping processing for '" + webScriptInvocationName + "'.  Web Script Invocation data not found.",
                    false);
            return result;
        } else if (webScriptInvocationData.getState() != DataCreationState.Scheduled) {
            result = new EventResult(
                    "Skipping processing for '" + webScriptInvocationName + "'.  Web Script Invocation not scheduled.",
                    false);
            return result;
        } else if (webScriptInvocationData.getUsername() == null) {
            result = new EventResult(
                    "Skipping processing for '" + webScriptInvocationName + "'.  Web Script Invocation has no username.",
                    false);
            return result;
        } else if (webScriptInvocationData.getMessage() == null) {
            result = new EventResult(
                    "Skipping processing for '" + webScriptInvocationName + "'.  Web Script Invocation has no message.",
                    false);
            return result;
        }

        EventResult eventResult = null;

        // Look up the user data for the username that will be used to authenticate and invoke the Web Script
        UserData user = userDataService.findUserByUsername(webScriptInvocationData.getUsername());
        if (user == null) {
            eventResult = new EventResult("User data not found in local database: " +
                    webScriptInvocationData.getUsername(), Collections.EMPTY_LIST,
                    false);
            return eventResult;
        }

        // Start the clock that times the Web Script call
        resumeTimer();

        // Make the Web Script call authenticated as username
        // WebScript Call will have a URL looking something like:
        //    http://localhost:8080/alfresco/service/sample/helloworld?message=Message%200000003
        HttpGet webScriptInvocationGet = new HttpGet(
                getFullUrlForPath(HELLO_WORLD_WS_URL +
                        URLEncoder.encode(webScriptInvocationData.getMessage(), "UTF-8")));
        HttpResponse httpResponse =
                executeHttpMethodAsUser(webScriptInvocationGet, webScriptInvocationData.getUsername(),
                SimpleHttpRequestCallback.getInstance());
        StatusLine httpStatus = httpResponse.getStatusLine();

        // Stop the clock, we are done with the Web Script call
        suspendTimer();

        // Check if the Alfresco server responded with OK
        if (httpStatus.getStatusCode() == HttpStatus.SC_OK) {
            // Record the name of the Web Script Invocation to reflect that is was executed on the Alfresco server
            boolean updated = webScriptInvocationDataDAO.updateWebScriptInvacationState(
                    webScriptInvocationName, DataCreationState.Created);
            if (updated) {
                // Create 'done' event, which will not have any further associated event processors
                Event doneEvent = new Event(eventNameWebScriptInvocationDone, 0L, webScriptInvocationName);
                eventResult = new EventResult("Web Script Invocation " + webScriptInvocationName +
                        " completed.", doneEvent);
            } else {
                throw new RuntimeException("Web Script Invocation " + webScriptInvocationName +
                        " was executed but not recorded.");
            }
        } else {
            // Web Script Invocation failed
            String msg = String.format("Web Script call failed, ReST-call resulted in status:%d with error %s ",
                    httpStatus.getStatusCode(), httpStatus.getReasonPhrase());
            eventResult = new EventResult(msg, Collections.<Event>emptyList(), false);
            webScriptInvocationDataDAO.updateWebScriptInvacationState(webScriptInvocationName, DataCreationState.Failed);
        }

        return eventResult;
    }
}
