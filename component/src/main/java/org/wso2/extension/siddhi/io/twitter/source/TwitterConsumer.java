/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.io.twitter.source;

import org.apache.log4j.Logger;
import org.wso2.extension.siddhi.io.twitter.util.ExtractParam;
import org.wso2.siddhi.core.stream.input.source.SourceEventListener;
import twitter4j.FilterQuery;
import twitter4j.GeoLocation;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.StallWarning;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusListener;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterObjectFactory;
import twitter4j.TwitterStream;

import java.util.List;

/**
 * This class handles consuming tweets .
 */

public class TwitterConsumer {
    private static final Logger log = Logger.getLogger(TwitterSource.class);
    private static boolean isPaused = false;

    private TwitterConsumer() {
    }

    /**
     * This method handles consuming livestream tweets.
     *
     * @param twitterStream       - For streaming mode
     * @param sourceEventListener - Listen events
     * @param languageParam       - Specifies language
     * @param trackParam          - Specifies keyword to track
     * @param follow              - Specifies follower's id
     * @param filterLevel         - Specifies filter level( low ,medium, none)
     * @param locations           - Specifies location
     */
    public static void consume(TwitterStream twitterStream, SourceEventListener sourceEventListener,
                               String languageParam, String trackParam, long[] follow,
                               String filterLevel, double[][] locations, int paramSize) {
        FilterQuery filterQuery;
        String[] tracks;
        String[] filterLang;

        StatusListener listener = new StatusListener() {
            @Override
            public void onStatus(Status status) {
                if (isPaused) {
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error(ie);
                    }
                }
                sourceEventListener.onEvent(TwitterObjectFactory.getRawJSON(status), null);
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                log.debug("Got a status deletion notice id:" + statusDeletionNotice.getStatusId());
            }

            @Override
            public void onTrackLimitationNotice(int numberOfLimitedStatuses) {
                log.debug("Got track limitation notice: " + numberOfLimitedStatuses);
            }

            @Override
            public void onScrubGeo(long userId, long upToStatusId) {
                log.debug("Got scrub_geo event userId:" + userId + " upToStatusId:" + upToStatusId);
            }

            @Override
            public void onStallWarning(StallWarning warning) {
                log.debug("Got stall warning:" + warning);
            }

            @Override
            public void onException(Exception ex) {
                log.error("Twitter source threw an exception", ex);
            }
        };

        twitterStream.addListener(listener);
        filterQuery = new FilterQuery();
        if (!trackParam.trim().isEmpty()) {
            tracks = ExtractParam.extract(trackParam);
            filterQuery.track(tracks);
        }

        if (!languageParam.trim().isEmpty()) {
            filterLang = ExtractParam.extract(languageParam);
            filterQuery.language(filterLang);
        }

        if (follow != null) {
            filterQuery.follow(follow);
        }

        if (!filterLevel.trim().isEmpty()) {
            filterQuery.filterLevel(filterLevel);
        }

        if (locations != null) {
            filterQuery.locations(locations);
        }

        if (paramSize == 6) {
            twitterStream.sample();
        } else {
            twitterStream.filter(filterQuery);
        }
    }

    /**
     * This method handles consuming historical tweets within a week.
     *
     * @param twitter             - For Twitter Polling
     * @param sourceEventListener - Listen Events
     * @param q                   - Defines search query
     * @param language            - Restricts tweets to the given language
     * @param sinceId             - Returns results with an ID greater than the specified ID.
     * @param maxId               - Returns results with an ID less than or equal to the specified ID.
     * @param until               - Returns tweets created before the given date.
     * @param resultType          - Specifies what type of search results you would prefer to receive.
     * @param latitude            - Specifies the latitude of the location
     * @param longitude           - Specifies the longitude of the location
     * @param radius              - Specify the radius of the given location
     * @param unitName            - Specifies the unit name of the radius
     * @throws InterruptedException - The InterruptedException is thrown when a thread is waiting or sleeping
     */

    public static void consume(Twitter twitter, SourceEventListener sourceEventListener, String q, String language,
                               long sinceId, long maxId, String until, String resultType, String geoCode,
                               double latitude, double longitude, double radius, String unitName)
            throws InterruptedException {
        try {
            Query query = new Query(q);
            QueryResult result;
            if (!language.trim().isEmpty()) {
                query.lang(language);
            }
            if (!resultType.trim().isEmpty()) {
                query.resultType(Query.ResultType.valueOf(resultType));
            }
            if (!geoCode.trim().isEmpty()) {
                query.geoCode(new GeoLocation(latitude, longitude), radius, unitName);
            }
            if (!until.trim().isEmpty()) {
                query.until(until);
            }
            query.sinceId(sinceId);
            query.maxId(maxId);

            do {
                result = twitter.search(query);
                List<Status> tweets = result.getTweets();
                for (Status tweet : tweets) {
                    if (isPaused) {
                        Thread.sleep(10000);
                    }
                    sourceEventListener.onEvent(TwitterObjectFactory.getRawJSON(tweet), null);
                }
            } while ((query = result.nextQuery()) != null);
        } catch (TwitterException te) {
            log.error("Failed to search tweets: " + te.getMessage());
        }
    }

    public static void pause() {
        isPaused = true;
    }

    public static void resume() {
        isPaused = false;
    }


}

