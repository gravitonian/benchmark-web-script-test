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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.alfresco.bm.data.DataCreationState;
import org.alfresco.bm.data.WebScriptInvocationData;
import org.alfresco.bm.data.WebScriptInvocationDataDAO;
import org.alfresco.bm.event.AbstractEventProcessor;
import org.alfresco.bm.event.Event;
import org.alfresco.bm.event.EventResult;
import org.alfresco.bm.user.UserDataService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Schedule a number of Alfresco Repo Web Script invocations (Hello World Web Script from AIO SDK Project).
 * This is done by writing batches of Web Script Invocation data objects to the Database (MongoDB).
 * For each data object written to the database an event is also created.
 * <p/>
 * These Web Script invocation events can then be picked up by any number of started "Web Script Test Drivers".
 * <p/>
 * <h1>Input</h1>
 * <p/>
 * No input requirements
 * <p/>
 * <h1>Data</h1>
 * <p/>
 * A MongoDB collection containing unprocessed Web Script invocation data.
 * <p/>
 * <h1>Actions</h1>
 * <p/>
 * Scheduled up to a batch size after which this processor reschedules itself.
 * <p/>
 * <h1>Output</h1>
 * <p/>
 * Scheduled up to 100:
 * {@link #EVENT_NAME_WEB_SCRIPT_INVOCATION}: The Web Script Invocation event name<br/>
 *
 * @author martin.bergljung@alfresco.com
 * @since 2.0
 */
public class ScheduleWebScriptInvocationsEventProcessor extends AbstractEventProcessor {
    private static Log logger = LogFactory.getLog(ScheduleWebScriptInvocationsEventProcessor.class);

    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final String EVENT_NAME_WEB_SCRIPT_INVOCATION = "webScriptInvocation";

    /**
     * User data service to get hold of usernames to use for Web Script invocation authentication.
     * There must be some users created in Alfresco (and in the mirror) for this to work.
     * This can be done via the Sign-Up test.
     */
    private final UserDataService userDataService;

    /**
     * Web Script Invocation data access object
     */
    private final WebScriptInvocationDataDAO webScriptInvocationDataDAO;

    /**
     * Fully qualified Name (FQN) for the active Test Run
     */
    private final String testRunFqn;

    /**
     * Total number of Web Script invocations that should be executed.
     */
    private long numberOfWebScriptInvocations;

    /**
     * The delay (millisec) between each Web Script invocation.
     */
    private final long timeBetweenWebScriptInvocations;

    /**
     * A pattern for how the generated Web Script message parameter value should look like.
     * Will be looking like:
     * "Message 0000001"
     * "Message 0000002"
     * etc.
     */
    private String webScriptMessagePattern;

    /**
     * The event name for a Web Script Invocation, will trigger an event processor that calls the Web Script.
     */
    private String eventNameWebScriptInvocation;

    /**
     * The number of Web Script invocations that should be scheduled in one go.
     * For example, if we should make in total 500 Web Script invocations, then we can
     * set the batch size to 100, and then have the invocations starting before all
     * have been scheduled.
     */
    private int batchSize;

    /**
     * @param userDataService                 user service for fetching username etc (requires Sign-Up test to have been run)
     * @param webScriptInvocationDataDAO      the DAO for storing Web Script invocation data
     * @param testRunFqn                      the name of the test run
     * @param numberOfWebScriptInvocations    the number of Web Script invocations to execute in total
     * @param timeBetweenWebScriptInvocations how long between each invocation
     * @param webScriptMessagePattern         a pattern for how the generated Web Script message parameter value should look like.
     */
    public ScheduleWebScriptInvocationsEventProcessor(UserDataService userDataService,
                                                      WebScriptInvocationDataDAO webScriptInvocationDataDAO,
                                                      String testRunFqn, int numberOfWebScriptInvocations, long timeBetweenWebScriptInvocations,
                                                      String webScriptMessagePattern) {
        super();
        this.userDataService = userDataService;
        this.webScriptInvocationDataDAO = webScriptInvocationDataDAO;
        this.testRunFqn = testRunFqn;
        this.numberOfWebScriptInvocations = numberOfWebScriptInvocations;
        this.timeBetweenWebScriptInvocations = timeBetweenWebScriptInvocations;
        this.batchSize = DEFAULT_BATCH_SIZE;
        this.eventNameWebScriptInvocation = EVENT_NAME_WEB_SCRIPT_INVOCATION;
        this.webScriptMessagePattern = webScriptMessagePattern;
    }

    /**
     * Override the {@link #DEFAULT_BATCH_SIZE default} batch size for Web Script invocation event processing
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    @Override
    public EventResult processEvent(Event event) throws Exception {
        // Check how many Web Script invocations that have already been scheduled.
        // This depends on the batchSize and the total number of invocations that should be made.
        Integer alreadyScheduled = (Integer) event.getData();
        if (alreadyScheduled == null) {
            alreadyScheduled = Integer.valueOf(0);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Already scheduled " + alreadyScheduled + " " + eventNameWebScriptInvocation +
                    " events and will schedule up to " + batchSize + " more.");
        }

        // Schedule another batch of Web Script Invocation events
        List<Event> events = new ArrayList<Event>(batchSize + 1);
        long now = System.currentTimeMillis();
        long scheduled = now;
        int localCount = 0;
        int totalCount = (int) alreadyScheduled;
        for (int i = 0; i < batchSize && totalCount < numberOfWebScriptInvocations; i++) {
            // Create a unique name for this Web Script invocation and store it under this name in the MongoDB
            String webScriptInvocationName = testRunFqn + "-" + UUID.randomUUID();

            // Delay the invocation with specified time
            scheduled += timeBetweenWebScriptInvocations;

            // Store this Web Script invocation as Scheduled
            WebScriptInvocationData data = new WebScriptInvocationData();
            data.setName(webScriptInvocationName);
            String message = webScriptMessagePattern;
            if (message.contains("%")) {
                message = String.format(webScriptMessagePattern, totalCount);
            }
            data.setMessage(message);
            data.setUsername(userDataService.getRandomUser().getUsername());
            data.setState(DataCreationState.Scheduled);
            webScriptInvocationDataDAO.createWebScriptInvocation(data);

            // Attach Web Script Invocation name as the event data, so we can look up the event data from
            // other Event Processors
            Event webScriptInvocationEvent = new Event(eventNameWebScriptInvocation, scheduled, webScriptInvocationName);

            // Add the Web Script Invocation event to the list of events scheduled
            events.add(webScriptInvocationEvent);
            localCount++;
            totalCount++;
        }

        // If we have not yet scheduled all the Web Script Invocations that we want to do, then reschedule this event
        if (totalCount < numberOfWebScriptInvocations) {
            Event rescheduleEvent = new Event(event.getName(), scheduled, Integer.valueOf(totalCount));
            events.add(rescheduleEvent);
        }

        // The ResultBarrier will ensure that this gets rescheduled, if necessary
        EventResult result = new EventResult("Created " + totalCount + " scheduled Web Script Invocations.", events);

        // Done
        if (logger.isDebugEnabled()) {
            logger.debug("Scheduled " + localCount + " Web Script Invocations and " + (totalCount < numberOfWebScriptInvocations ?
                    "rescheduled" : "did not reschedule") + " self.");
        }

        return result;
    }
}
